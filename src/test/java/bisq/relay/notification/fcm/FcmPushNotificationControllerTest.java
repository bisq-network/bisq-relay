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

package bisq.relay.notification.fcm;

import bisq.relay.notification.PushNotificationMessage;
import bisq.relay.notification.PushNotificationResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FcmPushNotificationControllerTest {
    @MockBean
    private FcmPushNotificationSender fcmSender;

    @SpyBean
    private ObjectMapper objectMapper;

    @Autowired
    private MockMvc mockMvc;

    private HttpHeaders httpHeaders;
    private String deviceToken;

    @BeforeEach
    void setup() {
        httpHeaders = new HttpHeaders();
        httpHeaders.set(HttpHeaders.USER_AGENT, "MockMvc");
        deviceToken = "d4HedtovQCyRdgPsxM0JbA:APA91bFJIwRdBpO4SQpeSuA5rpEnu5N3Y3_c1T5x69gpedyKwGLUrApT6xkwIq8LZVPCy" +
                "KVi1nh5NdG37TN2nGhpqchOUCysHweuL8V023WJYVwGgpUvdkk5mkYD9D3_QFj2c7f_2ul6";
    }

    @Test
    void whenSendFcmNotificationWithMissingToken_thenNotFoundResponseReturned() throws Exception {
        RequestBuilder requestBuilder = MockMvcRequestBuilders
                .post("/v1/fcm/device/")
                .headers(httpHeaders)
                .contentType(MediaType.APPLICATION_JSON);
        MvcResult result = mockMvc.perform(requestBuilder).andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
        assertThat(result.getResponse().getContentType()).isEqualTo(MediaType.APPLICATION_JSON_VALUE);
    }

    @Test
    void whenSendFcmNotificationWithMissingBody_thenBadRequestResponseReturned() throws Exception {
        RequestBuilder requestBuilder = MockMvcRequestBuilders
                .post("/v1/fcm/device/{deviceToken}", deviceToken)
                .headers(httpHeaders)
                .contentType(MediaType.APPLICATION_JSON);
        MvcResult result = mockMvc.perform(requestBuilder).andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(result.getResponse().getContentType()).isEqualTo(MediaType.APPLICATION_JSON_VALUE);
    }

    @Test
    void whenSendValidFcmNotification_thenSuccessfulResponseReturned() throws Exception {
        givenFcmNotificationWillBeAccepted();

        ObjectMapper mapper = new ObjectMapper();
        String serializedNotificationRequest = mapper.writeValueAsString(
                new PushNotificationMessage(
                        "encrypted",
                        true));

        RequestBuilder requestBuilder = MockMvcRequestBuilders
                .post("/v1/fcm/device/{deviceToken}", deviceToken)
                .headers(httpHeaders)
                .contentType(MediaType.APPLICATION_JSON)
                .content(serializedNotificationRequest);
        MvcResult mvcResult = mockMvc.perform(requestBuilder)
                .andExpect(request().asyncStarted())
                .andReturn();
        MvcResult asyncResult = mockMvc.perform(asyncDispatch(mvcResult))
                .andReturn();
        assertThat(asyncResult.getResponse().getStatus()).isEqualTo(HttpStatus.OK.value());
        assertThat(asyncResult.getResponse().getContentType()).isEqualTo(MediaType.APPLICATION_JSON_VALUE);
        assertThat(asyncResult.getResponse().getContentAsString()).isEqualTo("{\"wasAccepted\":true,\"isUnregistered\":false}");
    }

    @Test
    void whenSendInvalidFcmNotification_thenBadRequestResponseReturned() throws Exception {
        givenFcmNotificationWillBeRejected();

        ObjectMapper mapper = new ObjectMapper();
        String serializedNotificationRequest = mapper.writeValueAsString(
                new PushNotificationMessage(
                        "encrypted",
                        true));

        RequestBuilder requestBuilder = MockMvcRequestBuilders
                .post("/v1/fcm/device/{deviceToken}", deviceToken)
                .headers(httpHeaders)
                .contentType(MediaType.APPLICATION_JSON)
                .content(serializedNotificationRequest);
        MvcResult mvcResult = mockMvc.perform(requestBuilder)
                .andExpect(request().asyncStarted())
                .andReturn();
        MvcResult asyncResult = mockMvc.perform(asyncDispatch(mvcResult))
                .andReturn();
        assertThat(asyncResult.getResponse().getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(asyncResult.getResponse().getContentType()).isEqualTo(MediaType.APPLICATION_JSON_VALUE);
        assertThat(asyncResult.getResponse().getContentAsString()).isEqualTo("{\"wasAccepted\":false,\"errorCode\":\"UNREGISTERED\",\"isUnregistered\":true}");
    }

    @Test
    void whenFailedToSendNotificationToFcm_thenServerErrorResponseReturned() throws Exception {
        givenFcmIsUnreachable();

        ObjectMapper mapper = new ObjectMapper();
        String serializedNotificationRequest = mapper.writeValueAsString(
                new PushNotificationMessage(
                        "encrypted",
                        true));

        RequestBuilder requestBuilder = MockMvcRequestBuilders
                .post("/v1/fcm/device/{deviceToken}", deviceToken)
                .headers(httpHeaders)
                .contentType(MediaType.APPLICATION_JSON)
                .content(serializedNotificationRequest);
        MvcResult mvcResult = mockMvc.perform(requestBuilder)
                .andExpect(request().asyncStarted())
                .andReturn();
        MvcResult asyncResult = mockMvc.perform(asyncDispatch(mvcResult))
                .andReturn();
        assertThat(asyncResult.getResponse().getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
        assertThat(asyncResult.getResponse().getContentType()).isEqualTo(MediaType.APPLICATION_JSON_VALUE);
        assertThat(asyncResult.getResponse().getContentAsString()).isEmpty();
    }

    @Test
    void whenJsonProcessingExceptionWithPushNotificationResult_thenServerErrorResponseReturned() throws Exception {
        givenFcmNotificationWillBeAccepted();

        when(objectMapper.writeValueAsString(any())).thenThrow(new JsonProcessingException("JsonProcessingException") {});

        ObjectMapper mapper = new ObjectMapper();
        String serializedNotificationRequest = mapper.writeValueAsString(
                new PushNotificationMessage(
                        "encrypted",
                        true));

        RequestBuilder requestBuilder = MockMvcRequestBuilders
                .post("/v1/fcm/device/{deviceToken}", deviceToken)
                .headers(httpHeaders)
                .accept(MediaType.APPLICATION_JSON)
                .contentType(MediaType.APPLICATION_JSON)
                .content(serializedNotificationRequest);
        MvcResult mvcResult = mockMvc.perform(requestBuilder)
                .andExpect(request().asyncStarted())
                .andReturn();
        MvcResult asyncResult = mockMvc.perform(asyncDispatch(mvcResult))
                .andReturn();
        assertThat(asyncResult.getResponse().getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
        assertThat(asyncResult.getResponse().getContentType()).isEqualTo(MediaType.APPLICATION_JSON_VALUE);
        assertThat(asyncResult.getResponse().getContentAsString()).isEmpty();
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

    private void givenFcmIsUnreachable() {
        CompletableFuture<PushNotificationResult> completableFuture = new CompletableFuture<>();
        completableFuture.completeExceptionally(new IOException("Lost connection"));
        when(fcmSender.sendNotification(isA(PushNotificationMessage.class), isA(String.class)))
                .thenReturn(completableFuture);
    }
}
