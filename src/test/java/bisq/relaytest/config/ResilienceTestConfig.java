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

package bisq.relaytest.config;

import bisq.relay.notification.resilience.ResilienceAsyncExecutor;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.springboot3.circuitbreaker.autoconfigure.CircuitBreakerAutoConfiguration;
import io.github.resilience4j.springboot3.retry.autoconfigure.RetryAutoConfiguration;
import io.github.resilience4j.springboot3.timelimiter.autoconfigure.TimeLimiterAutoConfiguration;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Spring test configuration: provides Resilience4j registries via auto-config.
 */
@Configuration
@ImportAutoConfiguration({
        CircuitBreakerAutoConfiguration.class,
        RetryAutoConfiguration.class,
        TimeLimiterAutoConfiguration.class
})
public class ResilienceTestConfig {

    @Bean(destroyMethod = "shutdown")
    public ScheduledExecutorService resilience4jScheduler() {
        // A single thread is enough for test timers/backoff
        return Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r);
            t.setName("test-resilience4j-scheduler");
            t.setDaemon(true);
            return t;
        });
    }

    @Bean
    public ResilienceAsyncExecutor resilienceAsyncExecutor(
            CircuitBreakerRegistry cbRegistry,
            RetryRegistry retryRegistry,
            TimeLimiterRegistry timeLimiterRegistry,
            ScheduledExecutorService scheduler
    ) {
        return new ResilienceAsyncExecutor(cbRegistry, retryRegistry, timeLimiterRegistry, scheduler);
    }
}
