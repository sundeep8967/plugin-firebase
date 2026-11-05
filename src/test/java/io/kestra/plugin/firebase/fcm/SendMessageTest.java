package io.kestra.plugin.firebase.fcm;

import com.sun.net.httpserver.HttpServer;
import io.kestra.core.exceptions.KestraRuntimeException;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@KestraTest
class SendMessageTest {
    @Inject
    private RunContextFactory runContextFactory;

    private HttpServer server;
    private String serverUrl;
    private final AtomicReference<String> lastAuthHeader = new AtomicReference<>();
    private final AtomicReference<String> lastRequestBody = new AtomicReference<>();
    private int responseStatus = 200;
    private String responseBody = "{\"name\": \"projects/my-firebase-app/messages/fcm_msg_123456\"}";

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        serverUrl = "http://localhost:" + server.getAddress().getPort();

        server.createContext("/v1/projects/my-firebase-app/messages:send", exchange -> {
            lastAuthHeader.set(exchange.getRequestHeaders().getFirst("Authorization"));
            lastRequestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));

            byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(responseStatus, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
        server.start();
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void shouldSendMessageSuccessfullyToToken() throws Exception {
        RunContext runContext = runContextFactory.of(Map.of(
            "token", "device_registration_token_xyz",
            "title", "Deployment Finished"
        ));

        SendMessage task = SendMessage.builder()
            .oauthToken(Property.ofValue("ya29.mock_oauth_token"))
            .projectId(Property.ofValue("my-firebase-app"))
            .baseUrl(Property.ofValue(serverUrl))
            .token(Property.ofExpression("{{ token }}"))
            .title(Property.ofExpression("{{ title }}"))
            .body(Property.ofValue("Workflow pipeline ran successfully."))
            .data(Property.ofValue(Map.of("executionId", "exec-99", "env", "production")))
            .build();

        SendMessage.Output output = task.run(runContext);

        assertThat(output).isNotNull();
        assertThat(output.getName()).isEqualTo("projects/my-firebase-app/messages/fcm_msg_123456");
        assertThat(output.getMessageId()).isEqualTo("fcm_msg_123456");

        assertThat(lastAuthHeader.get()).isEqualTo("Bearer ya29.mock_oauth_token");
        assertThat(lastRequestBody.get()).contains("\"token\":\"device_registration_token_xyz\"");
        assertThat(lastRequestBody.get()).contains("\"title\":\"Deployment Finished\"");
        assertThat(lastRequestBody.get()).contains("\"body\":\"Workflow pipeline ran successfully.\"");
        assertThat(lastRequestBody.get()).contains("\"executionId\":\"exec-99\"");
    }

    @Test
    void shouldBroadcastMessageToTopicSuccessfully() throws Exception {
        RunContext runContext = runContextFactory.of();

        SendMessage task = SendMessage.builder()
            .oauthToken(Property.ofValue("ya29.mock_oauth_token"))
            .projectId(Property.ofValue("my-firebase-app"))
            .baseUrl(Property.ofValue(serverUrl))
            .topic(Property.ofValue("news_alerts"))
            .title(Property.ofValue("Breaking News"))
            .body(Property.ofValue("Important announcement."))
            .build();

        SendMessage.Output output = task.run(runContext);

        assertThat(output).isNotNull();
        assertThat(output.getMessageId()).isEqualTo("fcm_msg_123456");
        assertThat(lastRequestBody.get()).contains("\"topic\":\"news_alerts\"");
    }

    @Test
    void shouldThrowExceptionWhenNoTargetSpecified() {
        RunContext runContext = runContextFactory.of();

        SendMessage task = SendMessage.builder()
            .oauthToken(Property.ofValue("ya29.mock_oauth_token"))
            .projectId(Property.ofValue("my-firebase-app"))
            .baseUrl(Property.ofValue(serverUrl))
            .title(Property.ofValue("Missing Target"))
            .body(Property.ofValue("Should fail."))
            .build();

        assertThatThrownBy(() -> task.run(runContext))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Exactly one target among 'token', 'topic', or 'condition' must be specified");
    }

    @Test
    void shouldThrowExceptionWhenFCMReturnsErrorStatus() {
        responseStatus = 404;
        responseBody = "{\"error\": {\"code\": 404, \"message\": \"Requested entity was not found.\", \"status\": \"NOT_FOUND\"}}";

        RunContext runContext = runContextFactory.of();

        SendMessage task = SendMessage.builder()
            .oauthToken(Property.ofValue("ya29.mock_oauth_token"))
            .projectId(Property.ofValue("my-firebase-app"))
            .baseUrl(Property.ofValue(serverUrl))
            .token(Property.ofValue("nonexistent_device_token"))
            .title(Property.ofValue("Invalid Target"))
            .body(Property.ofValue("Testing error handling."))
            .build();

        assertThatThrownBy(() -> task.run(runContext))
            .isInstanceOf(KestraRuntimeException.class)
            .hasMessageContaining("FCM message dispatch failed with HTTP status 404");
    }
}
