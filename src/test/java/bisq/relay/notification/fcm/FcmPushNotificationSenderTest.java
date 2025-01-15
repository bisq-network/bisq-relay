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
import com.google.api.core.SettableApiFuture;
import com.google.firebase.messaging.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("test")
class FcmPushNotificationSenderTest {
    private static final String DEVICE_TOKEN =
            "d4HedtovQCyRdgPsxM0JbA:APA91bFJIwRdBpO4SQpeSuA5rpEnu5N3Y3_c1T5x69gpedyKwGLUrApT6xkwIq8LZVPCy" +
                    "KVi1nh5NdG37TN2nGhpqchOUCysHweuL8V023WJYVwGgpUvdkk5mkYD9D3_QFj2c7f_2ul6";

    private FirebaseMessaging firebaseMessaging;
    private FcmPushNotificationSender fcmSender;

    private PushNotificationResult pushNotificationResult;
    private Message sentPushNotification;

    @BeforeEach
    void setup() {
        firebaseMessaging = mock(FirebaseMessaging.class);
        fcmSender = new FcmPushNotificationSender(firebaseMessaging, new FcmPushNotificationBuilder());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void whenPushNotificationIsAcceptedByFcm_thenSuccessfulResultReturned(final boolean urgent)
            throws IllegalAccessException, NoSuchFieldException {

        givenFcmWillAcceptPushNotifications();
        whenSendingAPushNotification(urgent);
        thenTheSentPushNotificationIsCorrectlyPopulated(urgent);
        thenThePushNotificationWasAccepted();

        verifyNoMoreInteractions(firebaseMessaging);
    }

    @ParameterizedTest
    @CsvSource({"INVALID_ARGUMENT,false", "UNREGISTERED,true"})
    void whenPushNotificationIsRejectedByFcm_thenErrorResultReturned(
            final String rejectionReason, final boolean isUnregistered)
            throws NoSuchFieldException, IllegalAccessException {
        givenFcmWillRejectPushNotifications(rejectionReason);
        whenSendingAPushNotification(true);
        thenTheSentPushNotificationIsCorrectlyPopulated(true);
        thenThePushNotificationWasNotAccepted(rejectionReason, isUnregistered);

        verifyNoMoreInteractions(firebaseMessaging);
    }

    @Test
    void whenFailedToSendNotificationToFcm_thenExceptionRaised() {
        givenFcmIsUnreachable(new IOException("Lost connection"));

        PushNotificationMessage pushNotification = new PushNotificationMessage("foo", true);
        Throwable thrown = catchThrowable(() -> fcmSender.sendNotification(pushNotification, DEVICE_TOKEN).join());
        assertThat(thrown).isInstanceOf(CompletionException.class);

        verify(firebaseMessaging).sendAsync(any(Message.class));
        assertTrue(thrown.getCause() instanceof IOException);

        verifyNoMoreInteractions(firebaseMessaging);
    }

    private void givenFcmWillAcceptPushNotifications() {
        SettableApiFuture<String> apiFuture = SettableApiFuture.create();
        apiFuture.set("messageId");

        when(firebaseMessaging.sendAsync(isA(Message.class))).thenReturn(apiFuture);
    }

    private void givenFcmWillRejectPushNotifications(final String messagingErrorCode) {
        FirebaseMessagingException invalidArgumentException = mock(FirebaseMessagingException.class);
        when(invalidArgumentException.getMessagingErrorCode()).thenReturn(MessagingErrorCode.valueOf(messagingErrorCode));

        SettableApiFuture<String> apiFuture = SettableApiFuture.create();
        apiFuture.setException(invalidArgumentException);

        when(firebaseMessaging.sendAsync(isA(Message.class))).thenReturn(apiFuture);
    }

    private void givenFcmIsUnreachable(final Exception exception) {
        SettableApiFuture<String> apiFuture = SettableApiFuture.create();
        apiFuture.setException(exception);

        when(firebaseMessaging.sendAsync(isA(Message.class))).thenReturn(apiFuture);
    }

    private void whenSendingAPushNotification(final boolean urgent) {
        PushNotificationMessage pushNotificationMessage = new PushNotificationMessage("foo", urgent);
        pushNotificationResult = fcmSender.sendNotification(pushNotificationMessage, DEVICE_TOKEN).join();

        ArgumentCaptor<Message> message = ArgumentCaptor.forClass(Message.class);
        verify(firebaseMessaging).sendAsync(message.capture());
        sentPushNotification = message.getValue();
    }

    private void thenTheSentPushNotificationIsCorrectlyPopulated(final boolean urgent)
            throws NoSuchFieldException, IllegalAccessException {
        assertThat(MessageUtil.getMessageToken(sentPushNotification)).isEqualTo(DEVICE_TOKEN);
        assertThat(MessageUtil.getMessageData(sentPushNotification)).isEqualTo(Map.of("encrypted", "foo"));

        AndroidConfig androidConfig = MessageUtil.getMessageAndroidConfig(sentPushNotification);
        assertThat(AndroidConfigUtil.getTtl(androidConfig)).isEqualTo(
                String.format("%ss", Duration.ofDays(FcmPushNotificationBuilder.TTL_DAYS).toSeconds()));
        assertThat(AndroidConfigUtil.getPriority(androidConfig)).isEqualTo(
                urgent ? AndroidConfig.Priority.HIGH.name().toLowerCase() : AndroidConfig.Priority.NORMAL.name().toLowerCase());

        Notification notification = MessageUtil.getMessageNotification(sentPushNotification);
        assertThat(notification).isNull();
    }

    private void thenThePushNotificationWasAccepted() {
        assertThat(pushNotificationResult.wasAccepted()).isTrue();
        assertThat(pushNotificationResult.errorCode()).isNull();
        assertThat(pushNotificationResult.errorMessage()).isNull();
        assertThat(pushNotificationResult.isUnregistered()).isFalse();
    }

    private void thenThePushNotificationWasNotAccepted(final String expectedErrorCode, final boolean isUnregistered) {
        assertThat(pushNotificationResult.wasAccepted()).isFalse();
        assertThat(pushNotificationResult.errorCode()).isEqualTo(expectedErrorCode);
        assertThat(pushNotificationResult.errorMessage()).isNull();
        assertThat(pushNotificationResult.isUnregistered()).isEqualTo(isUnregistered);
    }
}
