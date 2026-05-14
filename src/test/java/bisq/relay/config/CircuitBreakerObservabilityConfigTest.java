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
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.IntStream;
import java.util.stream.Stream;

import static bisq.relay.notification.metrics.PushMetrics.*;
import static bisq.relaytest.utils.CircuitBreakerUtil.openCircuitBreaker;
import static bisq.relaytest.utils.CircuitBreakerUtil.triggerCallNotPermittedEvent;
import static bisq.relaytest.utils.MeterRegistryUtil.providerOf;
import static org.assertj.core.api.Assertions.assertThat;

class CircuitBreakerObservabilityConfigTest {

    private final CircuitBreakerRegistry cbRegistry = CircuitBreakerRegistry.ofDefaults();
    private final MeterRegistry meterRegistry = new SimpleMeterRegistry();

    private final CircuitBreakerObservabilityConfig config =
            new CircuitBreakerObservabilityConfig(cbRegistry, providerOf(meterRegistry));

    @BeforeEach
    void setUp() {
        // Manually invoke registration (no Spring context in this test)
        config.registerListeners();
    }

    @ParameterizedTest(name = "{index} => providerId={0}; numberOfCalls={1}")
    @MethodSource("provideProviderIdAndNumberOfCalls")
    void whenBreakerIsOpen_andCallsNotPermitted_thenMetricIsIncremented(
            final String providerId, final int numberOfCalls
    ) {
        CircuitBreaker cb = cbRegistry.circuitBreaker(providerId);

        openCircuitBreaker(cb);

        IntStream.range(0, numberOfCalls)
                .forEach(i -> triggerCallNotPermittedEvent(cb));

        Counter counter = meterRegistry.find(METRIC_PUSH_SHORT_CIRCUITED_TOTAL)
                .tag(TAG_PROVIDER, providerId)
                .counter();

        assertThat(counter)
                .describedAs("Short circuit counter for provider " + providerId)
                .isNotNull();
        assertThat(counter.count())
                .describedAs("Short circuit count for provider " + providerId)
                .isEqualTo(Double.valueOf(numberOfCalls));
    }

    private static Stream<Arguments> provideProviderIdAndNumberOfCalls() {
        return Stream.of(
                Arguments.of(PROVIDER_ID_APNS, 1),
                Arguments.of(PROVIDER_ID_FCM, 1),
                Arguments.of(PROVIDER_ID_APNS, 5),
                Arguments.of(PROVIDER_ID_FCM, 5)
        );
    }
}
