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
import com.eatthepath.pushy.apns.*;
import com.eatthepath.pushy.apns.util.SimpleApnsPushNotification;
import com.eatthepath.pushy.apns.util.concurrent.PushNotificationFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.stubbing.Answer;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CompletionException;

import static java.time.temporal.ChronoUnit.DAYS;
import static java.time.temporal.ChronoUnit.SECONDS;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("test")
class ApnsPushNotificationSenderTest {
    private static final String DEVICE_TOKEN =
            "d45161df3d172837f1b83bb3e411d5a63120de6b435ff9235adb70d619d162a1";
    private static final String APNS_BUNDLE_ID = "bisqremote.joachimneumann.com";

    private ApnsClient apnsClient;
    private ApnsPushNotificationSender apnsSender;

    private PushNotificationResult pushNotificationResult;
    private SimpleApnsPushNotification sentPushNotification;

    @BeforeEach
    void setup() {
        apnsClient = mock(ApnsClient.class);
        apnsSender = new ApnsPushNotificationSender(apnsClient, APNS_BUNDLE_ID, new ApnsPushNotificationBuilder());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void whenPushNotificationIsAcceptedByApns_thenSuccessfulResultReturned(final boolean urgent) {
        givenApnsWillAcceptPushNotifications();
        whenSendingAPushNotification(urgent);
        thenTheSentPushNotificationIsCorrectlyPopulated(urgent);
        thenThePushNotificationWasAccepted();

        verifyNoMoreInteractions(apnsClient);
    }

    @ParameterizedTest
    @CsvSource({"Unregistered,true,true", "BadDeviceToken,true,true", "BadTopic,false,false"})
    void whenPushNotificationIsRejectedByApns_thenErrorResultReturned(
            final String rejectionReason, final boolean isUnregistered, final boolean hasTokenInvalidationTimestamp) {
        givenApnsWillRejectPushNotifications(rejectionReason);
        whenSendingAPushNotification(true);
        thenTheSentPushNotificationIsCorrectlyPopulated(true);
        thenThePushNotificationWasNotAccepted(rejectionReason, hasTokenInvalidationTimestamp, isUnregistered);

        verifyNoMoreInteractions(apnsClient);
    }

    @Test
    void whenFailedToSendNotificationToApns_thenExceptionRaised() {
        givenApnsIsUnreachable(new IOException("lost connection"));

        PushNotificationMessage pushNotification = new PushNotificationMessage("foo", true);
        Throwable thrown = catchThrowable(() -> apnsSender.sendNotification(pushNotification, DEVICE_TOKEN).join());
        assertThat(thrown).isInstanceOf(CompletionException.class).hasCauseInstanceOf(IOException.class);

        verify(apnsClient).sendNotification(isA(SimpleApnsPushNotification.class));

        verifyNoMoreInteractions(apnsClient);
    }

    private void givenApnsWillAcceptPushNotifications() {
        @SuppressWarnings("unchecked")
        PushNotificationResponse<SimpleApnsPushNotification> response = mock(PushNotificationResponse.class);
        when(response.isAccepted()).thenReturn(true);

        when(apnsClient.sendNotification(isA(SimpleApnsPushNotification.class)))
                .thenAnswer(
                        (Answer<MockPushNotificationFuture<SimpleApnsPushNotification,
                                PushNotificationResponse<SimpleApnsPushNotification>>>) invocationOnMock ->
                                new MockPushNotificationFuture<>(invocationOnMock.getArgument(0), response));
    }

    private void givenApnsWillRejectPushNotifications(final String rejectionReason) {
        @SuppressWarnings("unchecked")
        PushNotificationResponse<SimpleApnsPushNotification> response = mock(PushNotificationResponse.class);
        when(response.isAccepted()).thenReturn(false);
        when(response.getRejectionReason()).thenReturn(Optional.of(rejectionReason));
        if ("Unregistered".equals(rejectionReason) || "BadDeviceToken".equals(rejectionReason)) {
            when(response.getTokenInvalidationTimestamp()).thenReturn(Optional.of(Instant.now()));
        }

        when(apnsClient.sendNotification(isA(SimpleApnsPushNotification.class)))
                .thenAnswer(
                        (Answer<MockPushNotificationFuture<SimpleApnsPushNotification,
                                PushNotificationResponse<SimpleApnsPushNotification>>>) invocationOnMock ->
                                new MockPushNotificationFuture<>(invocationOnMock.getArgument(0), response));
    }

    private void givenApnsIsUnreachable(final Exception exception) {
        @SuppressWarnings("unchecked")
        PushNotificationResponse<SimpleApnsPushNotification> response = mock(PushNotificationResponse.class);
        when(response.isAccepted()).thenReturn(true);

        when(apnsClient.sendNotification(isA(SimpleApnsPushNotification.class)))
                .thenAnswer(
                        (Answer<MockPushNotificationFuture<SimpleApnsPushNotification,
                                PushNotificationResponse<SimpleApnsPushNotification>>>) invocationOnMock ->
                                new MockPushNotificationFuture<>(invocationOnMock.getArgument(0), exception));
    }

    private void whenSendingAPushNotification(final boolean urgent) {
        PushNotificationMessage pushNotificationMessage = new PushNotificationMessage("foo", urgent);
        pushNotificationResult = apnsSender.sendNotification(pushNotificationMessage, DEVICE_TOKEN).join();

        ArgumentCaptor<SimpleApnsPushNotification> notification = ArgumentCaptor.forClass(SimpleApnsPushNotification.class);
        verify(apnsClient).sendNotification(notification.capture());
        sentPushNotification = notification.getValue();
    }

    private void thenTheSentPushNotificationIsCorrectlyPopulated(final boolean urgent) {
        assertThat(sentPushNotification.getToken()).isEqualTo(DEVICE_TOKEN);
        assertThat(sentPushNotification.getExpiration()).isCloseTo(
                Instant.now().plus(ApnsPushNotificationBuilder.INVALIDATION_TIME_PERIOD_DAYS, DAYS), within(10, SECONDS));
        assertThat(sentPushNotification.getPayload()).isEqualTo(
                "{\"encrypted\":\"foo\",\"aps\":{\"alert\":{\"loc-key\":\"notification\"},\"content-available\":1}}");

        assertThat(sentPushNotification.getPriority())
                .isEqualTo(urgent ? DeliveryPriority.IMMEDIATE : DeliveryPriority.CONSERVE_POWER);

        assertThat(sentPushNotification.getTopic()).isEqualTo(APNS_BUNDLE_ID);
        assertThat(sentPushNotification.getPushType())
                .isEqualTo(urgent ? PushType.ALERT : PushType.BACKGROUND);
        if (urgent) {
            assertThat(sentPushNotification.getCollapseId()).isNotNull();
        } else {
            assertThat(sentPushNotification.getCollapseId()).isNull();
        }
    }

    private void thenThePushNotificationWasAccepted() {
        assertThat(pushNotificationResult.wasAccepted()).isTrue();
        assertThat(pushNotificationResult.errorCode()).isNull();
        assertThat(pushNotificationResult.errorMessage()).isNull();
        assertThat(pushNotificationResult.isUnregistered()).isFalse();
    }

    private void thenThePushNotificationWasNotAccepted(
            final String expectedErrorCode, final boolean hasTokenInvalidationTimestamp, final boolean isUnregistered) {
        assertThat(pushNotificationResult.wasAccepted()).isFalse();
        assertThat(pushNotificationResult.errorCode()).isEqualTo(expectedErrorCode);
        if (hasTokenInvalidationTimestamp) {
            assertThat(pushNotificationResult.errorMessage()).containsPattern(
                    "Token is invalid as of 20\\d\\d-\\d\\d-\\d\\dT\\d\\d:\\d\\d:\\d\\d\\.\\d+Z");
        } else {
            assertThat(pushNotificationResult.errorMessage()).isNull();
        }
        assertThat(pushNotificationResult.isUnregistered()).isEqualTo(isUnregistered);
    }

    private static class MockPushNotificationFuture<P extends ApnsPushNotification, V> extends
            PushNotificationFuture<P, V> {

        MockPushNotificationFuture(final P pushNotification, final V response) {
            super(pushNotification);
            complete(response);
        }

        MockPushNotificationFuture(final P pushNotification, final Exception exception) {
            super(pushNotification);
            completeExceptionally(exception);
        }
    }
}
