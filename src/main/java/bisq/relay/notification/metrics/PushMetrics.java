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

package bisq.relay.notification.metrics;

/**
 * Constants for push notification metrics and provider IDs.
 * <p>
 * Defines metric names, tag keys, tag values, provider IDs, and classification categories.
 */
public final class PushMetrics {

    private PushMetrics() {
        throw new AssertionError("This class must not be instantiated");
    }

    // ========================================================================
    // Provider IDs
    // ========================================================================

    public static final String PROVIDER_ID_APNS = "apns";
    public static final String PROVIDER_ID_FCM = "fcm";

    // ========================================================================
    // Metric names
    // ========================================================================

    /**
     * Counter for push notification attempts, tagged by provider.
     */
    public static final String METRIC_PUSH_ATTEMPTS_TOTAL = "push_attempts_total";

    /**
     * Counter for completed sends, tagged by provider and result.
     */
    public static final String METRIC_PUSH_TOTAL = "push_total";

    /**
     * Histogram of send latencies in seconds, tagged by provider, result, and code.
     */
    public static final String METRIC_PUSH_LATENCY_SECONDS = "push_latency_seconds";

    // ========================================================================
    // Tag keys
    // ========================================================================

    public static final String TAG_PROVIDER = "provider";
    public static final String TAG_RESULT = "result";
    public static final String TAG_CODE = "code";

    // ========================================================================
    // Tag values: result
    // ========================================================================

    /**
     * Result: push accepted by the provider.
     */
    public static final String RESULT_ACCEPTED = "accepted";

    /**
     * Result: push rejected by the provider.
     */
    public static final String RESULT_REJECTED = "rejected";

    /**
     * Result: push failed before receiving a provider response.
     */
    public static final String RESULT_ERROR = "error";

    // ========================================================================
    // Tag values: code classification
    // ========================================================================

    /**
     * No error code (used for accepted pushes).
     */
    public static final String CODE_NONE = "none";

    /**
     * Token-related problem (invalid, unregistered, mismatched).
     */
    public static final String CODE_TOKEN = "token";

    /**
     * Payload too large or invalid in size.
     */
    public static final String CODE_PAYLOAD = "payload";

    /**
     * Provider throttling requests.
     */
    public static final String CODE_THROTTLE = "throttle";

    /**
     * Server-side error at the provider.
     */
    public static final String CODE_SERVER = "server";

    /**
     * Authentication/authorization problem.
     */
    public static final String CODE_AUTH = "auth";

    /**
     * I/O or network-level failure before provider response.
     */
    public static final String CODE_IO = "io";

    /**
     * Unclassified or unexpected error.
     */
    public static final String CODE_OTHER = "other";
}
