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

package bisq.relay.notification;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Nonnull;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

public abstract class PushNotificationController implements Filter {
    private static final Logger LOG = LoggerFactory.getLogger(PushNotificationController.class);

    private final PushNotificationSender pushNotificationSender;
    private final ObjectMapper objectMapper;

    protected PushNotificationController(@Nonnull final PushNotificationSender pushNotificationSender,
                                         @Nonnull final ObjectMapper objectMapper) {
        this.pushNotificationSender = Objects.requireNonNull(pushNotificationSender);
        this.objectMapper = Objects.requireNonNull(objectMapper);
    }

    @Override
    public void doFilter(final ServletRequest request, final ServletResponse response,
                         final FilterChain chain) throws IOException, ServletException {
        HttpServletResponse httpServletResponse = (HttpServletResponse) response;
        httpServletResponse.setContentType(MediaType.APPLICATION_JSON_VALUE);
        chain.doFilter(request, response);
    }

    // TODO implement rate limiting, can use resilience4j
    //  Ref: https://www.baeldung.com/spring-boot-resilience4j
    public CompletableFuture<ResponseEntity<String>> handleRequest(
            @Nonnull final String deviceToken,
            @Nonnull final PushNotificationMessage pushNotificationMessage) {
        Objects.requireNonNull(deviceToken);
        Objects.requireNonNull(pushNotificationMessage);

        return pushNotificationSender.sendNotification(pushNotificationMessage, deviceToken)
                .thenApply(notificationResult -> {
                    final String body;
                    try {
                        body = objectMapper.writeValueAsString(notificationResult);
                    } catch (JsonProcessingException e) {
                        LOG.error("Unable to serialize notification result; {}\n{}", e.getMessage(), notificationResult);
                        return ResponseEntity.internalServerError().body("");
                    }
                    if (notificationResult.wasAccepted()) {
                        return ResponseEntity.ok().body(body);
                    }
                    return ResponseEntity.badRequest().body(body);
                })
                .exceptionally(cause -> ResponseEntity.internalServerError().body(""));
    }
}
