# Firebase

The Firebase plugin provides tasks for sending mobile and web push notifications through [Firebase Cloud Messaging (FCM)](https://firebase.google.com/docs/cloud-messaging) HTTP v1 API.

Firebase is Google's mobile platform providing messaging, real-time databases, analytics, and authentication.

## Getting Started

1. In the [Firebase Console](https://console.firebase.google.com/), open **Project Settings** > **Service Accounts**.
2. Click **Generate new private key** to download your Google service account JSON file.
3. Store the full JSON content inside Kestra Secrets under `FIREBASE_SERVICE_ACCOUNT`.
4. Use the `SendMessage` task to dispatch push notifications to individual devices via registration tokens or broadcast to topics.

## Tasks

### `io.kestra.plugin.firebase.fcm.SendMessage`

Sends a push notification to Android, iOS, or Web applications using the FCM HTTP v1 API:
- `projectId`: Google Cloud / Firebase project identifier (optional if present in service account JSON).
- `token`: Target client registration token for a specific device.
- `topic`: Topic name for broadcast messaging (mutually exclusive with `token`).
- `title`: Notification title.
- `body`: Notification text body.
- `image`: Optional rich image URL displayed in the notification.
- `data`: Custom key-value map delivering background data payloads.
