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

package bisq.relay.util;

import jakarta.annotation.Nullable;

public class MaskingUtil {
    private static final int DEFAULT_VISIBLE_CHARACTERS = 5;
    private static final char DEFAULT_MASK_CHARACTER = '*';
    private static final int FIXED_MASK_LENGTH = 4;

    private MaskingUtil() {
        throw new AssertionError("This class must not be instantiated");
    }

    @Nullable
    public static String maskSensitive(@Nullable final String value, int visibleChars, char maskChar) {
        if (value == null || value.isEmpty()) {
            return value;
        }

        int minVisible = visibleChars * 2;
        if (value.length() <= minVisible) {
            return String.valueOf(maskChar).repeat(FIXED_MASK_LENGTH);
        }

        String firstPart = value.substring(0, visibleChars);
        String lastPart = value.substring(value.length() - visibleChars);

        int actualMiddleLength = value.length() - minVisible;
        int maskLength = Math.min(actualMiddleLength, FIXED_MASK_LENGTH);
        String maskedMiddle = String.valueOf(maskChar).repeat(maskLength);

        return firstPart + maskedMiddle + lastPart;
    }

    @Nullable
    public static String maskSensitive(@Nullable final String value) {
        return maskSensitive(value, DEFAULT_VISIBLE_CHARACTERS, DEFAULT_MASK_CHARACTER);
    }

    @Nullable
    public static String maskSensitive(@Nullable final String value, int visibleChars) {
        return maskSensitive(value, visibleChars, DEFAULT_MASK_CHARACTER);
    }
}
