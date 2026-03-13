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

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration properties for Apple Push Notification service (APNs).
 * <p>
 * These properties can be configured via environment variables:
 * <ul>
 *   <li>{@code BISQ_RELAY_APNS_BUNDLE_ID} - The iOS app bundle identifier (required)</li>
 *   <li>{@code BISQ_RELAY_APNS_CERTIFICATE_FILE} - Path to the .p12 certificate file</li>
 *   <li>{@code BISQ_RELAY_APNS_CERTIFICATE_PASSWORD_FILE} - Path to file containing certificate password</li>
 *   <li>{@code BISQ_RELAY_APNS_USE_SANDBOX} - Whether to use APNs sandbox (default: true)</li>
 * </ul>
 */
@Validated
@ConfigurationProperties(prefix = "apns")
public class ApnsProperties {

    /**
     * The iOS app bundle identifier (e.g., "bisq.mobile.client.BisqConnect").
     * This must match the bundle ID configured in the Apple Developer portal.
     */
    @NotBlank(message = "APNs bundle ID must be configured. Set BISQ_RELAY_APNS_BUNDLE_ID environment variable.")
    private String bundleId;

    /**
     * Path to the APNs .p12 certificate file.
     */
    @NotBlank(message = "APNs certificate file must be configured. Set BISQ_RELAY_APNS_CERTIFICATE_FILE environment variable.")
    private String certificateFile;

    /**
     * Path to the file containing the APNs certificate password.
     */
    @NotBlank(message = "APNs certificate password file must be configured. Set BISQ_RELAY_APNS_CERTIFICATE_PASSWORD_FILE environment variable.")
    private String certificatePasswordFile;

    /**
     * Whether to use the APNs sandbox (development) environment.
     * Default is {@code true} for safety - production deployments should explicitly set to {@code false}.
     */
    private boolean useSandbox = true;

    public String getBundleId() {
        return bundleId;
    }

    public void setBundleId(String bundleId) {
        this.bundleId = bundleId;
    }

    public String getCertificateFile() {
        return certificateFile;
    }

    public void setCertificateFile(String certificateFile) {
        this.certificateFile = certificateFile;
    }

    public String getCertificatePasswordFile() {
        return certificatePasswordFile;
    }

    public void setCertificatePasswordFile(String certificatePasswordFile) {
        this.certificatePasswordFile = certificatePasswordFile;
    }

    public boolean isUseSandbox() {
        return useSandbox;
    }

    public void setUseSandbox(boolean useSandbox) {
        this.useSandbox = useSandbox;
    }
}

