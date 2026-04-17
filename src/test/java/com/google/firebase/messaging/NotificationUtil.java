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

package com.google.firebase.messaging;

import bisq.relay.test.utils.ReflectionUtil;
import jakarta.annotation.Nonnull;

import java.util.Objects;

public final class NotificationUtil {
    private NotificationUtil() {
        throw new AssertionError("This class must not be instantiated");
    }

    public static String getTitle(@Nonnull final Notification notification)
            throws NoSuchFieldException, IllegalAccessException {
        Objects.requireNonNull(notification, "notification must not be null");
        return (String) ReflectionUtil.getPrivateField(
                Notification.class, "title").get(notification);
    }

    public static String getBody(@Nonnull final Notification notification)
            throws NoSuchFieldException, IllegalAccessException {
        Objects.requireNonNull(notification, "notification must not be null");
        return (String) ReflectionUtil.getPrivateField(
                Notification.class, "body").get(notification);
    }

    public static String getImage(@Nonnull final Notification notification)
            throws NoSuchFieldException, IllegalAccessException {
        Objects.requireNonNull(notification, "notification must not be null");
        return (String) ReflectionUtil.getPrivateField(
                Notification.class, "image").get(notification);
    }
}
