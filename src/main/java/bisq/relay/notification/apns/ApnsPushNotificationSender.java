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

import bisq.relay.config.ApnsProperties;
import bisq.relay.exception.ProviderFailureException;
import bisq.relay.notification.PushNotificationMessage;
import bisq.relay.notification.PushNotificationResult;
import bisq.relay.notification.PushNotificationSender;
import bisq.relay.notification.metrics.PushProvider;
import bisq.relay.notification.resilience.ResilienceAsyncExecutor;
import bisq.relay.util.FutureCancellationBridge;
import com.eatthepath.pushy.apns.ApnsClient;
import com.eatthepath.pushy.apns.ApnsClientBuilder;
import com.eatthepath.pushy.apns.PushNotificationResponse;
import com.eatthepath.pushy.apns.util.SimpleApnsPushNotification;
import com.eatthepath.pushy.apns.util.concurrent.PushNotificationFuture;
import com.google.common.annotations.VisibleForTesting;
import jakarta.annotation.Nonnull;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Objects;
import java.util.Scanner;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static bisq.relay.notification.metrics.PushMetrics.PROVIDER_ID_APNS;

@PushProvider(PROVIDER_ID_APNS)
@Service
public class ApnsPushNotificationSender implements PushNotificationSender {
    private static final Logger LOG = LoggerFactory.getLogger(ApnsPushNotificationSender.class);

    private final String apnsBundleId;
    private final ApnsClient apnsClient;
    private final ApnsPushNotificationBuilder apnsPushNotificationBuilder;
    private final ResilienceAsyncExecutor resilienceAsyncExecutor;

    @Autowired
    public ApnsPushNotificationSender(
            final ApnsProperties apnsProperties,
            final ApnsPushNotificationBuilder apnsPushNotificationBuilder,
            final ResilienceAsyncExecutor resilienceAsyncExecutor
    ) throws IOException {

        this.apnsBundleId = apnsProperties.getBundleId();
        this.apnsPushNotificationBuilder = apnsPushNotificationBuilder;
        this.resilienceAsyncExecutor = resilienceAsyncExecutor;

        final String appleCertPassword;
        try (Scanner scanner = new Scanner(new FileInputStream(apnsProperties.getCertificatePasswordFile()))) {
            appleCertPassword = scanner.next();
        }

        final File appleCertFile = new File(apnsProperties.getCertificateFile());

        final String apnsHost = apnsProperties.isUseSandbox()
                ? ApnsClientBuilder.DEVELOPMENT_APNS_HOST
                : ApnsClientBuilder.PRODUCTION_APNS_HOST;

        apnsClient = new ApnsClientBuilder()
                .setApnsServer(apnsHost)
                .setClientCredentials(appleCertFile, appleCertPassword)
                .build();

        LOG.info("APNs client is ready to push notifications (sandbox={})", apnsProperties.isUseSandbox());
    }

    @VisibleForTesting
    ApnsPushNotificationSender(
            final ApnsClient apnsClient,
            final String apnsBundleId,
            final ApnsPushNotificationBuilder apnsPushNotificationBuilder,
            final ResilienceAsyncExecutor resilienceAsyncExecutor) {
        this.apnsClient = apnsClient;
        this.apnsBundleId = apnsBundleId;
        this.apnsPushNotificationBuilder = apnsPushNotificationBuilder;
        this.resilienceAsyncExecutor = resilienceAsyncExecutor;
    }

    @PreDestroy
    public void destroy() {
        LOG.info("Shutting down APNs client");
        apnsClient.close().join();
    }

    /**
     * Sends a push notification to APNs under Resilience4j CircuitBreaker/Retry/TimeLimiter policies defined in
     * the application properties keyed by {@value bisq.relay.notification.metrics.PushMetrics#PROVIDER_ID_APNS}.
     * <p>
     * Token/payload rejections (client failures) return a normal {@link PushNotificationResult}.
     * Provider throttling/outage responses complete exceptionally to affect the circuit breaker.
     *
     * @param pushNotificationMessage encrypted payload and metadata
     * @param deviceToken             APNs device token
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
                PROVIDER_ID_APNS,
                () -> doSend(pushNotificationMessage, deviceToken),
                ex -> sendNotificationFallback(deviceToken, ex)
        );
    }

    /**
     * Performs the outbound APNs call and returns a stage completing with the result.
     * <p>
     * Important: Outbound side effects occur inside this method, which shall only be invoked
     * if the circuit breaker permits execution.
     * <p>
     * Cancellation bridging: if the returned future is cancelled (e.g., by {@code TimeLimiter}),
     * then the underlying provider future is also cancelled to reduce wasted work.
     */
    private CompletionStage<PushNotificationResult> doSend(
            @Nonnull final PushNotificationMessage pushNotificationMessage,
            @Nonnull final String deviceToken
    ) {
        final SimpleApnsPushNotification notification = apnsPushNotificationBuilder.buildPushNotification(
                pushNotificationMessage,
                deviceToken,
                apnsBundleId,
                pushNotificationMessage.coalescingKey()
        );

        final PushNotificationFuture<SimpleApnsPushNotification, PushNotificationResponse<SimpleApnsPushNotification>>
                providerFuture = apnsClient.sendNotification(notification);

        final CompletableFuture<PushNotificationResult> resultFuture = new CompletableFuture<>();

        FutureCancellationBridge.bridgeCancellation(resultFuture, providerFuture);

        providerFuture.whenComplete((response, cause) -> {
            // If already timed out or cancelled, ignore late results
            if (resultFuture.isDone()) {
                return;
            }

            try {
                resultFuture.complete(mapApnsOutcome(response, cause));
            } catch (Exception t) {
                resultFuture.completeExceptionally(t);
            }
        });

        return resultFuture;
    }

    /**
     * Maps the outcome of an APNs send attempt into a {@link PushNotificationResult} or an exception.
     *
     * @param response the APNs response, or {@code null} if a transport error occurred
     * @param cause    the transport failure cause (might be {@code null} when {@code response} is {@code null})
     * @return a normalized push notification result for accepted or non-breaker-relevant rejections
     * <ul>
     *   <li>Returns an accepted result when {@code response.isAccepted()} is {@code true}.</li>
     *   <li>Returns a rejected result for client rejections (e.g., bad token) that should not affect
     *      the circuit breaker.</li>
     * </ul>
     * @throws IOException              for transport-level failures (e.g., no response received)
     * @throws ProviderFailureException for breaker-relevant provider failures (throttling/outage/server errors)
     *                                  so resilience policies can record the call as failed.
     */
    private PushNotificationResult mapApnsOutcome(
            final PushNotificationResponse<SimpleApnsPushNotification> response,
            final Throwable cause
    ) throws IOException, ProviderFailureException {

        if (response == null) {
            // Something went wrong when trying to send the notification to the
            // APNs server. Note that this is distinct from a rejection from
            // the server and indicates that something went wrong when actually
            // sending the notification or waiting for a reply.
            LOG.error("Failed to send notification to APNs gateway; {}",
                    cause == null ? "unknown cause" : cause.getMessage());

            // Preserve the original IOException subtype (e.g., SocketTimeoutException)
            // and avoid double wrapping; otherwise normalize to IOException.
            throw (cause instanceof IOException io) ? io : new IOException("APNs transport error", cause);
        }

        if (response.isAccepted()) {
            LOG.info("Push notification accepted by APNs gateway; apnsId={}", response.getApnsId());
            return new PushNotificationResult(true, null, null, false);
        }

        final String errorCode = response.getRejectionReason().orElse("unknown reason");
        final String errorMessage = response.getTokenInvalidationTimestamp()
                .map(ts -> String.format("Token is invalid as of %s", ts))
                .orElse(null);

        LOG.error("Push notification rejected by APNs gateway; [{}] {}",
                errorCode, errorMessage == null ? "" : errorMessage);

        if (isBreakerRelevantApnsRejection(errorCode)) {
            throw new ProviderFailureException("APNs provider outage/throttle: " + errorCode);
        }

        return new PushNotificationResult(false, errorCode, errorMessage, isUnregisteredApnsToken(errorCode));
    }

    /**
     * Returns {@code true} if the given APNs rejection reason indicates that the device token is no longer valid
     * (i.e., the app instance should be treated as unregistered).
     *
     * @param errorCode the APNs rejection reason
     * @return {@code true} if the rejection reason corresponds to an invalid/unregistered token
     */
    private static boolean isUnregisteredApnsToken(final String errorCode) {
        return "Unregistered".equals(errorCode) || "BadDeviceToken".equals(errorCode);
    }

    /**
     * Returns {@code true} if the APNs rejection reason should count as a provider failure (breaker-relevant).
     * <p>
     * <b>Breaker-relevant:</b> throttling and provider server errors should count as failures.
     * <br />
     * <b>Breaker-irrelevant:</b> token/payload/auth/other client rejections should NOT poison the breaker.
     *
     * @param errorCode APNs rejection reason
     * @return {@code true} if breaker should treat as failure
     */
    private static boolean isBreakerRelevantApnsRejection(final String errorCode) {
        return switch (errorCode) {
            case "TooManyRequests", "ServiceUnavailable", "InternalServerError" -> true;
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

        LOG.warn("APNs fallback triggered for token [{}]", deviceToken, ex);

        return CompletableFuture.completedFuture(new PushNotificationResult(
                false,
                "ServiceUnavailable",
                "Push provider temporarily unavailable (APNs): " + ex.getMessage(),
                false
        ));
    }
}
