package com.foxya.coin.transfer;

import com.foxya.coin.transfer.dto.ExternalTransferRequestDto;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KorionWithdrawalBridgeClientTest {

    @Test
    void retriesOnceOnRetryableStatusThenSucceeds() {
        WebClient webClient = mock(WebClient.class);
        @SuppressWarnings("unchecked")
        HttpRequest<Buffer> httpRequest = mock(HttpRequest.class);
        @SuppressWarnings("unchecked")
        HttpResponse<Buffer> failedResponse = mock(HttpResponse.class);
        @SuppressWarnings("unchecked")
        HttpResponse<Buffer> successResponse = mock(HttpResponse.class);

        when(webClient.postAbs("http://korion-service:3000/api/withdrawals")).thenReturn(httpRequest);
        when(httpRequest.timeout(anyLong())).thenReturn(httpRequest);
        when(httpRequest.putHeader(eq("Content-Type"), eq("application/json"))).thenReturn(httpRequest);
        when(httpRequest.putHeader(eq("Idempotency-Key"), eq("transfer-1"))).thenReturn(httpRequest);
        when(httpRequest.sendJsonObject(any(JsonObject.class)))
            .thenReturn(Future.succeededFuture(failedResponse))
            .thenReturn(Future.succeededFuture(successResponse));

        when(failedResponse.statusCode()).thenReturn(503);
        when(failedResponse.bodyAsString()).thenReturn("temporarily unavailable");

        when(successResponse.statusCode()).thenReturn(201);
        when(successResponse.bodyAsJsonObject()).thenReturn(
            new JsonObject().put("withdrawal", new JsonObject().put("withdrawalId", "wd-1"))
        );

        KorionWithdrawalBridgeClient client = new KorionWithdrawalBridgeClient(
            webClient,
            "http://korion-service:3000",
            "/api/withdrawals",
            Set.of("KORI"),
            null
        );

        String result = client.requestWithdrawal(1L, "transfer-1", request(), "127.0.0.1").result();

        assertThat(result).isEqualTo("wd-1");
        verify(httpRequest, times(2)).sendJsonObject(any(JsonObject.class));
    }

    @Test
    void retriesOnceOnRetryableTransportErrorThenSucceeds() {
        WebClient webClient = mock(WebClient.class);
        @SuppressWarnings("unchecked")
        HttpRequest<Buffer> httpRequest = mock(HttpRequest.class);
        @SuppressWarnings("unchecked")
        HttpResponse<Buffer> successResponse = mock(HttpResponse.class);

        when(webClient.postAbs("http://korion-service:3000/api/withdrawals")).thenReturn(httpRequest);
        when(httpRequest.timeout(anyLong())).thenReturn(httpRequest);
        when(httpRequest.putHeader(eq("Content-Type"), eq("application/json"))).thenReturn(httpRequest);
        when(httpRequest.putHeader(eq("Idempotency-Key"), eq("transfer-1"))).thenReturn(httpRequest);
        when(httpRequest.sendJsonObject(any(JsonObject.class)))
            .thenReturn(Future.failedFuture("Connection refused"))
            .thenReturn(Future.succeededFuture(successResponse));

        when(successResponse.statusCode()).thenReturn(200);
        when(successResponse.bodyAsJsonObject()).thenReturn(
            new JsonObject().put("withdrawal", new JsonObject().put("withdrawalId", "wd-2"))
        );

        KorionWithdrawalBridgeClient client = new KorionWithdrawalBridgeClient(
            webClient,
            "http://korion-service:3000",
            "/api/withdrawals",
            Set.of("KORI"),
            null
        );

        String result = client.requestWithdrawal(1L, "transfer-1", request(), "127.0.0.1").result();

        assertThat(result).isEqualTo("wd-2");
        verify(httpRequest, times(2)).sendJsonObject(any(JsonObject.class));
    }

    private ExternalTransferRequestDto request() {
        return ExternalTransferRequestDto.builder()
            .toAddress("TYteNy9PWTg9U68dnjwNnosQC9FP1Hgs1Z")
            .currencyCode("KORI")
            .amount(new BigDecimal("12.34"))
            .chain("TRON")
            .build();
    }
}
