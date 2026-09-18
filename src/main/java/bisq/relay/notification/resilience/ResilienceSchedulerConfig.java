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

package bisq.relay.notification.resilience;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Provides a shared {@link ScheduledExecutorService} used by Resilience4j for retries and timeouts.
 * <p>
 * Best to use a single shared scheduler rather than allocating thread pools per operation.
 */
@Configuration
public class ResilienceSchedulerConfig {

    /**
     * Shared scheduler for Resilience4j retry backoff and timeouts.
     *
     * @return a daemon-thread scheduled executor
     */
    @Bean(destroyMethod = "shutdown")
    public ScheduledExecutorService resilience4jScheduler() {
        int threads = Math.max(2, Runtime.getRuntime().availableProcessors() / 2);
        AtomicInteger idx = new AtomicInteger(1);

        ThreadFactory tf = r -> {
            Thread t = new Thread(r);
            t.setName("resilience4j-scheduler-" + idx.getAndIncrement());
            t.setDaemon(true);
            return t;
        };

        return Executors.newScheduledThreadPool(threads, tf);
    }
}
