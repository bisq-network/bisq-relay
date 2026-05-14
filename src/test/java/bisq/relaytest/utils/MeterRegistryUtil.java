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

import io.micrometer.core.instrument.MeterRegistry;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.ObjectProvider;

import java.util.stream.Stream;

public class MeterRegistryUtil {

    /**
     * Returns an {@link ObjectProvider} for tests that always supplies the given meter registry.
     */
    public static ObjectProvider<MeterRegistry> providerOf(final MeterRegistry registry) {
        return new ObjectProvider<>() {
            @NotNull
            @Override
            public MeterRegistry getObject(@NotNull Object... args) {
                return registry;
            }

            @NotNull
            @Override
            public MeterRegistry getObject() {
                return registry;
            }

            @Override
            public MeterRegistry getIfAvailable() {
                return registry;
            }

            @Override
            public MeterRegistry getIfUnique() {
                return registry;
            }

            @NotNull
            @Override
            public Stream<MeterRegistry> stream() {
                return Stream.of(registry);
            }

            @NotNull
            @Override
            public Stream<MeterRegistry> orderedStream() {
                return Stream.of(registry);
            }
        };
    }
}
