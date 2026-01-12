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

import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MaskingUtilTest {

    @Test
    void testMaskSensitive_WithDefaultValues() {
        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(MaskingUtil.maskSensitive("1234567890")).isEqualTo("**********");
            softly.assertThat(MaskingUtil.maskSensitive("secretpassword")).isEqualTo("secre****sword");
            softly.assertThat(MaskingUtil.maskSensitive("short")).isEqualTo("*****");
        });
    }

    @Test
    void testMaskSensitive_WithCustomMaskChar() {
        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(MaskingUtil.maskSensitive("1234567890", 2, '#')).isEqualTo("12######90");
            softly.assertThat(MaskingUtil.maskSensitive("secretpassword", 3, '$')).isEqualTo("sec$$$$$$$$ord");
            softly.assertThat(MaskingUtil.maskSensitive("short", 2, '?')).isEqualTo("sh?rt");
        });
    }

    @Test
    void testMaskSensitive_WhenInputIsNullOrEmpty() {
        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(MaskingUtil.maskSensitive(null)).isEqualTo(null);
            softly.assertThat(MaskingUtil.maskSensitive("")).isEqualTo("");
        });
    }

    @Test
    void testMaskSensitive_WhenStringIsTooShortToMask() {
        assertThat(MaskingUtil.maskSensitive("tiny", 2, '#')).isEqualTo("####");
    }

    @Test
    void testMaskSensitive_WhenOnlyFewCharactersAreVisible() {
        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(MaskingUtil.maskSensitive("abcdefxyz", 2)).isEqualTo("ab*****yz");
            softly.assertThat(MaskingUtil.maskSensitive("helloWorld", 3)).isEqualTo("hel****rld");
        });
    }

    @Test
    void testMaskSensitive_WhenVisibleCharactersExceedLength() {
        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(MaskingUtil.maskSensitive("supersecure", 10)).isEqualTo("***********");
            softly.assertThat(MaskingUtil.maskSensitive("data", 5)).isEqualTo("****");
        });
    }
}
