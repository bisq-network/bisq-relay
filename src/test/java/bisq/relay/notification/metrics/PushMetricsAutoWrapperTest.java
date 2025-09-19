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

import bisq.relay.notification.PushNotificationMessage;
import bisq.relay.notification.PushNotificationResult;
import bisq.relay.notification.PushNotificationSender;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import java.util.concurrent.CompletableFuture;

import static bisq.relay.notification.metrics.PushMetrics.PROVIDER_ID_APNS;
import static org.assertj.core.api.Assertions.assertThat;

class PushMetricsAutoWrapperTest {

    @Configuration
    @Import(PushMetricsAutoWrapper.class)
    static class Cfg {
        @Bean
        SimpleMeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }

        @PushProvider(PROVIDER_ID_APNS)
        static class TestApnsSender implements PushNotificationSender {
            @Override
            public CompletableFuture<PushNotificationResult> sendNotification(
                    PushNotificationMessage msg, String token) {
                return CompletableFuture.completedFuture(
                        new PushNotificationResult(true, null, null, false));
            }
        }

        @Bean
        PushNotificationSender apnsSender() {
            return new TestApnsSender();
        }
    }

    @Test
    void beansAreWrappedWithMetricsDecorator() {
        AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(Cfg.class);
        PushNotificationSender bean = ctx.getBean(PushNotificationSender.class);

        assertThat(bean).isInstanceOf(MetricsPushNotificationSender.class);

        // Smoke: call and ensure metrics recorded
        bean.sendNotification(new PushNotificationMessage("foo", true), "tok").join();
        var reg = ctx.getBean(SimpleMeterRegistry.class);
        assertThat(reg.get(PushMetrics.METRIC_PUSH_ATTEMPTS_TOTAL)
                .tag(PushMetrics.TAG_PROVIDER, PROVIDER_ID_APNS).counter().count()).isEqualTo(1.0);

        ctx.close();
    }
}
