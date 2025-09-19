# Bisq Relay

## Overview

This service acts as an intermediary between Bisq clients and Apple Push Notification service (APNs)
or Firebase Cloud Messaging (FCM) in order to send push notifications to the mobile app.
More documentation can be found [here](https://github.com/bisq-network/bisqremote/wiki).

## Building the Source Code

### Clone the Repository

This repository has a dependency on git submodules [bisq](https://github.com/bisq-network/bisq)
and [bisq-gradle](https://github.com/bisq-network/bisq-gradle).  
There are two ways to clone it before it can be built:

1. Use the --recursive option in the clone command:
```sh
  git clone --recursive  https://github.com/bisq-network/bisq-relay.git
```

2. Do a normal clone, and pull down the bisq repo dependency with two git submodule commands:
```sh
  git clone https://github.com/bisq-network/bisq-relay.git
  cd bisq-relay
  git submodule init
  git submodule update
```

### Build the Project

```sh
  ./gradlew clean build
```

## Running the Service

### Requirements

Prior to running the service, several files need to be obtained in order to be able to
send push notifications to APNs and FCM, and ultimately to the corresponding mobile app.

#### FCM
An appropriate `fcmServiceAccountKey.json` file needs to be copied to the root folder.
Download it from the [Firebase console](https://console.firebase.google.com/)
under `project settings` > `service accounts`.

> Note, the Android app needs to be built with a corresponding `google-services.json` file
> for the same Firebase project.

#### APNs
An appropriate `apnsCertificate.production.p12` file needs to be copied to the root folder, along with the
corresponding password stored within a `apnsCertificatePassword.txt` file.

> Note, the APNs certificate needs to be manually renewed every year.

In order to obtain the APNs certificate, the following will need to be done on macOS:
1. Create and download a cer file from https://developer.apple.com/account/ios/certificate/?teamId=XXXXXX
2. Add the *.cer file to your keychain.
3. In keychain, go to "My certificates". Expand the Apple Push Service certificate and select both lines.
   Then export the certificate as a *.p12 file.

### Run the Script

After building the project, a `bisq-relay` script will be generated at the root of the project.
Run the script. If the necessary files as described above are located in the root folder, the service should start running.
```sh
  ./bisq-relay
```

If you need to specify a custom location/name of the files, you can provide arguments as follows:
```sh
  export BISQ_RELAY_OPTS="-Dfcm.firebaseConfigurationFile=serviceAccountKey.json -Dapns.certificateFile=apnsCert.production.p12 -Dapns.certificatePasswordFile=apnsCertPassword.txt"; ./bisq-relay
```

## Deploying a Local Test Environment

Use the following docker command to deploy a complete local test environment:

```sh
  docker compose up --build
```

Once deployed, the following will be available:

- Application REST API: http://127.0.0.1:8080 (e.g. `POST http://127.0.0.1:8080/v1/apns/device/{deviceToken}`)
- Application management interface: http://127.0.0.1:9400 (e.g. http://127.0.0.1:9400/actuator/info)
- Grafana: http://127.0.0.1:3000
- Prometheus: http://127.0.0.1:9090
