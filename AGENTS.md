# Kestra Firebase Plugin

## What

- Provides tasks under `io.kestra.plugin.firebase.fcm`.
- Includes `io.kestra.plugin.firebase.fcm.SendMessage` for sending push notifications via Firebase Cloud Messaging.

## Why

- What user problem does this solve? Enables dispatching mobile and web push notifications directly from Kestra workflows to Firebase Cloud Messaging.
- Why would a team adopt this plugin in a workflow? Allows alerting end users on mobile/web apps when jobs complete, threshold alarms trigger, or transactional events occur.
- What operational outcome does it enable? Reliable, secure push notification delivery with topic broadcast and background payload support.

## How

### Architecture

Single-module plugin. Source packages under `io.kestra.plugin.firebase`:
- `fcm`

### Key Plugin Classes

- `io.kestra.plugin.firebase.fcm.AbstractFcmTask`
- `io.kestra.plugin.firebase.fcm.SendMessage`

### Project Structure

```
plugin-firebase/
├── src/main/java/io/kestra/plugin/firebase/fcm/
├── src/test/java/io/kestra/plugin/firebase/fcm/
├── build.gradle
└── README.md
```
