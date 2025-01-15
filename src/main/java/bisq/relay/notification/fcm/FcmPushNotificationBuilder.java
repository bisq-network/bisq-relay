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
import jakarta.annotation.Nonnull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Objects;

@Component
public class FcmPushNotificationBuilder {
    private static final Logger LOG = LoggerFactory.getLogger(FcmPushNotificationBuilder.class);
    // The maximum time-to-live duration of an Android message is 4 weeks
    public static final long TTL_DAYS = 28;

    public Message buildMessage(
            @Nonnull final PushNotificationMessage pushNotificationMessage,
            @Nonnull final String deviceToken) {
        Objects.requireNonNull(deviceToken);
        Objects.requireNonNull(pushNotificationMessage);

        Message.Builder messageBuilder = getMessageBuilder(pushNotificationMessage)
                .setToken(deviceToken);

        if (pushNotificationMessage.encrypted() != null) {
            messageBuilder.putData("encrypted", pushNotificationMessage.encrypted());
        } else {
            LOG.warn("PushNotificationMessage is missing encrypted content: {}", pushNotificationMessage);
        }

        return messageBuilder.build();
    }

    private Message.Builder getMessageBuilder(@Nonnull final PushNotificationMessage pushNotificationMessage) {
        Objects.requireNonNull(pushNotificationMessage);

        AndroidConfig androidConfig = getAndroidConfig(pushNotificationMessage);

        return Message.builder()
                .setAndroidConfig(androidConfig);
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
