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

package bisq.relay.notification;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.annotation.Nonnull;
import jakarta.validation.constraints.NotBlank;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PushNotificationMessage(
        @NotBlank String encrypted,
        boolean isUrgent,
        boolean isMutableContent) {

    /**
     * Computes a deterministic coalescing identifier for this message.
     * <p>
     * <strong>Intent:</strong> reduce user-visible duplicates caused by retries of the <em>same logical</em>
     * push notification. If the relay retries sending the same message (same {@code encrypted} payload and
     * {@code isUrgent} flag), this method returns the same identifier so push providers can coalesce multiple
     * in-flight/queued notifications.
     * <p>
     * <strong>Provider semantics:</strong> many push providers support a "collapse"/"coalescing" key concept
     * (e.g., collapse identifiers / collapse keys). These are typically hints that allow a newer message to
     * supersede an older one with the same key; they are not strict exactly-once delivery guarantees.
     * <p>
     * <strong>Stability:</strong> the identifier is derived from an explicit canonical representation.
     * <p>
     * <strong>Size:</strong> the returned value is a Base64 URL-encoded SHA-256 digest (43 characters),
     * suitable for providers that impose modest size limits on their coalescing key fields.
     *
     * @return a deterministic coalescing identifier for this message
     */
    @Nonnull
    public String coalescingKey() {
        final String canonical = isUrgent + "|" + encrypted;

        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8));

            // SHA-256 base64url without padding = 43 chars
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException e) {
            // Extremely unlikely on a standard JRE
            throw new IllegalStateException("SHA-256 MessageDigest not available", e);
        }
    }

    @Override
    @Nonnull
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
                .append("encrypted", encrypted)
                .append("isUrgent", isUrgent)
                .append("isMutableContent", isMutableContent)
                .toString();
    }
}
