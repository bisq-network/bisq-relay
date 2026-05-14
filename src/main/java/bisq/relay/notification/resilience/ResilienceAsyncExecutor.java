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

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import jakarta.annotation.Nonnull;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.concurrent.*;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Executes asynchronous operations under Resilience4j policies (CircuitBreaker, TimeLimiter, Retry).
 * <p>
 * <b>Composition order</b> (outer → inner):
 * <ul>
 *   <li>CircuitBreaker (fast-fail when OPEN)</li>
 *   <li>TimeLimiter (overall timeout budget)</li>
 *   <li>Retry (retries within the time budget)</li>
 *   <li>Actual supplier (outbound call)</li>
 * </ul>
 * <p>
 * <b>Fallback</b> is applied after the resilience chain, so it runs for OPEN circuits, timeouts, retries exhausted,
 * and unexpected exceptions.
 */
@Component
public class ResilienceAsyncExecutor {

    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final RetryRegistry retryRegistry;
    private final TimeLimiterRegistry timeLimiterRegistry;
    private final ScheduledExecutorService scheduler;

    public ResilienceAsyncExecutor(
            @Nonnull final CircuitBreakerRegistry circuitBreakerRegistry,
            @Nonnull final RetryRegistry retryRegistry,
            @Nonnull final TimeLimiterRegistry timeLimiterRegistry,
            @Nonnull final ScheduledExecutorService scheduler
    ) {
        this.circuitBreakerRegistry = Objects.requireNonNull(circuitBreakerRegistry, "circuitBreakerRegistry must not be null");
        this.retryRegistry = Objects.requireNonNull(retryRegistry, "retryRegistry must not be null");
        this.timeLimiterRegistry = Objects.requireNonNull(timeLimiterRegistry, "timeLimiterRegistry must not be null");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler must not be null");
    }

    /**
     * Executes an async supplier under CircuitBreaker/TimeLimiter/Retry policies using the same instance name
     * for each registry.
     * <p>
     * The {@code actualSupplier} <b>must</b> defer any outbound side effects until it is invoked. This method
     * ensures the circuit breaker gates invocation, so when OPEN, the supplier will not be called.
     *
     * @param instanceName   resilience4j instance name (e.g., "apns", "fcm")
     * @param actualSupplier supplier producing the {@link CompletionStage} for the operation
     * @param fallback       fallback mapping any failure (including OPEN breaker) to an alternate result stage
     * @param <T>            result type
     * @return a {@link CompletableFuture} completing with success or a fallback result
     */
    public <T> CompletableFuture<T> execute(
            @Nonnull final String instanceName,
            @Nonnull final Supplier<? extends CompletionStage<T>> actualSupplier,
            @Nonnull final Function<? super Throwable, ? extends CompletionStage<T>> fallback
    ) {
        Objects.requireNonNull(instanceName, "instanceName must not be null");
        Objects.requireNonNull(actualSupplier, "actualSupplier must not be null");
        Objects.requireNonNull(fallback, "fallback must not be null");

        final CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker(instanceName);
        final TimeLimiter tl = timeLimiterRegistry.timeLimiter(instanceName);
        final Retry retry = retryRegistry.retry(instanceName);

        // Convert synchronous exceptions (thrown before producing a stage) into a failed stage
        Supplier<CompletionStage<T>> decorated = () -> {
            try {
                return actualSupplier.get();
            } catch (Exception e) {
                return CompletableFuture.failedFuture(e);
            }
        };

        // Retry (inner)
        decorated = Retry.decorateCompletionStage(retry, scheduler, decorated);

        // TimeLimiter around the whole operation (including retries)
        decorated = TimeLimiter.decorateCompletionStage(tl, scheduler, decorated);

        // CircuitBreaker (outermost gate)
        decorated = CircuitBreaker.decorateCompletionStage(cb, decorated);

        CompletionStage<T> stage = decorated.get();

        // Apply fallback AFTER a resilience chain (covers OPEN, timeout, retries exhausted, etc.)
        return stage.handle((value, throwable) -> {
                    if (throwable == null) {
                        return CompletableFuture.completedFuture(value);
                    }
                    return fallback.apply(unwrap(throwable)).toCompletableFuture();
                })
                .thenCompose(Function.identity())
                .toCompletableFuture();
    }

    /**
     * Unwraps common wrapper exceptions produced by CompletionStage pipelines.
     *
     * @param t throwable to unwrap
     * @return underlying cause when available, otherwise the original throwable
     */
    private static Throwable unwrap(final Throwable t) {
        if (t instanceof CompletionException ce && ce.getCause() != null) return ce.getCause();
        if (t instanceof ExecutionException ee && ee.getCause() != null) return ee.getCause();
        return t;
    }
}
