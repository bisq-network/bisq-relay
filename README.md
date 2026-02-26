# Bisq Relay

## Overview

This service acts as an intermediary between Bisq clients and Apple Push Notification service (APNs)
or Firebase Cloud Messaging (FCM) to send push notifications to the mobile app.
More documentation can be found [here](https://github.com/bisq-network/bisqremote/wiki).

## Building the Source Code

### Build the Project

Build the project using the `installDist` Gradle task, which creates a local runnable distribution under
`build/install/` and generates the startup script used to run the service.

```shell
./gradlew clean installDist
```

## Running the Service

### Requirements

Before running the service, several files need to be obtained to be able to
send push notifications to APNs and FCM, and ultimately to the corresponding mobile app.

#### FCM
An appropriate `fcmServiceAccountKey.json` file needs to be copied to the root folder.
Download it from the [Firebase console](https://console.firebase.google.com/)
under `project settings` > `service accounts`.

> Note, the Android app needs to be built with a corresponding `google-services.json` file
> for the same Firebase project.

By default, the relay sends basic notifications that are only processed by the app when clicked on.
If you want to send "data-only" messages, allowing the mobile app to receive and process messages
in the background and control how/if it wants to show a notification to the user, you can set the
`BISQ_RELAY_FCM_DATA_ONLY` environment variable to `true`.

> For more details about messages and notifications,
> see: https://firebase.google.com/docs/cloud-messaging/android/receive

#### APNs
An appropriate `apnsCertificate.p12` file needs to be copied to the root folder, along with the
corresponding password stored within a `apnsCertificatePassword.txt` file.

> Note, the APNs certificate needs to be manually renewed every year.

To get the APNs certificate, the following will need to be done on macOS:
1. Create and download a cer file from https://developer.apple.com/account/ios/certificate/?teamId=XXXXXX
2. Add the *.cer file to your keychain.
3. In keychain, go to "My certificates". Expand the Apple Push Service certificate and select both lines.
   Then export the certificate as a *.p12 file.

### Configuration

The service is configured via environment variables. The following variables are available:

#### APNs Configuration

| Environment Variable                        | Description                                  | Default  |
|---------------------------------------------|----------------------------------------------|----------|
| `BISQ_RELAY_APNS_BUNDLE_ID`                 | iOS app bundle identifier (required)         | _(none)_ |
| `BISQ_RELAY_APNS_CERTIFICATE_FILE`          | Path to .p12 certificate file (required)     | _(none)_ |
| `BISQ_RELAY_APNS_CERTIFICATE_PASSWORD_FILE` | Path to certificate password file (required) | _(none)_ |
| `BISQ_RELAY_APNS_USE_SANDBOX`               | Use APNs sandbox environment                 | `true`   |

> **Note:** `BISQ_RELAY_APNS_USE_SANDBOX` defaults to `true` for safety. Production deployments must explicitly set this to `false`.

#### FCM Configuration

| Environment Variable                         | Description                                                   | Default  |
|----------------------------------------------|---------------------------------------------------------------|----------|
| `BISQ_RELAY_FCM_ENABLED`                     | Enable FCM push notifications                                 | `false`  |
| `BISQ_RELAY_FCM_FIREBASE_CONFIGURATION_FILE` | Path to Firebase service account JSON (required when enabled) | _(none)_ |
| `BISQ_RELAY_FCM_FIREBASE_URL`                | Firebase database URL (required when enabled)                 | _(none)_ |
| `BISQ_RELAY_FCM_DATA_ONLY`                   | Enable sending FCM data-only messages                         | `false`  |

### Run the Script

After building the project using the `installDist` Gradle task, a script will be generated at
[build/install/bisq-relay/bin/bisq-relay](build/install/bisq-relay/bin/bisq-relay).

#### Development/Sandbox Mode

For development with APNs sandbox:
```sh
  export BISQ_RELAY_APNS_BUNDLE_ID="your.app.bundle.id"
  export BISQ_RELAY_APNS_CERTIFICATE_FILE=apnsCertificate.p12
  export BISQ_RELAY_APNS_CERTIFICATE_PASSWORD_FILE=apnsCertificatePassword.txt
  ./build/install/bisq-relay/bin/bisq-relay
```

#### Production Mode

For production deployment:
```sh
  export BISQ_RELAY_APNS_BUNDLE_ID="your.app.bundle.id"
  export BISQ_RELAY_APNS_USE_SANDBOX=false
  export BISQ_RELAY_APNS_CERTIFICATE_FILE=/path/to/apnsCertificate.production.p12
  export BISQ_RELAY_APNS_CERTIFICATE_PASSWORD_FILE=/path/to/apnsCertificatePassword.txt
  ./build/install/bisq-relay/bin/bisq-relay
```

#### With FCM Enabled

To also enable FCM (Android) push notifications:
```sh
  export BISQ_RELAY_APNS_BUNDLE_ID="your.app.bundle.id"
  export BISQ_RELAY_APNS_USE_SANDBOX=false
  export BISQ_RELAY_FCM_ENABLED=true
  export BISQ_RELAY_FCM_FIREBASE_CONFIGURATION_FILE=/path/to/fcmServiceAccountKey.json
  ./build/install/bisq-relay/bin/bisq-relay
```

#### Legacy Configuration (Deprecated)

You can still use Java system properties if needed:
```sh
  export BISQ_RELAY_OPTS="-Dapns.bundleId=your.app.bundle.id -Dapns.useSandbox=false"
  ./build/install/bisq-relay/bin/bisq-relay
```

## Deploying a Local Test Environment

Use the following docker command to deploy a complete local test environment:

```shell
docker compose up --build
```

Once deployed, the following will be available:

- Application REST API: http://127.0.0.1:8080 (e.g. `POST http://127.0.0.1:8080/v1/apns/device/{deviceToken}`)
- Application management interface: http://127.0.0.1:9400 (e.g. http://127.0.0.1:9400/actuator/info)
- Grafana: http://127.0.0.1:3000
- Prometheus: http://127.0.0.1:9090

## API Reference

### Request Body

The `POST /v1/apns/device/{deviceToken}` and `POST /v1/fcm/device/{deviceToken}` endpoints accept a JSON body with the following fields:

| Field | Type | Required | Default | Description |
|-------|------|----------|---------|-------------|
| `encrypted` | string | yes | — | Encrypted notification payload |
| `isUrgent` | boolean | no | `false` | When `true`, sends as high-priority alert; when `false`, sends as background notification |
| `isMutableContent` | boolean | no | `false` | APNs only. When `true`, sets the `mutable-content` flag in the APNs payload, allowing the iOS app's Notification Service Extension (NSE) to modify the notification content before display (e.g. for client-side decryption) |
