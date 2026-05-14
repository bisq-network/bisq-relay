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

package bisq.relay.util;

import jakarta.annotation.Nonnull;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

/**
 * Utilities for bridging cancellation between wrapper futures and provider-specific futures.
 */
public final class FutureCancellationBridge {

    private FutureCancellationBridge() {
        throw new AssertionError("This class must not be instantiated");
    }

    /**
     * Bridges cancellation from a wrapper future to an underlying provider future so cancellation
     * propagates to the provider, helping avoid unnecessary work and resource usage after the caller
     * has already cancelled.
     *
     * @param wrapperFuture  the wrapper future whose cancellation should be observed
     * @param providerFuture the underlying provider future to cancel when the wrapper is cancelled
     */
    public static void bridgeCancellation(
            @Nonnull final CompletableFuture<?> wrapperFuture,
            @Nonnull final Future<?> providerFuture
    ) {
        wrapperFuture.whenComplete((r, t) -> {
            if (t instanceof CancellationException) {
                providerFuture.cancel(true);
            }
        });
    }
}
