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

import bisq.relay.notification.PushNotificationSender;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Marks a {@link PushNotificationSender} implementation with the provider identifier for metrics tagging
 * and automatic decoration.
 *
 * <p>Example:</p>
 * <pre>{@code
 * @Service
 * @PushProvider(PushMetrics.PROVIDER_FCM)
 * public class FcmPushNotificationSender implements PushNotificationSender {
 *     ...
 * }
 * }</pre>
 * <p>
 * Typical values: {@value PushMetrics#PROVIDER_ID_APNS}, {@value PushMetrics#PROVIDER_ID_FCM}.
 */
@Documented
@Target(TYPE)
@Retention(RUNTIME)
public @interface PushProvider {
    /**
     * The provider name to use for metrics tagging and bean decoration.
     */
    String value();
}
