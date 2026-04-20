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
            @Autowired(required = false) final FcmPushNotificationController fcmPushNotificationController) {
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

        final String deviceToken = decodeDeviceToken(deviceTokenHex);
        final String encryptedMessage = decodeParameter(encryptedMessageHex, "msg");

        final PushNotificationMessage pushNotificationMessage = new PushNotificationMessage(
                encryptedMessage, true, false);

        if (isAndroid.isPresent() && isAndroid.get().equals(true)) {
            if (fcmPushNotificationController == null) {
                throw new BadArgumentsException("FCM is not enabled on this server");
            }
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

    /**
     * Extracts the device token, handling both Bisq v1 and Bisq2 formats.
     *
     * Bisq v1 hex-encodes all parameters (including the token) before sending.
     * Bisq2 sends the device token as-is (a hex string for APNs, or an opaque
     * string for FCM).
     *
     * To distinguish: we try hex-decoding the input. If the result is entirely
     * printable ASCII, it was hex-encoded (Bisq v1) and we return the decoded
     * value. If hex-decoding fails or produces non-printable bytes, the input
     * is already the raw token (Bisq2) and we pass it through unchanged.
     */
    private String decodeDeviceToken(final Optional<String> deviceTokenHex) {
        if (deviceTokenHex.isEmpty()) {
            LOG.error("Missing token parameter");
            throw new BadArgumentsException("Missing token parameter");
        }
        final String raw = deviceTokenHex.get();
        try {
            final String decoded = new String(Hex.decodeHex(raw.toCharArray()), StandardCharsets.UTF_8);
            if (isPrintableAscii(decoded)) {
                LOG.debug("Device token appears hex-encoded (Bisq v1 format), decoded to {} chars", decoded.length());
                return decoded;
            }
        } catch (DecoderException e) {
            // Not valid hex — fall through to use raw value
        }
        LOG.debug("Device token used as-is (Bisq2 format), {} chars", raw.length());
        return raw;
    }

    private static boolean isPrintableAscii(final String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c < 0x20 || c > 0x7E) {
                return false;
            }
        }
        return !s.isEmpty();
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
