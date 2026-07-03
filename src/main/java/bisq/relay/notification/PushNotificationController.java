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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

public abstract class PushNotificationController {
    private static final Logger LOG = LoggerFactory.getLogger(PushNotificationController.class);

    private final PushNotificationSender pushNotificationSender;
    private final ObjectMapper objectMapper;

    protected PushNotificationController(
            @Nonnull final PushNotificationSender pushNotificationSender,
            @Nonnull final ObjectMapper objectMapper
    ) {
        this.pushNotificationSender = Objects.requireNonNull(pushNotificationSender);
        this.objectMapper = Objects.requireNonNull(objectMapper);
    }

    // TODO implement rate limiting, can use resilience4j
    //  Ref: https://www.baeldung.com/spring-boot-resilience4j
    public CompletableFuture<ResponseEntity<String>> handleRequest(
            @Nonnull final String deviceToken,
            @Nonnull final PushNotificationMessage pushNotificationMessage
    ) {
        Objects.requireNonNull(deviceToken);
        Objects.requireNonNull(pushNotificationMessage);

        return pushNotificationSender.sendNotification(pushNotificationMessage, deviceToken)
                .thenApply(notificationResult -> {
                    final String body;
                    try {
                        body = objectMapper.writeValueAsString(notificationResult);
                    } catch (JsonProcessingException e) {
                        LOG.error("Unable to serialize notification result; {}\n{}", e.getMessage(), notificationResult);
                        return ResponseEntity
                                .internalServerError()
                                .contentType(MediaType.APPLICATION_JSON)
                                .body("");
                    }

                    if (notificationResult.wasAccepted()) {
                        return ResponseEntity
                                .ok()
                                .contentType(MediaType.APPLICATION_JSON)
                                .body(body);
                    }

                    return ResponseEntity
                            .badRequest()
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(body);
                })
                .exceptionally(cause -> ResponseEntity
                        .internalServerError()
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(""));
    }
}
