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

package bisq.relaytest.utils;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import jakarta.annotation.Nonnull;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Utility class for managing circuit breaker states and permissions.
 */
public final class CircuitBreakerUtil {

    private CircuitBreakerUtil() {
        throw new AssertionError("This class must not be instantiated");
    }

    /**
     * Opens a circuit breaker.
     *
     * @param cb the circuit breaker to open
     */
    public static void openCircuitBreaker(@Nonnull final CircuitBreaker cb) {
        cb.transitionToOpenState();
        assertThat(cb.getState())
                .describedAs("Circuit breaker state for " + cb.getName())
                .isEqualTo(CircuitBreaker.State.OPEN);
    }

    /**
     * Transitions a circuit breaker to HALF_OPEN state.
     *
     * @param cb the circuit breaker to transition
     */
    public static void halfOpenCircuitBreaker(@Nonnull final CircuitBreaker cb) {
        cb.transitionToOpenState();
        cb.transitionToHalfOpenState();
        assertThat(cb.getState())
                .describedAs("Circuit breaker state for " + cb.getName())
                .isEqualTo(CircuitBreaker.State.HALF_OPEN);
    }

    /**
     * Triggers a call not permitted event on a circuit breaker.
     *
     * @param cb the circuit breaker to trigger the event on
     */
    public static void triggerCallNotPermittedEvent(@Nonnull final CircuitBreaker cb) {
        assertThat(cb.tryAcquirePermission())
                .describedAs("Circuit breaker permission acquisition for " + cb.getName())
                .isFalse();
    }
}
