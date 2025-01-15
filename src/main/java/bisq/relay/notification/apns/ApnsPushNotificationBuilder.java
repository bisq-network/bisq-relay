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
import com.eatthepath.pushy.apns.DeliveryPriority;
import com.eatthepath.pushy.apns.PushType;
import com.eatthepath.pushy.apns.util.ApnsPayloadBuilder;
import com.eatthepath.pushy.apns.util.SimpleApnsPayloadBuilder;
import com.eatthepath.pushy.apns.util.SimpleApnsPushNotification;
import com.eatthepath.pushy.apns.util.TokenUtil;
import jakarta.annotation.Nonnull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Objects;

import static java.time.temporal.ChronoUnit.DAYS;

@Component
public class ApnsPushNotificationBuilder {
    private static final Logger LOG = LoggerFactory.getLogger(ApnsPushNotificationBuilder.class);
    // Use the equivalent maximum time-to-live duration of an Android message (4 weeks)
    public static final long INVALIDATION_TIME_PERIOD_DAYS = 28;

    public SimpleApnsPushNotification buildPushNotification(
            @Nonnull final PushNotificationMessage pushNotificationMessage,
            @Nonnull final String deviceToken,
            @Nonnull final String topic) {
        Objects.requireNonNull(pushNotificationMessage);
        Objects.requireNonNull(deviceToken);
        Objects.requireNonNull(topic);

        final PushType pushType = pushNotificationMessage.isUrgent() ? PushType.ALERT : PushType.BACKGROUND;
        final DeliveryPriority deliveryPriority =
                pushNotificationMessage.isUrgent() ? DeliveryPriority.IMMEDIATE : DeliveryPriority.CONSERVE_POWER;
        final String collapseId = pushNotificationMessage.isUrgent() ? "notification" : null;
        final Instant invalidationTime = Instant.now().plus(INVALIDATION_TIME_PERIOD_DAYS, DAYS);

        return new SimpleApnsPushNotification(
                TokenUtil.sanitizeTokenString(deviceToken),
                topic,
                buildPayload(pushNotificationMessage),
                invalidationTime,
                deliveryPriority,
                pushType,
                collapseId);
    }

    private String buildPayload(@Nonnull final PushNotificationMessage pushNotificationMessage) {
        Objects.requireNonNull(pushNotificationMessage);

        ApnsPayloadBuilder payloadBuilder = new SimpleApnsPayloadBuilder()
                .setLocalizedAlertMessage("notification")
                .setContentAvailable(true);

        if (pushNotificationMessage.encrypted() != null) {
            payloadBuilder.addCustomProperty("encrypted", pushNotificationMessage.encrypted());
        } else {
            LOG.warn("PushNotificationMessage is missing encrypted content: {}", pushNotificationMessage);
        }

        return payloadBuilder.build();
    }
}
