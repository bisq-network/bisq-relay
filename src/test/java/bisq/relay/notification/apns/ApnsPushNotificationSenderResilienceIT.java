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
import bisq.relay.notification.resilience.ResilienceAsyncExecutor;
import bisq.relaytest.config.ResilienceTestConfig;
import com.eatthepath.pushy.apns.ApnsClient;
import com.eatthepath.pushy.apns.PushNotificationResponse;
import com.eatthepath.pushy.apns.util.SimpleApnsPushNotification;
import com.eatthepath.pushy.apns.util.concurrent.PushNotificationFuture;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreaker.State;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiConsumer;

import static bisq.relay.notification.metrics.PushMetrics.PROVIDER_ID_APNS;
import static bisq.relaytest.utils.CircuitBreakerUtil.halfOpenCircuitBreaker;
import static bisq.relaytest.utils.CircuitBreakerUtil.openCircuitBreaker;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest(classes = {
        ResilienceTestConfig.class,
        ApnsPushNotificationSenderResilienceIT.ApnsTestConfig.class
})
@ActiveProfiles("integrationtest")
class ApnsPushNotificationSenderResilienceIT {

    private static final String DEVICE_TOKEN =
            "d45161df3d172837f1b83bb3e411d5a63120de6b435ff9235adb70d619d162a1";
    private static final String APNS_BUNDLE_ID = "bisqremote.joachimneumann.com";

    @Autowired
    private ApnsPushNotificationSender sender;

    @Autowired
    private ApnsClient apnsClient;

    @Autowired
    private ApnsPushNotificationBuilder builder;

    @Autowired
    private CircuitBreakerRegistry cbRegistry;

    @BeforeEach
    void setup() {
        cbRegistry.circuitBreaker(PROVIDER_ID_APNS).transitionToClosedState();
        reset(apnsClient);

        when(builder.buildPushNotification(any(), anyString(), anyString(), anyString()))
                .thenReturn(new SimpleApnsPushNotification(DEVICE_TOKEN, "topic", "payload"));
    }

    /**
     * Verifies OPEN breaker fully short-circuits the call and never invokes the outbound APNs client.
     */
    @Test
    void whenBreakerIsOpen_thenCallIsShortCircuited_andNoOutboundCallIsMade() {
        CircuitBreaker cb = cbRegistry.circuitBreaker(PROVIDER_ID_APNS);
        openCircuitBreaker(cb);

        PushNotificationResult result = sender.sendNotification(
                        new PushNotificationMessage("encrypted", false, false), DEVICE_TOKEN)
                .join();

        assertThat(result.wasAccepted()).isFalse();
        assertThat(result.errorCode()).isEqualTo("ServiceUnavailable");

        verify(apnsClient, never()).sendNotification(any(SimpleApnsPushNotification.class));
    }

    /**
     * Verifies HALF_OPEN permit exhaustion prevents additional calls from invoking the outbound APNs client.
     */
    @Test
    void whenHalfOpenPermitIsExhausted_thenCallIsShortCircuited_andNoOutboundCallIsMade() {
        CircuitBreaker cb = cbRegistry.circuitBreaker(PROVIDER_ID_APNS);
        halfOpenCircuitBreaker(cb);

        // Intentionally consume the permitted number of calls in HALF_OPEN state
        // so the next call shall be rejected.
        // This assumes permittedNumberOfCallsInHalfOpenState=2
        for (int i = 0; i < 2; i++) {
            assertThat(cb.tryAcquirePermission()).isTrue();
        }

        PushNotificationResult result = sender.sendNotification(
                        new PushNotificationMessage("encrypted", false, false), DEVICE_TOKEN)
                .join();

        assertThat(result.wasAccepted()).isFalse();
        assertThat(result.errorCode()).isEqualTo("ServiceUnavailable");

        verify(apnsClient, never()).sendNotification(any(SimpleApnsPushNotification.class));
    }

    /**
     * Verifies that a failed probe call while the circuit breaker is in HALF_OPEN state
     * causes the breaker to transition back to OPEN.
     */
    @Test
    void whenHalfOpenProbeFails_thenBreakerTransitionsBackToOpen() {
        CircuitBreaker cb = cbRegistry.circuitBreaker(PROVIDER_ID_APNS);
        halfOpenCircuitBreaker(cb);

        // Fail the probe with a transport error (breaker-relevant)
        givenApnsWillFailWith(new IOException("timeout"));

        // This assumes permittedNumberOfCallsInHalfOpenState=2
        for (int i = 0; i < 2; i++) {
            sender.sendNotification(
                            new PushNotificationMessage(
                                    "encrypted", false, false), DEVICE_TOKEN)
                    .join();
        }

        assertThat(cb.getState()).isEqualTo(State.OPEN);
    }

    /**
     * Verifies that after OPEN wait duration elapses, the next call is permitted as a probe and can close the breaker.
     */
    @Test
    void whenOpenWaitDurationElapsed_thenNextCallIsPermitted_andBreakerCanClose() {
        CircuitBreaker cb = cbRegistry.circuitBreaker(PROVIDER_ID_APNS);
        openCircuitBreaker(cb);

        // Wait for the OPEN wait duration to elapse
        // This assumes waitDurationInOpenState <= 3s (in test properties)
        Awaitility.await().pollDelay(Duration.ofSeconds(3)).until(() -> true);

        // The next call should be permitted as a probe; make it succeed so the breaker can close
        givenApnsWillAccept();

        PushNotificationResult result = sender
                .sendNotification(new PushNotificationMessage(
                        "encrypted", false, false), DEVICE_TOKEN)
                .join();

        assertThat(result.wasAccepted()).isTrue();
        assertThat(cb.getState()).isEqualTo(State.HALF_OPEN);
        verify(apnsClient, times(1)).sendNotification(any(SimpleApnsPushNotification.class));
    }

    /**
     * Verifies repeated transport errors (breaker-relevant) open the breaker, and subsequent calls are short-circuited.
     */
    @Test
    void whenSufficientTransportErrors_thenBreakerOpens_andFurtherCallsAreShortCircuited() {
        CircuitBreaker cb = cbRegistry.circuitBreaker(PROVIDER_ID_APNS);
        assertThat(cb.getState()).isEqualTo(State.CLOSED);

        // First: make enough failing calls to OPEN the circuit breaker
        // Use a transport failure (e.g., IOException)
        givenApnsWillFailWith(new IOException("timeout"));

        // This assumes minimumNumberOfCalls=5
        for (int i = 0; i < 5; i++) {
            sender.sendNotification(
                            new PushNotificationMessage(
                                    "encrypted", false, false), DEVICE_TOKEN)
                    .join();
        }

        assertThat(cb.getState()).isIn(State.OPEN, State.HALF_OPEN); // depending on timing

        reset(apnsClient);

        // Second: with CB OPEN, the next call should go straight to fall back
        PushNotificationResult fallbackResult = sender
                .sendNotification(new PushNotificationMessage(
                                "encrypted", false, false),
                        DEVICE_TOKEN)
                .join();
        assertThat(fallbackResult.wasAccepted()).isFalse();
        assertThat(fallbackResult.errorCode()).isEqualTo("ServiceUnavailable");
        verify(apnsClient, never()).sendNotification(any(SimpleApnsPushNotification.class));
    }

    @Test
    void whenPushIsAccepted_thenCbStaysClosed() {
        CircuitBreaker cb = cbRegistry.circuitBreaker(PROVIDER_ID_APNS);
        assertThat(cb.getState()).isEqualTo(State.CLOSED);

        givenApnsWillAccept();

        PushNotificationResult result = sender
                .sendNotification(new PushNotificationMessage(
                                "encrypted", false, false),
                        DEVICE_TOKEN)
                .join();

        assertThat(result.wasAccepted()).isTrue();
        assertThat(result.errorCode()).isNull();

        assertThat(cb.getState()).isEqualTo(State.CLOSED);
    }

    @Test
    void whenCbHalfOpen_andPushIsAccepted_thenCbCloses() {
        CircuitBreaker cb = cbRegistry.circuitBreaker(PROVIDER_ID_APNS);
        halfOpenCircuitBreaker(cb);
        assertThat(cb.getState()).isEqualTo(State.HALF_OPEN);

        givenApnsWillAccept();

        // This assumes permittedNumberOfCallsInHalfOpenState=2
        PushNotificationResult result = sender
                .sendNotification(new PushNotificationMessage(
                                "encrypted", false, false),
                        DEVICE_TOKEN)
                .join();
        assertThat(result.wasAccepted()).isTrue();
        assertThat(result.errorCode()).isNull();
        assertThat(cb.getState()).isEqualTo(State.HALF_OPEN);
        result = sender
                .sendNotification(new PushNotificationMessage(
                                "encrypted", false, false),
                        DEVICE_TOKEN)
                .join();
        assertThat(result.wasAccepted()).isTrue();
        assertThat(result.errorCode()).isNull();
        assertThat(cb.getState()).isEqualTo(State.CLOSED);
        verify(apnsClient, times(2)).sendNotification(any(SimpleApnsPushNotification.class));
    }

    @Test
    void whenPushIsRejectedWithClientError_thenCbDoesNotOpen() {
        CircuitBreaker cb = cbRegistry.circuitBreaker(PROVIDER_ID_APNS);
        assertThat(cb.getState()).isEqualTo(State.CLOSED);

        // Unregistered is a client-side error (400) which shouldn't trip the breaker
        givenApnsWillReject("Unregistered");

        // This assumes minimumNumberOfCalls=5
        for (int i = 0; i < 5; i++) {
            PushNotificationResult result = sender
                    .sendNotification(new PushNotificationMessage(
                                    "encrypted", false, false),
                            DEVICE_TOKEN)
                    .join();
            assertThat(result.wasAccepted()).isFalse();
            assertThat(result.isUnregistered()).isTrue();
            assertThat(result.errorCode()).isEqualTo("Unregistered");
        }

        assertThat(cb.getState()).isEqualTo(State.CLOSED);
    }

    @Test
    void whenSufficientThrottleResponses_thenCbOpens_andFallbackReturnsServiceUnavailable() {
        CircuitBreaker cb = cbRegistry.circuitBreaker(PROVIDER_ID_APNS);
        assertThat(cb.getState()).isEqualTo(State.CLOSED);

        givenApnsWillReject("TooManyRequests");

        // This assumes minimumNumberOfCalls=5
        for (int i = 0; i < 5; i++) {
            sender.sendNotification(
                            new PushNotificationMessage(
                                    "encrypted", false, false),
                            DEVICE_TOKEN)
                    .join();
        }

        assertThat(cb.getState()).isIn(State.OPEN, State.HALF_OPEN);

        PushNotificationResult fallbackResult = sender
                .sendNotification(new PushNotificationMessage(
                                "encrypted", false, false),
                        DEVICE_TOKEN)
                .join();

        assertThat(fallbackResult.wasAccepted()).isFalse();
        assertThat(fallbackResult.errorCode()).isEqualTo("ServiceUnavailable");
    }

    @Test
    void whenSufficientProviderServerErrors_thenCbOpens_andFallbackReturnsServiceUnavailable() {
        CircuitBreaker cb = cbRegistry.circuitBreaker(PROVIDER_ID_APNS);
        assertThat(cb.getState()).isEqualTo(State.CLOSED);

        givenApnsWillReject("ServiceUnavailable");

        // This assumes minimumNumberOfCalls=5
        for (int i = 0; i < 5; i++) {
            sender.sendNotification(
                            new PushNotificationMessage(
                                    "encrypted", false, false),
                            DEVICE_TOKEN)
                    .join();
        }

        assertThat(cb.getState()).isIn(State.OPEN, State.HALF_OPEN);

        PushNotificationResult fallbackResult = sender
                .sendNotification(new PushNotificationMessage(
                                "encrypted", false, false),
                        DEVICE_TOKEN)
                .join();

        assertThat(fallbackResult.wasAccepted()).isFalse();
        assertThat(fallbackResult.errorCode()).isEqualTo("ServiceUnavailable");
    }

    // ---------- Helpers to simulate APNs responses ----------

    private void givenApnsWillAccept() {
        var future = pushyFutureCompletingWith(accepted(), null);
        when(apnsClient.sendNotification(any(SimpleApnsPushNotification.class))).thenReturn(future);
    }

    private void givenApnsWillFailWith(final Exception exception) {
        var future = pushyFutureCompletingWith(null, exception);
        when(apnsClient.sendNotification(any(SimpleApnsPushNotification.class))).thenReturn(future);
    }

    private void givenApnsWillReject(final String reason) {
        var future = pushyFutureCompletingWith(rejected(reason, Instant.now()), null);
        when(apnsClient.sendNotification(any(SimpleApnsPushNotification.class))).thenReturn(future);
    }

    private PushNotificationFuture<SimpleApnsPushNotification,
            PushNotificationResponse<SimpleApnsPushNotification>> pushyFutureCompletingWith(
            PushNotificationResponse<SimpleApnsPushNotification> response, Throwable error) {

        @SuppressWarnings("unchecked")
        PushNotificationFuture<SimpleApnsPushNotification, PushNotificationResponse<SimpleApnsPushNotification>> fut =
                (PushNotificationFuture<SimpleApnsPushNotification, PushNotificationResponse<SimpleApnsPushNotification>>)
                        mock(PushNotificationFuture.class);

        when(fut.whenComplete(any())).thenAnswer(inv -> {
            @SuppressWarnings("unchecked")
            BiConsumer<PushNotificationResponse<SimpleApnsPushNotification>, Throwable> consumer =
                    inv.getArgument(0, BiConsumer.class);
            consumer.accept(response, error);
            return fut;
        });

        return fut;
    }

    @SuppressWarnings("unchecked")
    private PushNotificationResponse<SimpleApnsPushNotification> accepted() {
        var response = mock(PushNotificationResponse.class);
        when(response.isAccepted()).thenReturn(true);
        when(response.getApnsId()).thenReturn(UUID.randomUUID());
        when(response.getRejectionReason()).thenReturn(Optional.empty());
        when(response.getTokenInvalidationTimestamp()).thenReturn(Optional.empty());
        return response;
    }

    @SuppressWarnings({"unchecked", "unused"})
    private PushNotificationResponse<SimpleApnsPushNotification> rejected(String reason, Instant tokenInvalidSince) {
        var response = mock(PushNotificationResponse.class);
        when(response.isAccepted()).thenReturn(false);
        when(response.getApnsId()).thenReturn(UUID.randomUUID());
        when(response.getRejectionReason()).thenReturn(Optional.ofNullable(reason));
        when(response.getTokenInvalidationTimestamp()).thenReturn(Optional.ofNullable(tokenInvalidSince));
        return response;
    }

    /**
     * Spring test configuration: wires the APNs sender with mocks.
     */
    @Configuration
    static class ApnsTestConfig {

        @Bean
        ApnsPushNotificationSender apnsPushNotificationSender(
                ApnsClient apnsClient,
                ApnsPushNotificationBuilder builder,
                ResilienceAsyncExecutor executor
        ) {
            return new ApnsPushNotificationSender(apnsClient, APNS_BUNDLE_ID, builder, executor);
        }

        @Bean
        @Primary
        ApnsPushNotificationBuilder apnsPushNotificationBuilder() {
            return mock(ApnsPushNotificationBuilder.class);
        }

        @Bean
        @Primary
        ApnsClient apnsClient() {
            return mock(ApnsClient.class);
        }
    }
}
