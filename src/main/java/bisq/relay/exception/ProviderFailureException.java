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

package bisq.relay.exception;

/**
 * Signals a provider-side outage/throttle/server failure that should affect resilience policies
 * (e.g., count as a CircuitBreaker failure).
 * <p>
 * In contrast, client rejections (e.g., bad token or payload) should return a normal
 * {@code PushNotificationResult} and must not poison the breaker.
 */
public final class ProviderFailureException extends RuntimeException {

    public ProviderFailureException(final String message) {
        super(message);
    }

    public ProviderFailureException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
