package io.kestra.plugin.firebase.fcm;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.auth.oauth2.GoogleCredentials;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.runners.RunContext;
import io.kestra.core.serializers.JacksonMapper;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.Map;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
public abstract class AbstractFcmTask extends Task {
    private static final ObjectMapper MAPPER = JacksonMapper.ofJson();
    private static final String FCM_SCOPE = "https://www.googleapis.com/auth/firebase.messaging";

    @Schema(
        title = "Google Service Account JSON",
        description = "Full JSON key file content of the Google service account with Firebase Cloud Messaging permissions."
    )
    @PluginProperty(group = "connection", secret = true)
    @ToString.Exclude
    protected Property<String> serviceAccount;

    @Schema(
        title = "OAuth2 Access Token",
        description = "Pre-generated OAuth2 access token with Firebase messaging scope (`https://www.googleapis.com/auth/firebase.messaging`)."
    )
    @PluginProperty(group = "connection", secret = true)
    @ToString.Exclude
    protected Property<String> oauthToken;

    @Schema(
        title = "Firebase Project ID",
        description = "Google Cloud or Firebase project ID. If omitted, read automatically from the service account JSON."
    )
    @PluginProperty(group = "connection")
    protected Property<String> projectId;

    @Schema(
        title = "Base URL",
        description = "FCM API base endpoint. Defaults to `https://fcm.googleapis.com`."
    )
    @PluginProperty(group = "connection")
    @Builder.Default
    protected Property<String> baseUrl = Property.ofValue("https://fcm.googleapis.com");

    @Schema(
        title = "HTTP Request Timeout",
        description = "Maximum duration to wait for the FCM dispatch request to complete."
    )
    @PluginProperty(group = "connection")
    @Builder.Default
    protected Property<Duration> timeout = Property.ofValue(Duration.ofSeconds(15));

    protected AuthDetails resolveAuth(RunContext runContext) throws Exception {
        String token = runContext.render(this.oauthToken).as(String.class).orElse(null);
        String sa = runContext.render(this.serviceAccount).as(String.class).orElse(null);
        String projId = runContext.render(this.projectId).as(String.class).orElse(null);

        if (token != null && !token.isBlank()) {
            if (projId == null || projId.isBlank()) {
                throw new IllegalArgumentException("'projectId' is required when authenticating with a raw 'oauthToken'.");
            }
            return new AuthDetails(token, projId);
        }

        if (sa != null && !sa.isBlank()) {
            Map<String, Object> saMap = parseServiceAccount(sa);
            if (projId == null || projId.isBlank()) {
                projId = (String) saMap.get("project_id");
            }

            if (projId == null || projId.isBlank()) {
                throw new IllegalArgumentException("Could not determine 'projectId' from 'serviceAccount' JSON.");
            }

            GoogleCredentials credentials = GoogleCredentials.fromStream(
                new ByteArrayInputStream(sa.getBytes(StandardCharsets.UTF_8))
            ).createScoped(Collections.singletonList(FCM_SCOPE));

            credentials.refreshIfExpired();
            String accessToken = credentials.getAccessToken().getTokenValue();
            return new AuthDetails(accessToken, projId);
        }

        throw new IllegalArgumentException("Either 'serviceAccount' or 'oauthToken' must be provided to authenticate with Firebase Cloud Messaging.");
    }

    private Map<String, Object> parseServiceAccount(String json) throws IOException {
        return MAPPER.readValue(json, new TypeReference<Map<String, Object>>() {});
    }

    public record AuthDetails(String accessToken, String projectId) {
    }
}
