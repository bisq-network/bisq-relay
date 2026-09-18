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

import com.google.firebase.messaging.MessagingErrorCode;

import java.util.Arrays;
import java.util.Optional;

public enum FcmRejectionReason {

    UNREGISTERED(MessagingErrorCode.UNREGISTERED),
    INVALID_ARGUMENT(MessagingErrorCode.INVALID_ARGUMENT),
    QUOTA_EXCEEDED(MessagingErrorCode.QUOTA_EXCEEDED),
    UNAVAILABLE(MessagingErrorCode.UNAVAILABLE),
    INTERNAL(MessagingErrorCode.INTERNAL),
    SENDER_ID_MISMATCH(MessagingErrorCode.SENDER_ID_MISMATCH);

    private final MessagingErrorCode messagingErrorCode;

    FcmRejectionReason(final MessagingErrorCode messagingErrorCode) {
        this.messagingErrorCode = messagingErrorCode;
    }

    public MessagingErrorCode messagingErrorCode() {
        return messagingErrorCode;
    }

    public String code() {
        return messagingErrorCode.name();
    }

    public static Optional<FcmRejectionReason> fromMessagingErrorCode(final MessagingErrorCode messagingErrorCode) {
        return Arrays.stream(values())
                .filter(reason -> reason.messagingErrorCode == messagingErrorCode)
                .findFirst();
    }

    public static Optional<FcmRejectionReason> fromCode(final String code) {
        return Arrays.stream(values())
                .filter(reason -> reason.code().equals(code))
                .findFirst();
    }

    @Override
    public String toString() {
        return code();
    }
}
