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
import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import org.springframework.stereotype.Component;

import javax.annotation.Nonnull;
import java.time.Duration;
import java.util.Objects;

@Component
public class FcmPushNotificationBuilder {
    // The maximum time-to-live duration of an Android message is 4 weeks
    public static final long TTL_DAYS = 28;

    public Message buildMessage(
            @Nonnull final PushNotificationMessage pushNotificationMessage,
            @Nonnull final String deviceToken) {
        Objects.requireNonNull(deviceToken);
        Objects.requireNonNull(pushNotificationMessage);

        return getMessageBuilder(pushNotificationMessage)
                .setToken(deviceToken)
                .putData("encrypted", pushNotificationMessage.encrypted())
                .build();
    }

    private Message.Builder getMessageBuilder(@Nonnull final PushNotificationMessage pushNotificationMessage) {
        Objects.requireNonNull(pushNotificationMessage);

        AndroidConfig androidConfig = getAndroidConfig(pushNotificationMessage);

        // TODO send data-only messages (i.e. remove setNotification).
        //  This will allow the app to process/decrypt background messages as they are received,
        //  rather than only when clicked on.
        //  Wait until an updated version of the app has been released that supports data-only messages
        //  and installed by a majority of users.
        //  Ref: https://firebase.google.com/docs/cloud-messaging/android/receive
        return Message.builder()
                .setAndroidConfig(androidConfig)
                .setNotification(Notification.builder()
                        .setTitle("You have received a Bisq notification")
                        .setBody("Click to decrypt")
                        .build());
    }

    private AndroidConfig getAndroidConfig(@Nonnull final PushNotificationMessage pushNotificationMessage) {
        Objects.requireNonNull(pushNotificationMessage);
        return AndroidConfig.builder()
                .setTtl(Duration.ofDays(TTL_DAYS).toMillis())
                .setPriority(pushNotificationMessage.isUrgent() ?
                        AndroidConfig.Priority.HIGH : AndroidConfig.Priority.NORMAL)
                .build();
    }
}
