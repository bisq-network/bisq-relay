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
 * Configuration properties for Firebase Cloud Messaging (FCM).
 * <p>
 * These properties can be configured via environment variables:
 * <ul>
 *   <li>{@code BISQ_RELAY_FCM_ENABLED} - Whether FCM is enabled (default: false)</li>
 *   <li>{@code BISQ_RELAY_FCM_FIREBASE_CONFIGURATION_FILE} - Path to Firebase service account JSON file</li>
 *   <li>{@code BISQ_RELAY_FCM_FIREBASE_URL} - Firebase database URL</li>
 * </ul>
 */
@Validated
@ConfigurationProperties(prefix = "fcm")
public class FcmProperties {

    /**
     * Whether FCM push notifications are enabled.
     * Default is {@code false} - must be explicitly enabled.
     */
    private boolean enabled = false;

    /**
     * Path to the Firebase service account JSON configuration file.
     * Required when FCM is enabled.
     */
    @NotBlank(message = "FCM configuration file must be configured. Set BISQ_RELAY_FCM_FIREBASE_CONFIGURATION_FILE environment variable.")
    private String firebaseConfigurationFile;

    /**
     * Firebase Realtime Database URL.
     * Required when FCM is enabled.
     */
    @NotBlank(message = "FCM Firebase URL must be configured. Set BISQ_RELAY_FCM_FIREBASE_URL environment variable.")
    private String firebaseUrl;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getFirebaseConfigurationFile() {
        return firebaseConfigurationFile;
    }

    public void setFirebaseConfigurationFile(String firebaseConfigurationFile) {
        this.firebaseConfigurationFile = firebaseConfigurationFile;
    }

    public String getFirebaseUrl() {
        return firebaseUrl;
    }

    public void setFirebaseUrl(String firebaseUrl) {
        this.firebaseUrl = firebaseUrl;
    }
}

