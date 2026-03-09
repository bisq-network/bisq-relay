#!/bin/bash

./gradlew clean build --info

# SANDBOX
# export BISQ_RELAY_APNS_BUNDLE_ID="network.bisq.mobile.ios"
export BISQ_RELAY_APNS_BUNDLE_ID="bisq.mobile.client.BisqConnect"
export BISQ_RELAY_APNS_CERTIFICATE_FILE="apnsCertificate.production.p12"
export BISQ_RELAY_APNS_CERTIFICATE_PASSWORD_FILE="apnsCertificatePassword.txt"
export BISQ_RELAY_APNS_USE_SANDBOX=false
export BISQ_RELAY_FCM_ENABLED=false
export BISQ_RELAY_FCM_FIREBASE_CONFIGURATION_FILE="fcmServiceAccountKey.json"
export BISQ_RELAY_FCM_FIREBASE_URL="https://bisqnotifications.firebaseio.com"

JAVA_OPTS="-Dio.netty.resolver.dns.macos.dnsServerAddressStreamProvider.enable=false" ./bisq-relay
