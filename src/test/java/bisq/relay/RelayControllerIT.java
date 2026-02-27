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
import bisq.relay.notification.PushNotificationResult;
import bisq.relay.notification.apns.ApnsPushNotificationSender;
import bisq.relay.notification.fcm.FcmPushNotificationSender;
import org.apache.commons.codec.binary.Hex;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("integrationtest")
class RelayControllerIT {

    /**
     * Bisq v1 format: hex-encoded FCM token (hex-decode yields printable ASCII)
     */
    private static final String BISQ_V1_DEVICE_TOKEN =
            "655f4d327849323153616543657774544c48365a504f3a41504139316247646a4d645034757a5f5954444a72482d" +
                    "576c74584b6c4171594d665348434b2d7443522d646153786e5477754b445a4978785f4b7a64656d41483268794b54" +
                    "746237756d776c35544d48414d4c4f44357753336241454b4761713468574d75723538326a31743570336c79476f30" +
                    "5a30394d4e53466d784d566a32465a4b366f507674534e";
    /**
     * Bisq2 format: plain APNs hex token (64 hex chars representing 32 raw bytes)
     */
    private static final String BISQ_V2_APNS_TOKEN = "a1b2c3d4e5f60718293a4b5c6d7e8f90a1b2c3d4e5f60718293a4b5c6d7e8f90";
    /**
     * Bisq2 format: plain FCM token (contains non-hex chars like ':', '_', '-')
     */
    private static final String BISQ_V2_FCM_TOKEN = "e_M2xI21SaeCewtTLH6ZPO:APA91bGdjMdP4uz_YTDJrH-WltXKlAqYMfSHCK-" +
            "tCR-daSxnTwuKDZIxx";
    private static final String HEX_ENCODED_MSG = Hex.encodeHexString(
            "An Encrypted Message".getBytes(StandardCharsets.UTF_8));

    @MockitoBean
    private ApnsPushNotificationSender apnsSender;
    @MockitoBean
    private FcmPushNotificationSender fcmSender;

    @Autowired
    private MockMvc mockMvc;

    private HttpHeaders httpHeaders;

    @BeforeEach
    void setup() {
        httpHeaders = new HttpHeaders();
        httpHeaders.set(HttpHeaders.USER_AGENT, "MockMvc");
        httpHeaders.set(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
    }

    // ----------------------------
    // Parameter validation tests
    // ----------------------------

    private static Stream<Arguments> invalidRequests() {
        return Stream.of(
                Arguments.of(MockMvcRequestBuilders.get("/relay"),
                        "Missing token parameter"),
                Arguments.of(MockMvcRequestBuilders.get("/relay")
                                .param("msg", HEX_ENCODED_MSG),
                        "Missing token parameter"),
                Arguments.of(MockMvcRequestBuilders.get("/relay")
                                .param("token", BISQ_V1_DEVICE_TOKEN),
                        "Missing msg parameter"),
                Arguments.of(MockMvcRequestBuilders.get("/relay")
                                .param("token", BISQ_V2_APNS_TOKEN)
                                .param("msg", "Not a hex message"),
                        "Invalid msg parameter value"),
                Arguments.of(MockMvcRequestBuilders.get("/relay")
                                .param("token", BISQ_V2_FCM_TOKEN)
                                .param("msg", Hex.encodeHexString("".getBytes(StandardCharsets.UTF_8))),
                        "Missing msg parameter"),
                Arguments.of(MockMvcRequestBuilders.get("/relay")
                                .param("token", BISQ_V1_DEVICE_TOKEN)
                                .param("msg", Hex.encodeHexString(" ".getBytes(StandardCharsets.UTF_8))),
                        "Invalid msg parameter value")
        );
    }

    @ParameterizedTest(name = "{index} => {1}")
    @MethodSource("invalidRequests")
    void whenRelayingNotificationWithMissingOrInvalidParameters_thenBadRequestResponseReturned(
            RequestBuilder requestBuilder,
            String expectedMessage
    ) throws Exception {
        expectBadRequestBadArguments(
                mockMvc.perform(((MockHttpServletRequestBuilder) requestBuilder)
                        .headers(httpHeaders)),
                expectedMessage
        );

        verifyNoInteractions(apnsSender);
        verifyNoInteractions(fcmSender);
    }

    // ----------------------------
    // APNs/FCM relay behavior tests
    // ----------------------------

    private enum Provider {
        APNS(false, "{\"wasAccepted\":false,\"errorCode\":\"Unregistered\",\"isUnregistered\":true}"),
        FCM(true, "{\"wasAccepted\":false,\"errorCode\":\"UNREGISTERED\",\"isUnregistered\":true}");

        final boolean isAndroid;
        final String expectedErrorBodyJson;

        Provider(boolean isAndroid, String expectedErrorBodyJson) {
            this.isAndroid = isAndroid;
            this.expectedErrorBodyJson = expectedErrorBodyJson;
        }
    }

    static Stream<Arguments> providerAndToken() {
        return Stream.of(
                Arguments.of(Provider.APNS, BISQ_V1_DEVICE_TOKEN),
                Arguments.of(Provider.FCM, BISQ_V1_DEVICE_TOKEN),
                Arguments.of(Provider.APNS, BISQ_V2_APNS_TOKEN),
                Arguments.of(Provider.FCM, BISQ_V2_FCM_TOKEN)
        );
    }

    @ParameterizedTest(name = "{index} => {0} success")
    @MethodSource("providerAndToken")
    void whenRelayingValidNotification_thenSuccessfulResponseReturned(Provider provider, String deviceToken) throws Exception {
        givenNotificationWillBeAccepted(provider);

        MvcResult asyncResult = performAsyncRelay(provider.isAndroid, deviceToken);

        assertThat(asyncResult.getResponse().getStatus()).isEqualTo(HttpStatus.OK.value());
        assertThat(asyncResult.getResponse().getContentType()).isEqualTo(MediaType.APPLICATION_JSON_VALUE);
        assertThat(asyncResult.getResponse().getContentAsString()).isEqualTo("success");
    }

    @ParameterizedTest(name = "{index} => {0} rejected")
    @MethodSource("providerAndToken")
    void whenSendingInvalidNotification_thenBadRequestResponseReturned(Provider provider, String deviceToken) throws Exception {
        givenNotificationWillBeRejected(provider);

        MvcResult asyncResult = performAsyncRelay(provider.isAndroid, deviceToken);

        assertThat(asyncResult.getResponse().getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(asyncResult.getResponse().getContentType()).isEqualTo(MediaType.APPLICATION_JSON_VALUE);
        assertThat(asyncResult.getResolvedException()).isInstanceOf(BadArgumentsException.class);
        assertThat(Objects.requireNonNull(asyncResult.getResolvedException()).getMessage())
                .isEqualTo(provider.expectedErrorBodyJson);
    }

    // ----------------------------
    // Helpers
    // ----------------------------

    private void expectBadRequestBadArguments(ResultActions actions, String expectedMessage) throws Exception {
        actions.andExpect(status().isBadRequest())
                .andExpect(result ->
                        assertEquals(MediaType.APPLICATION_JSON_VALUE, result.getResponse().getContentType()))
                .andExpect(result ->
                        assertInstanceOf(BadArgumentsException.class, result.getResolvedException()))
                .andExpect(result ->
                        assertEquals(expectedMessage,
                                Objects.requireNonNull(result.getResolvedException()).getMessage()));
    }

    private MvcResult performAsyncRelay(boolean isAndroid, String deviceToken) throws Exception {
        RequestBuilder requestBuilder = MockMvcRequestBuilders
                .get("/relay")
                .param("isAndroid", String.valueOf(isAndroid))
                .param("msg", HEX_ENCODED_MSG)
                .param("token", deviceToken)
                .headers(httpHeaders);

        MvcResult mvcResult = mockMvc.perform(requestBuilder)
                .andExpect(request().asyncStarted())
                .andReturn();

        return mockMvc.perform(asyncDispatch(mvcResult)).andReturn();
    }

    private void givenNotificationWillBeAccepted(Provider provider) {
        CompletableFuture<PushNotificationResult> completableFuture = new CompletableFuture<>();
        completableFuture.complete(new PushNotificationResult(true, null, null, false));

        if (provider == Provider.APNS) {
            when(apnsSender.sendNotification(isA(PushNotificationMessage.class), isA(String.class)))
                    .thenReturn(completableFuture);
        } else {
            when(fcmSender.sendNotification(isA(PushNotificationMessage.class), isA(String.class)))
                    .thenReturn(completableFuture);
        }
    }

    private void givenNotificationWillBeRejected(Provider provider) {
        CompletableFuture<PushNotificationResult> completableFuture = new CompletableFuture<>();
        if (provider == Provider.APNS) {
            completableFuture.complete(
                    new PushNotificationResult(false, "Unregistered", null, true));
            when(apnsSender.sendNotification(isA(PushNotificationMessage.class), isA(String.class)))
                    .thenReturn(completableFuture);
        } else {
            completableFuture.complete(
                    new PushNotificationResult(false, "UNREGISTERED", null, true));
            when(fcmSender.sendNotification(isA(PushNotificationMessage.class), isA(String.class)))
                    .thenReturn(completableFuture);
        }
    }
}
