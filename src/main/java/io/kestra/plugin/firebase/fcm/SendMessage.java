package io.kestra.plugin.firebase.fcm;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.kestra.core.exceptions.KestraRuntimeException;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.runners.RunContext;
import io.kestra.core.serializers.JacksonMapper;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import org.slf4j.Logger;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Send a push notification via Firebase Cloud Messaging",
    description = "Dispatches a push notification to individual devices, topics, or topic condition targets using the FCM HTTP v1 API."
)
@Plugin(
    examples = {
        @Example(
            title = "Send a push notification to a device token",
            full = true,
            code = """
                id: send_fcm_notification
                namespace: company.team

                tasks:
                  - id: send_push
                    type: io.kestra.plugin.firebase.fcm.SendMessage
                    serviceAccount: "{{ secret('FIREBASE_SERVICE_ACCOUNT') }}"
                    token: "eXaMpLeDeViCeToKeN_12345"
                    title: "Pipeline Completed"
                    body: "The nightly ETL data warehouse pipeline has finished."
                """
        ),
        @Example(
            title = "Broadcast a notification to a topic with custom data",
            full = true,
            code = """
                id: broadcast_fcm_topic
                namespace: company.team

                tasks:
                  - id: send_broadcast
                    type: io.kestra.plugin.firebase.fcm.SendMessage
                    serviceAccount: "{{ secret('FIREBASE_SERVICE_ACCOUNT') }}"
                    topic: "global_announcements"
                    title: "Scheduled Maintenance"
                    body: "Platform maintenance will begin at 02:00 UTC."
                    data:
                      maintenance_window: "30m"
                      priority: "high"
                """
        )
    }
)
public class SendMessage extends AbstractFcmTask implements RunnableTask<SendMessage.Output> {
    private static final ObjectMapper MAPPER = JacksonMapper.ofJson();

    @Schema(
        title = "Device Registration Token",
        description = "Registration token of the destination client device."
    )
    @PluginProperty(group = "destination")
    private Property<String> token;

    @Schema(
        title = "Topic Name",
        description = "Name of the topic to send the message to (do not include the `/topics/` prefix)."
    )
    @PluginProperty(group = "destination")
    private Property<String> topic;

    @Schema(
        title = "Topic Condition",
        description = "Condition expression matching topics (e.g. `'TopicA' in topics && ('TopicB' in topics || 'TopicC' in topics)`)."
    )
    @PluginProperty(group = "destination")
    private Property<String> condition;

    @Schema(
        title = "Notification Title",
        description = "Title of the push notification."
    )
    @PluginProperty(group = "main")
    private Property<String> title;

    @Schema(
        title = "Notification Body",
        description = "Body text of the push notification."
    )
    @PluginProperty(group = "main")
    private Property<String> body;

    @Schema(
        title = "Notification Image URL",
        description = "URL of a rich image to display in the push notification."
    )
    @PluginProperty(group = "main")
    private Property<String> image;

    @Schema(
        title = "Custom Data Payload",
        description = "Key-value data payload delivered to the client application."
    )
    @PluginProperty(group = "main")
    private Property<Map<String, String>> data;

    @Override
    public Output run(RunContext runContext) throws Exception {
        Logger logger = runContext.logger();

        AuthDetails auth = resolveAuth(runContext);
        String base = runContext.render(this.baseUrl).as(String.class).orElse("https://fcm.googleapis.com");
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        Duration reqTimeout = runContext.render(this.timeout).as(Duration.class).orElse(Duration.ofSeconds(15));

        String tokenVal = runContext.render(this.token).as(String.class).orElse(null);
        String topicVal = runContext.render(this.topic).as(String.class).orElse(null);
        String conditionVal = runContext.render(this.condition).as(String.class).orElse(null);

        int targetsCount = (tokenVal != null ? 1 : 0) + (topicVal != null ? 1 : 0) + (conditionVal != null ? 1 : 0);
        if (targetsCount != 1) {
            throw new IllegalArgumentException("Exactly one target among 'token', 'topic', or 'condition' must be specified for FCM messages.");
        }

        String titleVal = runContext.render(this.title).as(String.class).orElse(null);
        String bodyVal = runContext.render(this.body).as(String.class).orElse(null);
        String imageVal = runContext.render(this.image).as(String.class).orElse(null);
        Map<String, String> dataVal = runContext.render(this.data).asMap(String.class, String.class);

        if (titleVal == null && bodyVal == null && (dataVal == null || dataVal.isEmpty())) {
            throw new IllegalArgumentException("At least one of 'title', 'body', or non-empty 'data' must be provided for FCM messages.");
        }

        Map<String, Object> message = new HashMap<>();
        if (tokenVal != null) {
            message.put("token", tokenVal);
        } else if (topicVal != null) {
            message.put("topic", topicVal);
        } else {
            message.put("condition", conditionVal);
        }

        if (titleVal != null || bodyVal != null || imageVal != null) {
            Map<String, String> notification = new HashMap<>();
            if (titleVal != null) {
                notification.put("title", titleVal);
            }
            if (bodyVal != null) {
                notification.put("body", bodyVal);
            }
            if (imageVal != null) {
                notification.put("image", imageVal);
            }
            message.put("notification", notification);
        }

        if (dataVal != null && !dataVal.isEmpty()) {
            message.put("data", dataVal);
        }

        Map<String, Object> requestPayload = Map.of("message", message);
        String jsonPayload = MAPPER.writeValueAsString(requestPayload);

        String targetUrl = "%s/v1/projects/%s/messages:send".formatted(base, auth.projectId());
        logger.debug("Dispatching FCM message to: {}", targetUrl);

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(targetUrl))
            .timeout(reqTimeout)
            .header("Authorization", "Bearer " + auth.accessToken())
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(jsonPayload, StandardCharsets.UTF_8))
            .build();

        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        int statusCode = response.statusCode();
        String responseBody = response.body();

        if (statusCode >= 400) {
            throw new KestraRuntimeException("FCM message dispatch failed with HTTP status %d: %s".formatted(statusCode, responseBody));
        }

        Map<String, Object> responseMap = MAPPER.readValue(responseBody, new TypeReference<Map<String, Object>>() {});
        String name = (String) responseMap.get("name");
        String messageId = null;
        if (name != null && name.contains("/")) {
            messageId = name.substring(name.lastIndexOf('/') + 1);
        }

        logger.info("Successfully sent FCM message with ID: {}", messageId != null ? messageId : name);

        return Output.builder()
            .name(name)
            .messageId(messageId)
            .rawResponse(responseMap)
            .build();
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(
            title = "Message Resource Name",
            description = "Full resource name returned by FCM (e.g. `projects/{project_id}/messages/{message_id}`)."
        )
        private String name;

        @Schema(
            title = "Message ID",
            description = "Unique message ID generated by Firebase Cloud Messaging."
        )
        private String messageId;

        @Schema(
            title = "Raw Response",
            description = "Full response payload from the FCM API."
        )
        private Map<String, Object> rawResponse;
    }
}
