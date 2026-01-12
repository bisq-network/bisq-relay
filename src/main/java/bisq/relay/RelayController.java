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

package bisq.relay;

import bisq.relay.exception.BadArgumentsException;
import bisq.relay.notification.PushNotificationMessage;
import bisq.relay.notification.apns.ApnsPushNotificationController;
import bisq.relay.notification.fcm.FcmPushNotificationController;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static bisq.relay.util.MaskingUtil.maskSensitive;

/**
 * This controller exists to handle legacy requests for the /relay endpoint.
 *
 * @deprecated to be removed once clients are updated to use the new APNs/FCM specific endpoints.
 */
@RestController
@Deprecated(since = "2.0", forRemoval = true)
public class RelayController {
    private static final Logger LOG = LoggerFactory.getLogger(RelayController.class);
    // Used in Bisq app to check for success state. We won't want a code dependency just for that string,
    // so we keep it duplicated in core and here. Must not be changed.
    private static final String SUCCESS = "success";

    private final ApnsPushNotificationController apnsPushNotificationController;
    private final FcmPushNotificationController fcmPushNotificationController;

    @Autowired
    public RelayController(
            final ApnsPushNotificationController apnsPushNotificationController,
            final FcmPushNotificationController fcmPushNotificationController) {
        this.apnsPushNotificationController = apnsPushNotificationController;
        this.fcmPushNotificationController = fcmPushNotificationController;
    }

    @GetMapping(value = "/relay")
    public CompletableFuture<String> relayNotification(
            @RequestParam("isAndroid") final Optional<Boolean> isAndroid,
            @RequestParam("token") final Optional<String> deviceTokenHex,
            @RequestParam("msg") final Optional<String> encryptedMessageHex,
            final HttpServletRequest httpRequest) {

        if (LOG.isInfoEnabled()) {
            LOG.info("Relaying notification; isAndroid={} token={} encryptedMessage={}",
                    isAndroid.orElse(null),
                    maskSensitive(deviceTokenHex.orElse(null)),
                    maskSensitive(encryptedMessageHex.orElse(null)));
        }

        final String deviceToken = decodeParameter(deviceTokenHex, "token");
        final String encryptedMessage = decodeParameter(encryptedMessageHex, "msg");

        final PushNotificationMessage pushNotificationMessage = new PushNotificationMessage(
                encryptedMessage, true);

        if (isAndroid.isPresent() && isAndroid.get().equals(true)) {
            return fcmPushNotificationController.sendFcmNotification(
                    deviceToken, pushNotificationMessage, httpRequest).thenApply(result -> {
                if (result.getStatusCode().equals(HttpStatus.OK)) {
                    return SUCCESS;
                }
                throw new BadArgumentsException(result.getBody());
            });
        } else {
            return apnsPushNotificationController.sendApnsNotification(
                    deviceToken, pushNotificationMessage, httpRequest).thenApply(result -> {
                if (result.getStatusCode().equals(HttpStatus.OK)) {
                    return SUCCESS;
                }
                throw new BadArgumentsException(result.getBody());
            });
        }
    }

    private String decodeParameter(final Optional<String> parameterHexValue, final String parameterName) {
        if (parameterHexValue.isEmpty()) {
            final String errorMessage = String.format("Missing %s parameter", parameterName);
            LOG.error(errorMessage);
            throw new BadArgumentsException(errorMessage);
        }

        try {
            return new String(Hex.decodeHex(parameterHexValue.get().toCharArray()), StandardCharsets.UTF_8);
        } catch (DecoderException e) {
            final String errorMessage = String.format("Invalid %s parameter value", parameterName);
            LOG.error(errorMessage, e);
            throw new BadArgumentsException(errorMessage);
        }
    }
}
