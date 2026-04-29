#!/bin/bash
#
# Bisq Connect relay launcher.
#
# Usage:
#   ./connect_run.sh         # production: prod APNs (no sandbox), prod bundle id
#   ./connect_run.sh debug   # debug: APNs sandbox, dev bundle id
#
# Anything other than the literal "debug" argument is treated as production
# to avoid accidentally pointing at sandbox in prod runs.

set -euo pipefail

MODE="${1:-prod}"

case "$MODE" in
    debug)
        APNS_BUNDLE_ID="network.bisq.mobile.ios"
        APNS_USE_SANDBOX=true
        ;;
    prod|"")
        APNS_BUNDLE_ID="bisq.mobile.client.BisqConnect"
        APNS_USE_SANDBOX=false
        ;;
    *)
        echo "Unknown mode: $MODE" >&2
        echo "Usage: $0 [debug]" >&2
        exit 2
        ;;
esac

echo "Starting bisq-relay in $MODE mode (APNs sandbox=$APNS_USE_SANDBOX)"

./gradlew clean build --info

export BISQ_RELAY_APNS_BUNDLE_ID="$APNS_BUNDLE_ID"
export BISQ_RELAY_APNS_CERTIFICATE_FILE="apnsCertificate.production.p12"
export BISQ_RELAY_APNS_CERTIFICATE_PASSWORD_FILE="apnsCertificatePassword.txt"
export BISQ_RELAY_APNS_USE_SANDBOX="$APNS_USE_SANDBOX"
export BISQ_RELAY_FCM_ENABLED=true
export BISQ_RELAY_FCM_FIREBASE_CONFIGURATION_FILE="fcmServiceAccountKey.json"
export BISQ_RELAY_FCM_FIREBASE_URL="https://bisqnotifications.firebaseio.com"
# Required for the encrypted-payload model: when true, the relay sends FCM
# data-only messages (no `notification` block). That ensures the device's
# BisqFirebaseMessagingService.onMessageReceived() runs in all states
# (foreground/background/killed), decrypts with the per-device symmetric key,
# and posts the real category-based notification. With this set to false the
# system would render the relay's hardcoded "You have received a Bisq
# notification / Click to decrypt" text whenever the app is not foregrounded.
export BISQ_RELAY_FCM_DATA_ONLY=true

JAVA_OPTS="-Dio.netty.resolver.dns.macos.dnsServerAddressStreamProvider.enable=false" ./bisq-relay
