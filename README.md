# Kestra Firebase Plugin

The Firebase plugin provides tasks for sending mobile and web push notifications through [Firebase Cloud Messaging (FCM)](https://firebase.google.com/docs/cloud-messaging) HTTP v1 API.

## Why

- **Mobile & Web Push Notifications**: Direct notification channel to Android, iOS, and Web clients from Kestra data pipelines and event triggers.
- **Topic Broadcasting**: Broadcast notifications to subscribed topics or complex condition expressions.
- **Secure Authentication**: Built on Google Cloud service account credentials or OAuth2 tokens with granular Firebase Messaging scopes.

## What

- Provides tasks under `io.kestra.plugin.firebase.fcm`.
- Includes `io.kestra.plugin.firebase.fcm.SendMessage` for sending push notifications to tokens, topics, or topic condition queries with notification display titles, bodies, images, and custom data payloads.

## License

Apache 2.0 (c) Kestra Technologies.
