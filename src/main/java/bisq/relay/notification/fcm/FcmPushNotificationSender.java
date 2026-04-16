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

import bisq.relay.config.FcmProperties;
import bisq.relay.exception.ProviderFailureException;
import bisq.relay.notification.PushNotificationMessage;
import bisq.relay.notification.PushNotificationResult;
import bisq.relay.notification.PushNotificationSender;
import bisq.relay.notification.metrics.PushProvider;
import bisq.relay.notification.resilience.ResilienceAsyncExecutor;
import bisq.relay.util.FutureCancellationBridge;
import com.google.api.core.ApiFuture;
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
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static bisq.relay.notification.metrics.PushMetrics.PROVIDER_ID_FCM;

@PushProvider(PROVIDER_ID_FCM)
@Service
@ConditionalOnProperty(name = "fcm.enabled", havingValue = "true", matchIfMissing = false)
public class FcmPushNotificationSender implements PushNotificationSender {
    private static final Logger LOG = LoggerFactory.getLogger(FcmPushNotificationSender.class);

    private final FirebaseMessaging firebaseMessaging;
    private final FcmPushNotificationBuilder fcmPushNotificationBuilder;
    private final ResilienceAsyncExecutor resilienceAsyncExecutor;

    @Autowired
    public FcmPushNotificationSender(
            final FcmProperties fcmProperties,
            final FcmPushNotificationBuilder fcmPushNotificationBuilder,
            final ResilienceAsyncExecutor resilienceAsyncExecutor
    ) throws IOException {

        this.fcmPushNotificationBuilder = fcmPushNotificationBuilder;
        this.resilienceAsyncExecutor = resilienceAsyncExecutor;

        try (InputStream firebaseConfigStream = new FileInputStream(fcmProperties.getFirebaseConfigurationFile())) {
            GoogleCredentials googleCredentials = GoogleCredentials.fromStream(firebaseConfigStream);
            FirebaseOptions firebaseOptions = FirebaseOptions.builder()
                    .setCredentials(googleCredentials)
                    .setDatabaseUrl(fcmProperties.getFirebaseUrl())
                    .build();
            if (FirebaseApp.getApps().isEmpty()) {
                FirebaseApp.initializeApp(firebaseOptions);
            }
        }

        this.firebaseMessaging = FirebaseMessaging.getInstance();

        LOG.info("FCM client is ready to push notifications");
    }

    @VisibleForTesting
    FcmPushNotificationSender(
            final FirebaseMessaging firebaseMessaging,
            final FcmPushNotificationBuilder fcmPushNotificationBuilder,
            final ResilienceAsyncExecutor resilienceAsyncExecutor) {
        this.firebaseMessaging = firebaseMessaging;
        this.fcmPushNotificationBuilder = fcmPushNotificationBuilder;
        this.resilienceAsyncExecutor = resilienceAsyncExecutor;
    }

    @PreDestroy
    public void destroy() {
        LOG.info("Shutting down FCM client");
        for (FirebaseApp firebaseApp : FirebaseApp.getApps()) {
            firebaseApp.delete();
        }
    }

    /**
     * Sends a push notification to FCM under Resilience4j CircuitBreaker/Retry/TimeLimiter policies defined in
     * the application properties keyed by {@value bisq.relay.notification.metrics.PushMetrics#PROVIDER_ID_FCM}.
     * <p>
     * Token/payload rejections (client failures) return a normal {@link PushNotificationResult}.
     * Provider throttling/outage responses complete exceptionally to affect the circuit breaker.
     *
     * @param pushNotificationMessage encrypted payload and metadata
     * @param deviceToken             FCM device token
     * @return future completing with success/rejection result, or fallback result when unavailable
     */
    @Override
    public CompletableFuture<PushNotificationResult> sendNotification(
            @Nonnull final PushNotificationMessage pushNotificationMessage,
            @Nonnull final String deviceToken
    ) {
        Objects.requireNonNull(pushNotificationMessage, "pushNotificationMessage must not be null");
        Objects.requireNonNull(deviceToken, "deviceToken must not be null");

        return resilienceAsyncExecutor.execute(
                PROVIDER_ID_FCM,
                () -> doSend(pushNotificationMessage, deviceToken),
                ex -> sendNotificationFallback(deviceToken, ex)
        );
    }

    /**
     * Performs the outbound FCM call and returns a stage completing with the result.
     * <p>
     * Important: Outbound side effects occur inside this method, which shall only be invoked
     * if the circuit breaker permits execution.
     * <p>
     * Cancellation bridging: if the returned future is cancelled (e.g., by {@code TimeLimiter}),
     * the underlying provider future is also cancelled to reduce wasted work.
     */
    private CompletionStage<PushNotificationResult> doSend(
            @Nonnull final PushNotificationMessage pushNotificationMessage,
            @Nonnull final String deviceToken
    ) {
        final Message message = fcmPushNotificationBuilder.buildMessage(
                pushNotificationMessage,
                deviceToken,
                pushNotificationMessage.coalescingKey()
        );

        final ApiFuture<String> providerFuture = firebaseMessaging.sendAsync(message);

        final CompletableFuture<PushNotificationResult> resultFuture = new CompletableFuture<>();

        FutureCancellationBridge.bridgeCancellation(resultFuture, providerFuture);

        providerFuture.addListener(() -> {
                    // If already timed out or cancelled, ignore late results
                    if (resultFuture.isDone()) {
                        return;
                    }

                    try {
                        resultFuture.complete(mapFcmOutcome(providerFuture));
                    } catch (Exception t) {
                        resultFuture.completeExceptionally(t);
                    }
                },

                // The listener may run inline on the calling thread if already complete,
                // otherwise on the provider's completion thread. Keep the listener non-blocking/lightweight
                // since it can execute on Firebase/Google API internal threads.
                MoreExecutors.directExecutor()
        );

        return resultFuture;
    }

    /**
     * Maps the outcome of an FCM send attempt into a {@link PushNotificationResult} or an exception.
     *
     * @param providerFuture the FCM provider future representing an in-flight send operation
     * @return a normalized push notification result for accepted or non-breaker-relevant rejections
     * <ul>
     *   <li>Returns an accepted result when FCM returns a message id.</li>
     *   <li>Returns a rejected result for client rejections (e.g., invalid/unregistered token) that
     *   should not affect the circuit breaker.</li>
     * </ul>
     * @throws IOException              for transport-level failures (e.g., no response received)
     * @throws ProviderFailureException for breaker-relevant provider failures (throttling/outage/server errors)
     *                                  so resilience policies can record the call as failed.
     */
    private PushNotificationResult mapFcmOutcome(@Nonnull final ApiFuture<String> providerFuture)
            throws IOException, ProviderFailureException {

        try {
            final String messageId = providerFuture.get();
            LOG.info("Push notification accepted by FCM gateway; messageId={}", messageId);
            return new PushNotificationResult(true, null, null, false);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            LOG.warn("FCM send was interrupted");
            throw new IOException("FCM send was interrupted", ie);
        } catch (Exception e) {
            final Throwable cause = e.getCause() != null ? e.getCause() : e;

            final PushNotificationResult mapped = tryMapFirebaseMessagingException(cause);
            if (mapped != null) {
                return mapped;
            }

            LOG.error("Failed to send notification to FCM gateway; {}", cause.getMessage());
            // Preserve the original IOException subtype (e.g., SocketTimeoutException)
            // and avoid double wrapping; otherwise normalize to IOException.
            throw (cause instanceof IOException io) ? io : new IOException("FCM transport error", cause);
        }
    }

    /**
     * @return the mapped result if this is a FirebaseMessagingException with a MessagingErrorCode;
     * otherwise {@code null} to signal "not handled here".
     */
    private PushNotificationResult tryMapFirebaseMessagingException(@Nonnull final Throwable cause)
            throws ProviderFailureException {

        if (!(cause instanceof FirebaseMessagingException fme)) {
            return null;
        }

        final MessagingErrorCode messagingErrorCode = fme.getMessagingErrorCode();
        if (messagingErrorCode == null) {
            return null;
        }

        final String errorCode = messagingErrorCode.name();
        final String errorMessage = fme.getMessage();

        LOG.error("Push notification rejected by FCM gateway; [{}] {}",
                errorCode, errorMessage == null ? "" : errorMessage);

        if (isBreakerRelevantFcmError(messagingErrorCode)) {
            throw new ProviderFailureException("FCM provider outage/throttle: " + errorCode, cause);
        }

        return new PushNotificationResult(
                false,
                errorCode,
                errorMessage,
                messagingErrorCode == MessagingErrorCode.UNREGISTERED
        );
    }

    /**
     * Returns {@code true} if the FCM error should count as a provider failure (breaker-relevant).
     * <p>
     * <b>Breaker-relevant:</b> throttling and provider server errors should count as failures.
     * <br />
     * <b>Breaker-irrelevant:</b> token/payload/auth/other client rejections should NOT poison the breaker.
     *
     * @param errorCode FCM messaging error code
     * @return {@code true} if breaker should treat as failure
     */
    private static boolean isBreakerRelevantFcmError(@Nonnull final MessagingErrorCode errorCode) {
        return switch (errorCode) {
            case QUOTA_EXCEEDED, UNAVAILABLE, INTERNAL -> true;
            default -> false;
        };
    }

    /**
     * Fallback used when the circuit breaker is OPEN, or timeouts/retries are exhausted.
     *
     * @param deviceToken original device token
     * @param ex          cause (e.g., CallNotPermittedException, TimeoutException, etc.)
     * @return completed future with a service-unavailable result
     */
    private CompletableFuture<PushNotificationResult> sendNotificationFallback(
            @Nonnull final String deviceToken,
            @Nonnull final Throwable ex) {

        LOG.warn("FCM fallback triggered for token [{}]", deviceToken, ex);

        return CompletableFuture.completedFuture(new PushNotificationResult(
                false,
                "ServiceUnavailable",
                "Push provider temporarily unavailable (FCM): " + ex.getMessage(),
                false
        ));
    }
}
