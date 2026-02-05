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

package bisq.relay.config;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for FcmProperties validation.
 */
class FcmPropertiesTest {

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            validator = factory.getValidator();
        }
    }

    @Test
    void whenAllPropertiesAreValid_thenNoViolations() {
        FcmProperties properties = createValidProperties();

        Set<ConstraintViolation<FcmProperties>> violations = validator.validate(properties);

        assertThat(violations).isEmpty();
    }

    @Test
    void whenEnabledDefaultsToFalse_thenSaferDefault() {
        FcmProperties properties = new FcmProperties();

        assertThat(properties.isEnabled()).isFalse();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t", "\n"})
    void whenFirebaseConfigurationFileIsBlank_thenViolation(String configFile) {
        FcmProperties properties = createValidProperties();
        properties.setFirebaseConfigurationFile(configFile);

        Set<ConstraintViolation<FcmProperties>> violations = validator.validate(properties);

        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getPropertyPath().toString()).isEqualTo("firebaseConfigurationFile");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t", "\n"})
    void whenFirebaseUrlIsBlank_thenViolation(String firebaseUrl) {
        FcmProperties properties = createValidProperties();
        properties.setFirebaseUrl(firebaseUrl);

        Set<ConstraintViolation<FcmProperties>> violations = validator.validate(properties);

        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getPropertyPath().toString()).isEqualTo("firebaseUrl");
    }

    @Test
    void whenMultiplePropertiesAreInvalid_thenMultipleViolations() {
        FcmProperties properties = new FcmProperties();
        // All required properties are null/blank

        Set<ConstraintViolation<FcmProperties>> violations = validator.validate(properties);

        assertThat(violations).hasSize(2);
        assertThat(violations)
                .extracting(v -> v.getPropertyPath().toString())
                .containsExactlyInAnyOrder("firebaseConfigurationFile", "firebaseUrl");
    }

    private FcmProperties createValidProperties() {
        FcmProperties properties = new FcmProperties();
        properties.setEnabled(true);
        properties.setFirebaseConfigurationFile("fcmServiceAccountKey.json");
        properties.setFirebaseUrl("https://example.firebaseio.com");
        return properties;
    }
}

