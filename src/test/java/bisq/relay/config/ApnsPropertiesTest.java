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
 * Unit tests for ApnsProperties validation.
 */
class ApnsPropertiesTest {

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            validator = factory.getValidator();
        }
    }

    @Test
    void whenAllPropertiesAreValid_thenNoViolations() {
        ApnsProperties properties = createValidProperties();

        Set<ConstraintViolation<ApnsProperties>> violations = validator.validate(properties);

        assertThat(violations).isEmpty();
    }

    @Test
    void whenUseSandboxDefaultsToTrue_thenSaferDefault() {
        ApnsProperties properties = new ApnsProperties();

        assertThat(properties.isUseSandbox()).isTrue();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t", "\n"})
    void whenBundleIdIsBlank_thenViolation(String bundleId) {
        ApnsProperties properties = createValidProperties();
        properties.setBundleId(bundleId);

        Set<ConstraintViolation<ApnsProperties>> violations = validator.validate(properties);

        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getPropertyPath().toString()).isEqualTo("bundleId");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t", "\n"})
    void whenCertificateFileIsBlank_thenViolation(String certificateFile) {
        ApnsProperties properties = createValidProperties();
        properties.setCertificateFile(certificateFile);

        Set<ConstraintViolation<ApnsProperties>> violations = validator.validate(properties);

        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getPropertyPath().toString()).isEqualTo("certificateFile");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t", "\n"})
    void whenCertificatePasswordFileIsBlank_thenViolation(String certificatePasswordFile) {
        ApnsProperties properties = createValidProperties();
        properties.setCertificatePasswordFile(certificatePasswordFile);

        Set<ConstraintViolation<ApnsProperties>> violations = validator.validate(properties);

        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getPropertyPath().toString()).isEqualTo("certificatePasswordFile");
    }

    @Test
    void whenMultiplePropertiesAreInvalid_thenMultipleViolations() {
        ApnsProperties properties = new ApnsProperties();
        // All required properties are null/blank

        Set<ConstraintViolation<ApnsProperties>> violations = validator.validate(properties);

        assertThat(violations).hasSize(3);
        assertThat(violations)
                .extracting(v -> v.getPropertyPath().toString())
                .containsExactlyInAnyOrder("bundleId", "certificateFile", "certificatePasswordFile");
    }

    private ApnsProperties createValidProperties() {
        ApnsProperties properties = new ApnsProperties();
        properties.setBundleId("com.example.app");
        properties.setCertificateFile("certificate.p12");
        properties.setCertificatePasswordFile("password.txt");
        properties.setUseSandbox(true);
        return properties;
    }
}

