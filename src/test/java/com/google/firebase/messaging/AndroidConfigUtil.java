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

import javax.annotation.Nonnull;
import java.util.Objects;

public final class AndroidConfigUtil {
    private AndroidConfigUtil() {
    }

    public static String getTtl(@Nonnull final AndroidConfig androidConfig)
            throws NoSuchFieldException, IllegalAccessException {
        Objects.requireNonNull(androidConfig, "androidConfig must not be null");
        return (String) ReflectionUtil.getPrivateField(
                AndroidConfig.class, "ttl").get(androidConfig);
    }

    public static String getPriority(@Nonnull final AndroidConfig androidConfig)
            throws NoSuchFieldException, IllegalAccessException {
        Objects.requireNonNull(androidConfig, "androidConfig must not be null");
        return (String) ReflectionUtil.getPrivateField(
                AndroidConfig.class, "priority").get(androidConfig);
    }

    public static AndroidNotification getNotification(@Nonnull final AndroidConfig androidConfig)
            throws NoSuchFieldException, IllegalAccessException {
        Objects.requireNonNull(androidConfig, "androidConfig must not be null");
        return (AndroidNotification) ReflectionUtil.getPrivateField(
                AndroidConfig.class, "notification").get(androidConfig);
    }
}
