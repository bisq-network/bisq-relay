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

package bisq.relay.notification.metrics;

import bisq.relay.notification.PushNotificationMessage;
import bisq.relay.notification.PushNotificationResult;
import bisq.relay.notification.PushNotificationSender;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.springframework.beans.factory.ObjectProvider;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static bisq.relay.notification.metrics.PushMetrics.*;

/**
 * Decorator that adds Micrometer metrics to any {@link PushNotificationSender}.
 * <p>Emits the following metrics:
 * <ul>
 *   <li>{@code push_attempts_total{provider}}</li>
 *   <li>{@code push_total{provider,result=accepted|rejected|error}}</li>
 *   <li>{@code push_latency_seconds_bucket{provider,result,code,le=...}}</li>
 * </ul>
 * <p><strong>Note:</strong> The {@link MeterRegistry} is resolved lazily via {@link ObjectProvider}
 * to avoid early bean initialization during post-processing. If no registry is available, this
 * decorator becomes a no-op and simply delegates for the {@link PushNotificationSender}.
 */
final class MetricsPushNotificationSender implements PushNotificationSender {

    private static final double[] PUSH_LATENCY_PERCENTILES = {0.5, 0.9, 0.95, 0.99};
    private static final Duration PUSH_LATENCY_MAX_EXPECTED_DURATION = Duration.ofSeconds(10);

    private final String providerId;
    private final PushNotificationSender pushNotificationSender;
    private final ObjectProvider<MeterRegistry> registryProvider;

    /**
     * Creates a metrics-decorated push notification sender.
     *
     * @param providerId             provider id (e.g., {@link PushMetrics#PROVIDER_ID_APNS}, {@link PushMetrics#PROVIDER_ID_FCM})
     * @param pushNotificationSender underlying push notification sender to delegate for
     * @param registryProvider       lazy provider for {@link MeterRegistry}; may yield {@code null} at runtime
     */
    public MetricsPushNotificationSender(
            @Nonnull final String providerId,
            @Nonnull final PushNotificationSender pushNotificationSender,
            @Nonnull final ObjectProvider<MeterRegistry> registryProvider) {
        this.providerId = Objects.requireNonNull(providerId, "providerId must not be null");
        this.pushNotificationSender = Objects.requireNonNull(pushNotificationSender, "pushNotificationSender must not be null");
        this.registryProvider = Objects.requireNonNull(registryProvider, "registryProvider must not be null");
    }

    @Override
    public CompletableFuture<PushNotificationResult> sendNotification(
            @Nonnull final PushNotificationMessage message,
            @Nonnull final String deviceToken) {

        // Resolve registry lazily and tolerate absence (e.g., in certain tests)
        final MeterRegistry registry = registryProvider.getIfAvailable();

        final long startNanos = System.nanoTime();

        if (registry != null) {
            // Count the attempt (low cardinality: only provider tag)
            registry.counter(METRIC_PUSH_ATTEMPTS_TOTAL, TAG_PROVIDER, providerId).increment();
        }

        return pushNotificationSender.sendNotification(message, deviceToken)
                .whenComplete((result, error) -> {
                    if (registry == null) {
                        // No metrics backend available; nothing to record
                        return;
                    }

                    final String outcome;
                    final String code;

                    if (error != null) {
                        outcome = RESULT_ERROR;
                        code = CODE_IO;
                    } else if (result != null && result.wasAccepted()) {
                        outcome = RESULT_ACCEPTED;
                        code = CODE_NONE;
                    } else {
                        outcome = RESULT_REJECTED;
                        code = classifyCode(providerId, result != null ? result.errorCode() : null);
                    }

                    // Count the result (providerId + outcome)
                    registry.counter(METRIC_PUSH_TOTAL,
                            TAG_PROVIDER, providerId,
                            TAG_RESULT, outcome).increment();

                    // Record the latency (providerId, outcome, code)
                    final long durationNanos = System.nanoTime() - startNanos;
                    Timer.builder(METRIC_PUSH_LATENCY_SECONDS)
                            .tags(TAG_PROVIDER, providerId, TAG_RESULT, outcome, TAG_CODE, code)
                            .publishPercentiles(PUSH_LATENCY_PERCENTILES)
                            .maximumExpectedValue(PUSH_LATENCY_MAX_EXPECTED_DURATION)
                            .register(registry)
                            .record(durationNanos, TimeUnit.NANOSECONDS);
                });
    }

    /**
     * Maps raw provider error codes into a bounded set of tag values from {@link PushMetrics}.
     *
     * @param providerId the provider id (e.g., {@link PushMetrics#PROVIDER_ID_APNS}, {@link PushMetrics#PROVIDER_ID_FCM})
     * @param errorCode  the raw error code returned by the provider
     * @return a low-cardinality classification tag value
     */
    private static String classifyCode(@Nonnull final String providerId, @Nullable final String errorCode) {
        if (errorCode == null) {
            return CODE_OTHER;
        }

        if (PROVIDER_ID_APNS.equals(providerId)) {
            return switch (errorCode) {
                case "Unregistered", "BadDeviceToken" -> CODE_TOKEN;
                case "TooManyRequests" -> CODE_THROTTLE;
                case "PayloadTooLarge" -> CODE_PAYLOAD;
                case "InternalServerError", "ServiceUnavailable" -> CODE_SERVER;
                default -> CODE_OTHER;
            };
        }

        if (PROVIDER_ID_FCM.equals(providerId)) {
            return switch (errorCode) {
                case "UNREGISTERED", "INVALID_ARGUMENT" -> CODE_TOKEN;
                case "QUOTA_EXCEEDED" -> CODE_THROTTLE;
                case "MESSAGE_TOO_BIG" -> CODE_PAYLOAD;
                case "UNAVAILABLE", "INTERNAL" -> CODE_SERVER;
                case "SENDER_ID_MISMATCH" -> CODE_AUTH;
                default -> CODE_OTHER;
            };
        }

        return CODE_OTHER;
    }
}
