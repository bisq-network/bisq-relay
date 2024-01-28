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

package bisq.relay.notification.apns;

import bisq.relay.notification.PushNotificationMessage;
import bisq.relay.notification.PushNotificationController;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.util.concurrent.CompletableFuture;

@RestController
public class ApnsPushNotificationController extends PushNotificationController {
    private static final Logger LOG = LoggerFactory.getLogger(ApnsPushNotificationController.class);

    @Autowired
    public ApnsPushNotificationController(final ApnsPushNotificationSender apnsSender, final ObjectMapper objectMapper) {
        super(apnsSender, objectMapper);
    }

    @PostMapping(value = "/v1/apns/device/{deviceToken}")
    public CompletableFuture<ResponseEntity<String>> sendApnsNotification(
            @PathVariable("deviceToken") final String deviceToken,
            @Valid @RequestBody final PushNotificationMessage pushNotificationMessage,
            final HttpServletRequest httpRequest) {

        if (LOG.isInfoEnabled()) {
            LOG.info("Handling APNs notification for device token [{}] from [{}]", deviceToken,
                    httpRequest.getHeader(HttpHeaders.USER_AGENT));
        }

        return handleRequest(deviceToken, pushNotificationMessage);
    }
}
