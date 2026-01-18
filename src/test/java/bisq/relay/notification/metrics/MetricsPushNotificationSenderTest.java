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
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.annotation.Nonnull;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.ObjectProvider;

import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import static bisq.relay.notification.metrics.PushMetrics.*;
import static org.assertj.core.api.Assertions.assertThat;

class MetricsPushNotificationSenderTest {

    private static final PushNotificationMessage MSG = new PushNotificationMessage("foo", true);

    private static ObjectProvider<MeterRegistry> providerOf(@Nonnull final MeterRegistry registry) {
        return new ObjectProvider<>() {
            @Override
            @Nonnull
            public MeterRegistry getObject() {
                return registry;
            }

            @Override
            @Nonnull
            public MeterRegistry getObject(@Nonnull final Object... args) {
                return registry;
            }

            @Override
            @Nonnull
            public MeterRegistry getIfAvailable() {
                return registry;
            }

            @Override
            @Nonnull
            public MeterRegistry getIfUnique() {
                return registry;
            }

            @Override
            @Nonnull
            public Stream<MeterRegistry> orderedStream() {
                return Stream.of(registry);
            }

            @Override
            @Nonnull
            public Stream<MeterRegistry> stream() {
                return Stream.of(registry);
            }
        };
    }

    private final MeterRegistry registry = new SimpleMeterRegistry();

    @ParameterizedTest
    @ValueSource(strings = {PROVIDER_ID_APNS, PROVIDER_ID_FCM})
    void whenPushNotificationAccepted_thenAcceptedMetricsRecorded(final String providerId) {
        PushNotificationSender senderThatReturnsSuccessfulResponse = (m, tok) ->
                CompletableFuture.completedFuture(
                        new PushNotificationResult(true, null, null, false));

        MetricsPushNotificationSender metricsSender = new MetricsPushNotificationSender(
                providerId, senderThatReturnsSuccessfulResponse, providerOf(registry));

        metricsSender.sendNotification(MSG, "tok").join();

        assertThat(registry.get(METRIC_PUSH_ATTEMPTS_TOTAL).tag(TAG_PROVIDER, providerId).counter().count())
                .describedAs(METRIC_PUSH_ATTEMPTS_TOTAL)
                .isEqualTo(1.0);
        assertThat(registry.get(METRIC_PUSH_TOTAL)
                .tags(TAG_PROVIDER, providerId, TAG_RESULT, RESULT_ACCEPTED)
                .counter().count())
                .describedAs(String.format("%s{%s,%s}", METRIC_PUSH_TOTAL, providerId, RESULT_ACCEPTED))
                .isEqualTo(1.0);
        assertThat(registry.get(METRIC_PUSH_LATENCY_SECONDS)
                .tags(TAG_PROVIDER, providerId, TAG_RESULT, RESULT_ACCEPTED, TAG_CODE, CODE_NONE)
                .timer().count())
                .describedAs(String.format("%s{%s,%s,%s}", METRIC_PUSH_LATENCY_SECONDS, providerId, RESULT_ACCEPTED, CODE_NONE))
                .isEqualTo(1);
    }

    @ParameterizedTest
    @MethodSource("provideProviderIdAndTokenErrorCode")
    void whenPushNotificationRejected_thenRejectedMetricsRecorded(final String providerId, final String errorCode) {
        PushNotificationSender senderThatReturnsTokenError = (m, tok) ->
                CompletableFuture.completedFuture(
                        new PushNotificationResult(false, errorCode, "msg", true));

        MetricsPushNotificationSender metricsSender = new MetricsPushNotificationSender(
                providerId, senderThatReturnsTokenError, providerOf(registry));

        metricsSender.sendNotification(MSG, "tok").join();

        assertThat(registry.get(METRIC_PUSH_TOTAL)
                .tags(TAG_PROVIDER, providerId, TAG_RESULT, RESULT_REJECTED)
                .counter().count())
                .describedAs(String.format("%s{%s,%s}", METRIC_PUSH_TOTAL, providerId, RESULT_REJECTED))
                .isEqualTo(1.0);
        assertThat(registry.get(METRIC_PUSH_LATENCY_SECONDS)
                .tags(TAG_PROVIDER, providerId, TAG_RESULT, RESULT_REJECTED, TAG_CODE, CODE_TOKEN)
                .timer().count())
                .describedAs(String.format("%s{%s,%s,%s}", METRIC_PUSH_LATENCY_SECONDS, providerId, RESULT_REJECTED, CODE_TOKEN))
                .isEqualTo(1);
    }

    private static Stream<Arguments> provideProviderIdAndTokenErrorCode() {
        return Stream.of(
                Arguments.of(PROVIDER_ID_APNS, "BadDeviceToken"),
                Arguments.of(PROVIDER_ID_FCM, "UNREGISTERED")
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {PROVIDER_ID_APNS, PROVIDER_ID_FCM})
    void whenPushNotificationError_thenErrorMetricsRecorded(final String providerId) {
        PushNotificationSender senderThatThrowsException = (m, tok) -> {
            var cf = new CompletableFuture<PushNotificationResult>();
            cf.completeExceptionally(new RuntimeException("boom"));
            return cf;
        };

        MetricsPushNotificationSender metricsSender = new MetricsPushNotificationSender(
                providerId, senderThatThrowsException, providerOf(registry));

        try {
            metricsSender.sendNotification(MSG, "tok").join();
        } catch (Exception ignored) {
            // ignored
        }

        assertThat(registry.get(METRIC_PUSH_TOTAL)
                .tags(TAG_PROVIDER, providerId, TAG_RESULT, RESULT_ERROR)
                .counter().count())
                .describedAs(String.format("%s{%s,%s}", METRIC_PUSH_TOTAL, providerId, RESULT_ERROR))
                .isEqualTo(1.0);
        assertThat(registry.get(METRIC_PUSH_LATENCY_SECONDS)
                .tags(TAG_PROVIDER, providerId, TAG_RESULT, RESULT_ERROR, TAG_CODE, CODE_IO)
                .timer().count())
                .describedAs(String.format("%s{%s,%s,%s}", METRIC_PUSH_LATENCY_SECONDS, providerId, RESULT_ERROR, CODE_IO))
                .isEqualTo(1);
    }
}
