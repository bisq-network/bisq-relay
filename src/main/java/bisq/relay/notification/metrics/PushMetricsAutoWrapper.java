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

import bisq.relay.notification.PushNotificationSender;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.Nonnull;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanInitializationException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;

/**
 * Auto-wraps all {@link PushNotificationSender} beans with {@link MetricsPushNotificationSender}
 * to emit standardized metrics.
 */
class PushMetricsAutoWrapper implements BeanPostProcessor {

    private final ObjectProvider<MeterRegistry> registryProvider;

    public PushMetricsAutoWrapper(ObjectProvider<MeterRegistry> registryProvider) {
        // Do NOT resolve here; keep it lazy
        this.registryProvider = registryProvider;
    }

    @Override
    public Object postProcessAfterInitialization(
            @Nonnull final Object bean,
            @Nonnull final String beanName) throws BeansException {

        if (bean instanceof MetricsPushNotificationSender) {
            // Avoid double wrapping
            return bean;
        }

        if (bean instanceof PushNotificationSender sender) {
            final PushProvider ann = bean.getClass().getAnnotation(PushProvider.class);
            if (ann == null || ann.value().isBlank()) {
                throw new BeanInitializationException(
                        "PushNotificationSender bean '" + beanName + "' (" + bean.getClass().getName() + ") " +
                                "must be annotated with @PushProvider(\"<providerId>\") and providerId must be non-blank.");
            }
            String providerId = ann.value().trim();

            return new MetricsPushNotificationSender(providerId, sender, registryProvider);
        }

        return bean;
    }
}
