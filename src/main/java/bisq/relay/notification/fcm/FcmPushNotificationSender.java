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

package bisq.relay.notification.fcm;

import bisq.relay.notification.PushNotificationMessage;
import bisq.relay.notification.PushNotificationResult;
import bisq.relay.notification.PushNotificationSender;
import com.google.api.core.ApiFuture;
import com.google.api.core.ApiFutureCallback;
import com.google.api.core.ApiFutures;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import jakarta.annotation.Nonnull;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Service
public class FcmPushNotificationSender implements PushNotificationSender {
    private static final Logger LOG = LoggerFactory.getLogger(FcmPushNotificationSender.class);

    private final Executor executor;
    private final FirebaseMessaging firebaseMessaging;
    private final FcmPushNotificationBuilder fcmPushNotificationBuilder;

    @Autowired
    public FcmPushNotificationSender(
            @Value("${fcm.firebaseUrl}") final String fcmFirebaseUrl,
            @Value("${fcm.firebaseConfigurationFile}") final String fcmFirebaseConfigurationFile,
            final FcmPushNotificationBuilder fcmPushNotificationBuilder) throws IOException {
        this.fcmPushNotificationBuilder = fcmPushNotificationBuilder;

        this.executor = MoreExecutors.directExecutor();

        InputStream firebaseConfigStream = new FileInputStream(fcmFirebaseConfigurationFile);
        GoogleCredentials googleCredentials = GoogleCredentials.fromStream(firebaseConfigStream);
        FirebaseOptions firebaseOptions = FirebaseOptions.builder()
                .setCredentials(googleCredentials)
                .setDatabaseUrl(fcmFirebaseUrl)
                .build();
        if (FirebaseApp.getApps().isEmpty()) {
            FirebaseApp.initializeApp(firebaseOptions);
        }

        this.firebaseMessaging = FirebaseMessaging.getInstance();

        LOG.info("FCM client is ready to push notifications");
    }

    @VisibleForTesting
    public FcmPushNotificationSender(
            final FirebaseMessaging firebaseMessaging,
            final FcmPushNotificationBuilder fcmPushNotificationBuilder) {
        this.firebaseMessaging = firebaseMessaging;
        this.fcmPushNotificationBuilder = fcmPushNotificationBuilder;
        this.executor = MoreExecutors.directExecutor();
    }

    @PreDestroy
    public void destroy() {
        LOG.info("Shutting down FCM client");
        for (FirebaseApp firebaseApp : FirebaseApp.getApps()) {
            firebaseApp.delete();
        }
    }

    // TODO implement circuit breaker, can use resilience4j
    //  Ref: https://www.baeldung.com/spring-boot-resilience4j
    public CompletableFuture<PushNotificationResult> sendNotification(
            @Nonnull final PushNotificationMessage pushNotificationMessage,
            @Nonnull final String deviceToken) {
        Objects.requireNonNull(pushNotificationMessage);
        Objects.requireNonNull(deviceToken);

        final Message message = fcmPushNotificationBuilder.buildMessage(pushNotificationMessage, deviceToken);

        final CompletableFuture<PushNotificationResult> completableFuture = new CompletableFuture<>();

        final ApiFuture<String> apiFuture = firebaseMessaging.sendAsync(message);

        ApiFutures.addCallback(apiFuture, new ApiFutureCallback<>() {
            @Override
            public void onSuccess(final String result) {
                LOG.info("Push notification accepted by FCM gateway; messageId={}", result);
                completableFuture.complete(new PushNotificationResult(true, null, null, false));
            }

            @Override
            public void onFailure(final Throwable cause) {
                if (cause instanceof final FirebaseMessagingException firebaseMessagingException &&
                        firebaseMessagingException.getMessagingErrorCode() != null) {
                    final String errorCode = firebaseMessagingException.getMessagingErrorCode().name();
                    final String errorMessage = firebaseMessagingException.getMessage();
                    LOG.error("Push notification rejected by FCM gateway; [{}] {}", errorCode,
                            errorMessage == null ? "" : errorMessage);
                    completableFuture.complete(new PushNotificationResult(false, errorCode, errorMessage,
                            firebaseMessagingException.getMessagingErrorCode() == MessagingErrorCode.UNREGISTERED));
                } else {
                    // Something went wrong when trying to send the notification to the
                    // FCM server. Note that this is distinct from a rejection from
                    // the server, and indicates that something went wrong when actually
                    // sending the notification or waiting for a reply.
                    LOG.error("Failed to send notification to FCM gateway; {}", cause.getMessage());
                    completableFuture.completeExceptionally(cause);
                }
            }
        }, executor);

        return completableFuture;
    }
}
