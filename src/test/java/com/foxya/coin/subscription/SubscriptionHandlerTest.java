package com.foxya.coin.subscription;

import com.fasterxml.jackson.core.type.TypeReference;
import com.foxya.coin.common.HandlerTestBase;
import com.foxya.coin.common.dto.ApiResponse;
import com.foxya.coin.subscription.dto.SubscriptionStatusResponseDto;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(VertxExtension.class)
@Slf4j
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class SubscriptionHandlerTest extends HandlerTestBase {
    
    private final TypeReference<ApiResponse<SubscriptionStatusResponseDto>> refSubscriptionStatus = new TypeReference<>() {};
    
    public SubscriptionHandlerTest() {
        super("/api/v1/subscription");
    }
    
    @Nested
    @DisplayName("프리미엄 구독 상태 조회 테스트")
    class GetSubscriptionStatusTest {
        
        @Test
        @Order(1)
        @DisplayName("성공 - 프리미엄 구독 상태 조회")
        void successGetSubscriptionStatus(VertxTestContext tc) {
            String accessToken = getAccessTokenOfUser(1L);
            
            reqGet(getUrl("/status"))
                .bearerTokenAuthentication(accessToken)
                .send(tc.succeeding(res -> tc.verify(() -> {
                    log.info("Get subscription status response: {}", res.bodyAsJsonObject());
                    SubscriptionStatusResponseDto response = expectSuccessAndGetResponse(res, refSubscriptionStatus);
                    
                    assertThat(response).isNotNull();
                    assertThat(response.getIsSubscribed()).isNotNull();
                    
                    tc.completeNow();
                })));
        }
        
        @Test
        @Order(2)
        @DisplayName("실패 - 인증 없이 조회")
        void failNoAuth(VertxTestContext tc) {
            reqGet(getUrl("/status"))
                .send(tc.succeeding(res -> tc.verify(() -> {
                    expectError(res, 401);
                    tc.completeNow();
                })));
        }
    }
    
    @Nested
    @DisplayName("프리미엄 패키지 구독 테스트")
    class SubscribeTest {
        
        @Test
        @Order(3)
        @DisplayName("성공 - 프리미엄 패키지 구독")
        void successSubscribe(VertxTestContext tc) {
            String accessToken = getAccessTokenOfUser(1L);
            
            JsonObject data = new JsonObject()
                .put("packageType", "PREMIUM")
                .put("months", 1);
            
            reqPost(getUrl("/subscribe"))
                .bearerTokenAuthentication(accessToken)
                .sendJson(data, tc.succeeding(res -> tc.verify(() -> {
                    log.info("Subscribe response: {}", res.bodyAsJsonObject());
                    assertThat(res.statusCode()).isEqualTo(200);
                    tc.completeNow();
                })));
        }
        
        @Test
        @Order(4)
        @DisplayName("실패 - 인증 없이 구독")
        void failNoAuth(VertxTestContext tc) {
            JsonObject data = new JsonObject()
                .put("packageType", "PREMIUM")
                .put("months", 1);
            
            reqPost(getUrl("/subscribe"))
                .sendJson(data, tc.succeeding(res -> tc.verify(() -> {
                    expectError(res, 401);
                    tc.completeNow();
                })));
        }
    }
}

