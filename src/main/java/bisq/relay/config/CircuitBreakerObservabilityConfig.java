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

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Configuration;

import static bisq.relay.notification.metrics.PushMetrics.METRIC_PUSH_SHORT_CIRCUITED_TOTAL;
import static bisq.relay.notification.metrics.PushMetrics.TAG_PROVIDER;

/**
 * Observability glue for Resilience4j circuit breakers.
 * <p>
 * This configuration registers listeners on all {@link CircuitBreaker} instances from the
 * {@link CircuitBreakerRegistry} to provide two things:
 * <ol>
 *   <li><strong>State transition logs</strong> (CLOSED/OPEN/HALF_OPEN) including the provider identifier.</li>
 *   <li><strong>A dedicated short-circuit metric</strong> that increments when a call is not permitted
 *       because the breaker is OPEN: {@code push_short_circuited_total{provider="apns|fcm"}}.</li>
 * </ol>
 * <p>
 * <strong>Provider identifier:</strong> this uses the circuit breaker instance name (e.g., {@code apns}, {@code fcm})
 * as the {@code provider} label, keeping it aligned with push metrics tagging.
 * <p>
 * <strong>Micrometer registry:</strong> the {@link MeterRegistry} is resolved lazily via {@link ObjectProvider}
 * so this remains safe in tests or runtime modes where no registry is configured.
 */
@Configuration
public class CircuitBreakerObservabilityConfig {

    private static final Logger LOG = LoggerFactory.getLogger(CircuitBreakerObservabilityConfig.class);

    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final ObjectProvider<MeterRegistry> meterRegistryProvider;

    public CircuitBreakerObservabilityConfig(
            final CircuitBreakerRegistry circuitBreakerRegistry,
            final ObjectProvider<MeterRegistry> meterRegistryProvider) {
        this.circuitBreakerRegistry = circuitBreakerRegistry;
        this.meterRegistryProvider = meterRegistryProvider;
    }

    @PostConstruct
    void registerListeners() {
        // Register already-created circuit breakers
        circuitBreakerRegistry.getAllCircuitBreakers().forEach(this::registerFor);

        // Register any circuit breakers that get created later
        circuitBreakerRegistry.getEventPublisher()
                .onEntryAdded(event -> registerFor(event.getAddedEntry()));
    }

    private void registerFor(final CircuitBreaker cb) {
        cb.getEventPublisher()
                .onStateTransition(event ->
                        LOG.warn("CircuitBreaker state transition: provider={} {} -> {}",
                                cb.getName(),
                                event.getStateTransition().getFromState(),
                                event.getStateTransition().getToState())
                )
                .onCallNotPermitted(event -> {
                    LOG.warn("CircuitBreaker short-circuited call: provider={} state={}",
                            cb.getName(),
                            cb.getState());

                    // Resolve registry lazily and tolerate absence (e.g., in certain tests)
                    MeterRegistry registry = meterRegistryProvider.getIfAvailable();
                    if (registry != null) {
                        registry.counter(METRIC_PUSH_SHORT_CIRCUITED_TOTAL, TAG_PROVIDER, cb.getName()).increment();
                    }
                });
    }
}
