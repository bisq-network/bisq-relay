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

import jakarta.annotation.Nonnull;

import java.util.Map;
import java.util.Objects;

public final class MessageUtil {
    private MessageUtil() {
    }

    public static String getMessageToken(@Nonnull final Message message) {
        Objects.requireNonNull(message, "message must not be null");
        return message.getToken();
    }

    public static Map<String, String> getMessageData(@Nonnull final Message message) {
        Objects.requireNonNull(message, "message must not be null");
        return message.getData();
    }

    public static Notification getMessageNotification(@Nonnull final Message message) {
        Objects.requireNonNull(message, "message must not be null");
        return message.getNotification();
    }

    public static AndroidConfig getMessageAndroidConfig(@Nonnull final Message message) {
        Objects.requireNonNull(message, "message must not be null");
        return message.getAndroidConfig();
    }

    public static FcmOptions getMessageFcmOptions(@Nonnull final Message message) {
        Objects.requireNonNull(message, "message must not be null");
        return message.getFcmOptions();
    }
}
