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

import java.util.Arrays;
import java.util.Optional;

public enum ApnsRejectionReason {
    UNREGISTERED("Unregistered"),
    BAD_DEVICE_TOKEN("BadDeviceToken"),
    TOO_MANY_REQUESTS("TooManyRequests"),
    SERVICE_UNAVAILABLE("ServiceUnavailable"),
    INTERNAL_SERVER_ERROR("InternalServerError"),
    PAYLOAD_TOO_LARGE("PayloadTooLarge");

    private final String code;

    ApnsRejectionReason(final String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static Optional<ApnsRejectionReason> fromCode(final String code) {
        return Arrays.stream(values())
                .filter(reason -> reason.code.equals(code))
                .findFirst();
    }

    @Override
    public String toString() {
        return code;
    }
}
