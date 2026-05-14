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
import bisq.relay.notification.resilience.ResilienceAsyncExecutor;
import bisq.relaytest.config.ResilienceTestConfig;
import com.google.api.core.SettableApiFuture;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
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

import static bisq.relay.notification.metrics.PushMetrics.PROVIDER_ID_FCM;
import static bisq.relaytest.utils.CircuitBreakerUtil.halfOpenCircuitBreaker;
import static bisq.relaytest.utils.CircuitBreakerUtil.openCircuitBreaker;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest(classes = {
        ResilienceTestConfig.class,
        FcmPushNotificationSenderResilienceIT.FcmTestConfig.class
})
@ActiveProfiles("integrationtest")
class FcmPushNotificationSenderResilienceIT {

    private static final String DEVICE_TOKEN = "fcm-device-token";

    @Autowired
    private FcmPushNotificationSender sender;

    @Autowired
    private FirebaseMessaging firebaseMessaging;

    @Autowired
    private FcmPushNotificationBuilder builder;

    @Autowired
    private CircuitBreakerRegistry cbRegistry;

    @BeforeEach
    void setup() {
        cbRegistry.circuitBreaker(PROVIDER_ID_FCM).transitionToClosedState();
        reset(firebaseMessaging);

        when(builder.buildMessage(any(), anyString(), anyString()))
                .thenReturn(mock(Message.class));
    }

    /**
     * Verifies OPEN breaker fully short-circuits the call and never invokes the outbound FCM client.
     */
    @Test
    void whenBreakerIsOpen_thenCallIsShortCircuited_andNoOutboundCallIsMade() {
        CircuitBreaker cb = cbRegistry.circuitBreaker(PROVIDER_ID_FCM);
        openCircuitBreaker(cb);

        PushNotificationResult result = sender.sendNotification(
                        new PushNotificationMessage(
                                "encrypted", false, false), DEVICE_TOKEN)
                .join();

        assertThat(result.wasAccepted()).isFalse();
        assertThat(result.errorCode()).isEqualTo("ServiceUnavailable");

        verify(firebaseMessaging, never()).sendAsync(any(Message.class));
    }

    /**
     * Verifies HALF_OPEN permit exhaustion prevents additional calls from invoking the outbound FCM client.
     */
    @Test
    void whenHalfOpenPermitIsExhausted_thenCallIsShortCircuited_andNoOutboundCallIsMade() {
        CircuitBreaker cb = cbRegistry.circuitBreaker(PROVIDER_ID_FCM);
        halfOpenCircuitBreaker(cb);

        // Intentionally consume the permitted number of calls in HALF_OPEN state
        // so the next call shall be rejected.
        // This assumes permittedNumberOfCallsInHalfOpenState=2
        for (int i = 0; i < 2; i++) {
            assertThat(cb.tryAcquirePermission()).isTrue();
        }

        PushNotificationResult result = sender
                .sendNotification(new PushNotificationMessage(
                                "encrypted", false, false),
                        DEVICE_TOKEN)
                .join();

        assertThat(result.wasAccepted()).isFalse();
        assertThat(result.errorCode()).isEqualTo("ServiceUnavailable");

        verify(firebaseMessaging, never()).sendAsync(any());
    }

    /**
     * Verifies that a failed probe call while the circuit breaker is in HALF_OPEN state
     * causes the breaker to transition back to OPEN.
     */
    @Test
    void whenHalfOpenProbeFails_thenBreakerTransitionsBackToOpen() {
        CircuitBreaker cb = cbRegistry.circuitBreaker(PROVIDER_ID_FCM);
        halfOpenCircuitBreaker(cb);

        // Fail the probe with a transport error (breaker-relevant)
        givenFcmWillFailWith(new IOException("timeout"));

        // This assumes permittedNumberOfCallsInHalfOpenState=2
        for (int i = 0; i < 2; i++) {
            sender.sendNotification(
                            new PushNotificationMessage(
                                    "encrypted", false, false),
                            DEVICE_TOKEN)
                    .join();
        }

        assertThat(cb.getState()).isEqualTo(State.OPEN);
    }

    /**
     * Verifies that after OPEN wait duration elapses, the next call is permitted as a probe and can close the breaker.
     */
    @Test
    void whenOpenWaitDurationElapsed_thenNextCallIsPermitted_andBreakerCanClose() {
        CircuitBreaker cb = cbRegistry.circuitBreaker(PROVIDER_ID_FCM);
        openCircuitBreaker(cb);

        // Wait for the OPEN wait duration to elapse
        // This assumes waitDurationInOpenState <= 3s (in test properties)
        Awaitility.await().pollDelay(Duration.ofSeconds(3)).until(() -> true);

        // The next call should be permitted as a probe; make it succeed so the breaker can close
        givenFcmWillAccept();

        PushNotificationResult result = sender
                .sendNotification(new PushNotificationMessage(
                                "encrypted", false, false),
                        DEVICE_TOKEN)
                .join();

        assertThat(result.wasAccepted()).isTrue();
        assertThat(cb.getState()).isEqualTo(State.HALF_OPEN);
        verify(firebaseMessaging, times(1)).sendAsync(any(Message.class));
    }

    /**
     * Verifies repeated transport errors (breaker-relevant) open the breaker, and subsequent calls are short-circuited.
     */
    @Test
    void whenSufficientTransportErrors_thenBreakerOpens_andFurtherCallsAreShortCircuited() {
        CircuitBreaker cb = cbRegistry.circuitBreaker(PROVIDER_ID_FCM);
        assertThat(cb.getState()).isEqualTo(State.CLOSED);

        // First: make enough failing calls to OPEN the circuit breaker
        // Use a transport failure (e.g., IOException)
        givenFcmWillFailWith(new IOException("timeout"));

        // This assumes minimumNumberOfCalls=5
        for (int i = 0; i < 5; i++) {
            sender.sendNotification(
                            new PushNotificationMessage(
                                    "encrypted", false, false),
                            DEVICE_TOKEN)
                    .join();
        }

        assertThat(cb.getState()).isIn(State.OPEN, State.HALF_OPEN); // depending on timing

        reset(firebaseMessaging);

        // Second: with CB OPEN, the next call should go straight to fall back
        PushNotificationResult fallbackResult = sender
                .sendNotification(new PushNotificationMessage(
                                "encrypted", false, false),
                        DEVICE_TOKEN)
                .join();
        assertThat(fallbackResult.wasAccepted()).isFalse();
        assertThat(fallbackResult.errorCode()).isEqualTo("ServiceUnavailable");
        verify(firebaseMessaging, never()).sendAsync(any(Message.class));
    }

    @Test
    void whenPushIsAccepted_thenCbStaysClosed() {
        CircuitBreaker cb = cbRegistry.circuitBreaker(PROVIDER_ID_FCM);
        assertThat(cb.getState()).isEqualTo(State.CLOSED);

        givenFcmWillAccept();

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
        CircuitBreaker cb = cbRegistry.circuitBreaker(PROVIDER_ID_FCM);
        halfOpenCircuitBreaker(cb);
        assertThat(cb.getState()).isEqualTo(State.HALF_OPEN);

        givenFcmWillAccept();

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
        verify(firebaseMessaging, times(2)).sendAsync(any(Message.class));
    }

    @Test
    void whenPushIsRejectedWithClientError_thenCbDoesNotOpen() {
        CircuitBreaker cb = cbRegistry.circuitBreaker(PROVIDER_ID_FCM);
        assertThat(cb.getState()).isEqualTo(State.CLOSED);

        // INVALID_ARGUMENT is a client-side error (400) which shouldn't trip the breaker
        givenFcmWillReject(MessagingErrorCode.INVALID_ARGUMENT);

        // This assumes minimumNumberOfCalls=5
        for (int i = 0; i < 5; i++) {
            PushNotificationResult result = sender
                    .sendNotification(new PushNotificationMessage(
                                    "encrypted", false, false),
                            DEVICE_TOKEN)
                    .join();

            assertThat(result.wasAccepted()).isFalse();
            assertThat(result.errorCode()).isEqualTo("INVALID_ARGUMENT");
        }

        assertThat(cb.getState()).isEqualTo(State.CLOSED);
    }

    @Test
    void whenSufficientThrottleResponses_thenCbOpens_andFallbackReturnsServiceUnavailable() {
        CircuitBreaker cb = cbRegistry.circuitBreaker(PROVIDER_ID_FCM);
        assertThat(cb.getState()).isEqualTo(State.CLOSED);

        givenFcmWillReject(MessagingErrorCode.QUOTA_EXCEEDED);

        // This assumes minimumNumberOfCalls=5
        for (int i = 0; i < 5; i++) {
            sender.sendNotification(
                            new PushNotificationMessage(
                                    "encrypted", false, false),
                            DEVICE_TOKEN)
                    .join();
        }

        assertThat(cb.getState()).isIn(State.OPEN, State.HALF_OPEN);

        PushNotificationResult fallback = sender
                .sendNotification(new PushNotificationMessage(
                                "encrypted", false, false),
                        DEVICE_TOKEN)
                .join();

        assertThat(fallback.wasAccepted()).isFalse();
        assertThat(fallback.errorCode()).isEqualTo("ServiceUnavailable");
    }

    @Test
    void whenSufficientProviderServerErrors_thenCbOpens_andFallbackReturnsServiceUnavailable() {
        CircuitBreaker cb = cbRegistry.circuitBreaker(PROVIDER_ID_FCM);
        assertThat(cb.getState()).isEqualTo(State.CLOSED);

        givenFcmWillReject(MessagingErrorCode.UNAVAILABLE);

        // This assumes minimumNumberOfCalls=5
        for (int i = 0; i < 5; i++) {
            sender.sendNotification(
                            new PushNotificationMessage(
                                    "encrypted", false, false),
                            DEVICE_TOKEN)
                    .join();
        }

        assertThat(cb.getState()).isIn(State.OPEN, State.HALF_OPEN);

        PushNotificationResult fallback = sender
                .sendNotification(new PushNotificationMessage(
                                "encrypted", false, false),
                        DEVICE_TOKEN)
                .join();

        assertThat(fallback.wasAccepted()).isFalse();
        assertThat(fallback.errorCode()).isEqualTo("ServiceUnavailable");
    }

    // ---------- Helpers to simulate FCM responses ----------

    private void givenFcmWillAccept() {
        SettableApiFuture<String> future = SettableApiFuture.create();
        future.set("projects/demo/messages/123");
        when(firebaseMessaging.sendAsync(any())).thenReturn(future);
    }

    private void givenFcmWillFailWith(final Exception e) {
        SettableApiFuture<String> future = SettableApiFuture.create();
        future.setException(e);
        when(firebaseMessaging.sendAsync(any())).thenReturn(future);
    }

    private void givenFcmWillReject(final MessagingErrorCode code) {
        FirebaseMessagingException ex = mock(FirebaseMessagingException.class);
        when(ex.getMessagingErrorCode()).thenReturn(code);
        givenFcmWillFailWith(ex);
    }

    /**
     * Spring test configuration: wires the FCM sender with mocks.
     */
    @Configuration
    static class FcmTestConfig {

        @Bean
        FcmPushNotificationSender fcmPushNotificationSender(
                FirebaseMessaging firebaseMessaging,
                FcmPushNotificationBuilder builder,
                ResilienceAsyncExecutor executor) {
            return new FcmPushNotificationSender(firebaseMessaging, builder, executor);
        }

        @Bean
        @Primary
        FcmPushNotificationBuilder fcmPushNotificationBuilder() {
            return mock(FcmPushNotificationBuilder.class);
        }

        @Bean
        @Primary
        FirebaseMessaging firebaseMessaging() {
            return mock(FirebaseMessaging.class);
        }
    }
}
