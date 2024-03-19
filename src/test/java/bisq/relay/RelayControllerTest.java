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
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.codec.binary.Hex;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class RelayControllerTest {
    @MockBean
    private ApnsPushNotificationSender apnsSender;
    @MockBean
    private FcmPushNotificationSender fcmSender;

    @Autowired
    private MockMvc mockMvc;

    private HttpHeaders httpHeaders;
    private String deviceToken;
    private String message;

    @BeforeEach
    void setup() {
        httpHeaders = new HttpHeaders();
        httpHeaders.set(HttpHeaders.USER_AGENT, "MockMvc");
        httpHeaders.set(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
        deviceToken = "655f4d327849323153616543657774544c48365a504f3a41504139316247646a4d645034757a5f5954444a72482d" +
                "576c74584b6c4171594d665348434b2d7443522d646153786e5477754b445a4978785f4b7a64656d41483268794b547462" +
                "37756d776c35544d48414d4c4f44357753336241454b4761713468574d75723538326a31743570336c79476f305a30394d" +
                "4e53466d784d566a32465a4b366f507674534e";
        message = Hex.encodeHexString("Some Message".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void whenRelayingNotificationWithNoParameters_thenBadRequestResponseReturned() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders
                        .get("/relay")
                        .headers(httpHeaders))
                .andExpect(status().isBadRequest())
                .andExpect(result -> assertEquals(MediaType.APPLICATION_JSON_VALUE, result.getResponse().getContentType()))
                .andExpect(result -> assertInstanceOf(BadArgumentsException.class, result.getResolvedException()))
                .andExpect(result -> assertEquals("Missing token parameter", result.getResolvedException().getMessage()));
    }

    @Test
    void whenRelayingNotificationWithMissingTokenParameter_thenBadRequestResponseReturned() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders
                        .get("/relay")
                        .param("msg", message)
                        .headers(httpHeaders))
                .andExpect(status().isBadRequest())
                .andExpect(result -> assertEquals(MediaType.APPLICATION_JSON_VALUE, result.getResponse().getContentType()))
                .andExpect(result -> assertInstanceOf(BadArgumentsException.class, result.getResolvedException()))
                .andExpect(result -> assertEquals("Missing token parameter", result.getResolvedException().getMessage()));
    }

    @Test
    void whenRelayingNotificationWithMissingMessageParameter_thenBadRequestResponseReturned() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders
                        .get("/relay")
                        .param("token", deviceToken)
                        .headers(httpHeaders))
                .andExpect(status().isBadRequest())
                .andExpect(result -> assertEquals(MediaType.APPLICATION_JSON_VALUE, result.getResponse().getContentType()))
                .andExpect(result -> assertInstanceOf(BadArgumentsException.class, result.getResolvedException()))
                .andExpect(result -> assertEquals("Missing msg parameter", result.getResolvedException().getMessage()));
    }

    @Test
    void whenRelayingNotificationWithInvalidTokenValue_thenBadRequestResponseReturned() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders
                        .get("/relay")
                        .param("msg", message)
                        .param("token", "invalidToken")
                        .headers(httpHeaders))
                .andExpect(status().isBadRequest())
                .andExpect(result -> assertEquals(MediaType.APPLICATION_JSON_VALUE, result.getResponse().getContentType()))
                .andExpect(result -> assertInstanceOf(BadArgumentsException.class, result.getResolvedException()))
                .andExpect(result -> assertEquals("Invalid token parameter value", result.getResolvedException().getMessage()));
    }

    @Test
    void whenRelayingNotificationWithInvalidMessageValue_thenBadRequestResponseReturned() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders
                        .get("/relay")
                        .param("msg", "invalidMessage")
                        .param("token", deviceToken)
                        .headers(httpHeaders))
                .andExpect(status().isBadRequest())
                .andExpect(result -> assertEquals(MediaType.APPLICATION_JSON_VALUE, result.getResponse().getContentType()))
                .andExpect(result -> assertInstanceOf(BadArgumentsException.class, result.getResolvedException()))
                .andExpect(result -> assertEquals("Invalid msg parameter value", result.getResolvedException().getMessage()));
    }

    @Test
    void whenRelayingValidApnsNotification_thenSuccessfulResponseReturned() throws Exception {
        givenApnsNotificationWillBeAccepted();

        RequestBuilder requestBuilder = MockMvcRequestBuilders
                .get("/relay")
                .param("isAndroid", String.valueOf(false))
                .param("msg", message)
                .param("token", deviceToken)
                .headers(httpHeaders);
        MvcResult mvcResult = mockMvc.perform(requestBuilder)
                .andExpect(request().asyncStarted())
                .andReturn();
        MvcResult asyncResult = mockMvc.perform(asyncDispatch(mvcResult))
                .andReturn();
        assertThat(asyncResult.getResponse().getStatus()).isEqualTo(HttpStatus.OK.value());
        assertThat(asyncResult.getResponse().getContentType()).isEqualTo(MediaType.APPLICATION_JSON_VALUE);
        assertThat(asyncResult.getResponse().getContentAsString()).isEqualTo("success");
    }

    @Test
    void whenRelayingValidFcmNotification_thenSuccessfulResponseReturned() throws Exception {
        givenFcmNotificationWillBeAccepted();

        RequestBuilder requestBuilder = MockMvcRequestBuilders
                .get("/relay")
                .param("isAndroid", String.valueOf(true))
                .param("msg", message)
                .param("token", deviceToken)
                .headers(httpHeaders);
        MvcResult mvcResult = mockMvc.perform(requestBuilder)
                .andExpect(request().asyncStarted())
                .andReturn();
        MvcResult asyncResult = mockMvc.perform(asyncDispatch(mvcResult))
                .andReturn();
        assertThat(asyncResult.getResponse().getStatus()).isEqualTo(HttpStatus.OK.value());
        assertThat(asyncResult.getResponse().getContentType()).isEqualTo(MediaType.APPLICATION_JSON_VALUE);
        assertThat(asyncResult.getResponse().getContentAsString()).isEqualTo("success");
    }

    @Test
    void whenSendInvalidApnsNotification_thenBadRequestResponseReturned() throws Exception {
        givenApnsNotificationWillBeRejected();

        RequestBuilder requestBuilder = MockMvcRequestBuilders
                .get("/relay")
                .param("isAndroid", String.valueOf(false))
                .param("msg", message)
                .param("token", deviceToken)
                .headers(httpHeaders);
        MvcResult mvcResult = mockMvc.perform(requestBuilder)
                .andExpect(request().asyncStarted())
                .andReturn();
        MvcResult asyncResult = mockMvc.perform(asyncDispatch(mvcResult))
                .andReturn();
        assertThat(asyncResult.getResponse().getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(asyncResult.getResponse().getContentType()).isEqualTo(MediaType.APPLICATION_JSON_VALUE);
        assertThat(asyncResult.getResolvedException()).isInstanceOf(BadArgumentsException.class);
        assertThat(asyncResult.getResolvedException().getMessage()).isEqualTo("{\"wasAccepted\":false,\"errorCode\":\"Unregistered\",\"isUnregistered\":true}");
    }

    @Test
    void whenSendInvalidFcmNotification_thenBadRequestResponseReturned() throws Exception {
        givenFcmNotificationWillBeRejected();

        RequestBuilder requestBuilder = MockMvcRequestBuilders
                .get("/relay")
                .param("isAndroid", String.valueOf(true))
                .param("msg", message)
                .param("token", deviceToken)
                .headers(httpHeaders);
        MvcResult mvcResult = mockMvc.perform(requestBuilder)
                .andExpect(request().asyncStarted())
                .andReturn();
        MvcResult asyncResult = mockMvc.perform(asyncDispatch(mvcResult))
                .andReturn();
        assertThat(asyncResult.getResponse().getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(asyncResult.getResponse().getContentType()).isEqualTo(MediaType.APPLICATION_JSON_VALUE);
        assertThat(asyncResult.getResolvedException()).isInstanceOf(BadArgumentsException.class);
        assertThat(asyncResult.getResolvedException().getMessage()).isEqualTo("{\"wasAccepted\":false,\"errorCode\":\"UNREGISTERED\",\"isUnregistered\":true}");
    }

    private void givenApnsNotificationWillBeAccepted() {
        CompletableFuture<PushNotificationResult> completableFuture = new CompletableFuture<>();
        completableFuture.complete(new PushNotificationResult(true, null, null, false));
        when(apnsSender.sendNotification(isA(PushNotificationMessage.class), isA(String.class)))
                .thenReturn(completableFuture);
    }

    private void givenApnsNotificationWillBeRejected() {
        CompletableFuture<PushNotificationResult> completableFuture = new CompletableFuture<>();
        completableFuture.complete(new PushNotificationResult(false, "Unregistered", null, true));
        when(apnsSender.sendNotification(isA(PushNotificationMessage.class), isA(String.class)))
                .thenReturn(completableFuture);
    }

    private void givenFcmNotificationWillBeAccepted() {
        CompletableFuture<PushNotificationResult> completableFuture = new CompletableFuture<>();
        completableFuture.complete(new PushNotificationResult(true, null, null, false));
        when(fcmSender.sendNotification(isA(PushNotificationMessage.class), isA(String.class)))
                .thenReturn(completableFuture);
    }

    private void givenFcmNotificationWillBeRejected() {
        CompletableFuture<PushNotificationResult> completableFuture = new CompletableFuture<>();
        completableFuture.complete(new PushNotificationResult(false, "UNREGISTERED", null, true));
        when(fcmSender.sendNotification(isA(PushNotificationMessage.class), isA(String.class)))
                .thenReturn(completableFuture);
    }
}
