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

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PushNotificationMessageTest {

    @Test
    void coalescingKey_isDeterministic_forSameMessageInstance() {
        PushNotificationMessage msg = new PushNotificationMessage("encrypted-payload", false, false);

        String k1 = msg.coalescingKey();
        String k2 = msg.coalescingKey();

        assertThat(k1).isEqualTo(k2);
    }

    @Test
    void coalescingKey_isDeterministic_forEquivalentMessages() {
        PushNotificationMessage msg1 = new PushNotificationMessage("encrypted-payload", false, false);
        PushNotificationMessage msg2 = new PushNotificationMessage("encrypted-payload", false, false);

        assertThat(msg1.coalescingKey()).isEqualTo(msg2.coalescingKey());
    }

    @Test
    void coalescingKey_changes_whenEncryptedChanges() {
        PushNotificationMessage msg1 = new PushNotificationMessage("encrypted-payload-1", false, false);
        PushNotificationMessage msg2 = new PushNotificationMessage("encrypted-payload-2", false, false);

        assertThat(msg1.coalescingKey()).isNotEqualTo(msg2.coalescingKey());
    }

    @Test
    void coalescingKey_changes_whenUrgencyChanges() {
        PushNotificationMessage msg1 = new PushNotificationMessage("encrypted-payload", false, false);
        PushNotificationMessage msg2 = new PushNotificationMessage("encrypted-payload", true, false);

        assertThat(msg1.coalescingKey()).isNotEqualTo(msg2.coalescingKey());
    }

    @Test
    void coalescingKey_isBase64Url_withoutPadding_andHasExpectedLength() {
        // For SHA-256 digest, base64 url without padding should always be 43 chars
        PushNotificationMessage msg = new PushNotificationMessage("encrypted-payload", false, false);

        String key = msg.coalescingKey();

        assertThat(key)
                .hasSize(43)
                .doesNotContain("=")  // no padding
                .doesNotContain("+")  // base64 url uses '-' instead
                .doesNotContain("/")  // base64 url uses '_' instead
                .matches("^[A-Za-z0-9_-]{43}$");
    }
}
