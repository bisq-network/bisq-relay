/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.relay.notification.apns;

import bisq.relay.notification.PushNotificationMessage;
import bisq.relay.notification.PushNotificationResult;
import bisq.relay.notification.PushNotificationSender;
import com.eatthepath.pushy.apns.ApnsClient;
import com.eatthepath.pushy.apns.ApnsClientBuilder;
import com.eatthepath.pushy.apns.PushNotificationResponse;
import com.eatthepath.pushy.apns.util.SimpleApnsPushNotification;
import com.eatthepath.pushy.apns.util.concurrent.PushNotificationFuture;
import com.google.common.annotations.VisibleForTesting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.Nonnull;
import javax.annotation.PreDestroy;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Objects;
import java.util.Scanner;
import java.util.concurrent.CompletableFuture;

@Service
public class ApnsPushNotificationSender implements PushNotificationSender {
    private static final Logger LOG = LoggerFactory.getLogger(ApnsPushNotificationSender.class);

    private final String apnsBundleId;
    private final ApnsClient apnsClient;
    private final ApnsPushNotificationBuilder apnsPushNotificationBuilder;

    @Autowired
    public ApnsPushNotificationSender(
            @Value("${apns.bundleId}") final String apnsBundleId,
            @Value("${apns.certificateFile}") final String apnsCertificateFile,
            @Value("${apns.certificatePasswordFile}") final String apnsCertificatePasswordFile,
            final ApnsPushNotificationBuilder apnsPushNotificationBuilder)
            throws IOException {
        this.apnsBundleId = apnsBundleId;
        this.apnsPushNotificationBuilder = apnsPushNotificationBuilder;

        final String appleCertPassword;
        try (Scanner scanner = new Scanner(new FileInputStream(apnsCertificatePasswordFile))) {
            appleCertPassword = scanner.next();
        }

        final File appleCertFile = new File(apnsCertificateFile);

        apnsClient = new ApnsClientBuilder().setApnsServer(ApnsClientBuilder.PRODUCTION_APNS_HOST)
                .setClientCredentials(appleCertFile, appleCertPassword).build();

        LOG.info("APNS client is ready to push notifications");
    }

    @VisibleForTesting
    public ApnsPushNotificationSender(
            final ApnsClient apnsClient,
            final String apnsBundleId,
            final ApnsPushNotificationBuilder apnsPushNotificationBuilder) {
        this.apnsClient = apnsClient;
        this.apnsBundleId = apnsBundleId;
        this.apnsPushNotificationBuilder = apnsPushNotificationBuilder;
    }

    @PreDestroy
    public void destroy() {
        LOG.info("Shutting down APNs client");
        apnsClient.close().join();
    }

    // TODO implement circuit breaker, can use resilience4j
    //  Ref: https://www.baeldung.com/spring-boot-resilience4j
    public CompletableFuture<PushNotificationResult> sendNotification(
            @Nonnull final PushNotificationMessage pushNotificationMessage,
            @Nonnull final String deviceToken) {
        Objects.requireNonNull(pushNotificationMessage);
        Objects.requireNonNull(deviceToken);

        final CompletableFuture<PushNotificationResult> completableFuture = new CompletableFuture<>();

        final PushNotificationFuture<SimpleApnsPushNotification, PushNotificationResponse<SimpleApnsPushNotification>>
                sendNotificationFuture = apnsClient.sendNotification(apnsPushNotificationBuilder.buildPushNotification(
                pushNotificationMessage, deviceToken, apnsBundleId));

        sendNotificationFuture.whenComplete((response, cause) -> {
            if (response == null) {
                // Something went wrong when trying to send the notification to the
                // APNs server. Note that this is distinct from a rejection from
                // the server, and indicates that something went wrong when actually
                // sending the notification or waiting for a reply.
                LOG.error("Failed to send notification to APNs gateway; {}", cause.getMessage());
                completableFuture.completeExceptionally(cause);
                return;
            }

            final boolean wasAccepted;
            final String errorCode;
            final String errorMessage;
            final boolean isUnregistered;

            if (response.isAccepted()) {
                wasAccepted = true;
                errorCode = null;
                errorMessage = null;
                isUnregistered = false;
                LOG.info("Push notification accepted by APNs gateway; apnsId={}", response.getApnsId());
            } else {
                wasAccepted = false;
                errorCode = response.getRejectionReason().orElse("unknown");
                if (response.getTokenInvalidationTimestamp().isPresent()) {
                    errorMessage = String.format("Token is invalid as of %s", response.getTokenInvalidationTimestamp().get());
                } else {
                    errorMessage = null;
                }
                isUnregistered = ("Unregistered".equals(errorCode) || "BadDeviceToken".equals(errorCode));
                LOG.error("Push notification rejected by APNs gateway; [{}] {}",
                        errorCode, errorMessage == null ? "" : errorMessage);
            }

            completableFuture.complete(new PushNotificationResult(wasAccepted, errorCode, errorMessage, isUnregistered));
        });

        return completableFuture;
    }
}
