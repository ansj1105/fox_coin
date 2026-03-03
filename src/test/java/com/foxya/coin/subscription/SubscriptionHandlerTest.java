package com.foxya.coin.subscription;

import com.fasterxml.jackson.core.type.TypeReference;
import com.foxya.coin.common.HandlerTestBase;
import com.foxya.coin.common.dto.ApiResponse;
import com.foxya.coin.subscription.dto.SubscriptionPlanResponseDto;
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
    private final TypeReference<ApiResponse<SubscriptionPlanResponseDto>> refSubscriptionPlans = new TypeReference<>() {};
    
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
                    assertThat(response.getAdFree()).isNotNull();
                    assertThat(response.getAutoBoostMining()).isNotNull();
                    assertThat(response.getReferralReregisterUnlimited()).isNotNull();
                    assertThat(response.getFullMiningHistoryAccess()).isNotNull();
                    assertThat(response.getProfileImageUnlock()).isNotNull();
                    assertThat(response.getMiningEfficiencyBonusPercent()).isNotNull();
                    
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
    @DisplayName("VIP 구독 플랜 조회 테스트")
    class GetSubscriptionPlansTest {

        @Test
        @Order(3)
        @DisplayName("성공 - VIP 구독 플랜 조회")
        void successGetPlans(VertxTestContext tc) {
            String accessToken = getAccessTokenOfUser(1L);

            reqGet(getUrl("/plans"))
                .bearerTokenAuthentication(accessToken)
                .send(tc.succeeding(res -> tc.verify(() -> {
                    SubscriptionPlanResponseDto response = expectSuccessAndGetResponse(res, refSubscriptionPlans);
                    assertThat(response).isNotNull();
                    assertThat(response.getPlans()).isNotNull();
                    assertThat(response.getPlans().size()).isGreaterThanOrEqualTo(5);
                    assertThat(response.getPlans().stream().anyMatch(plan -> "VIP_PASS_7D".equals(plan.getPlanCode()))).isTrue();
                    assertThat(response.getPlans().stream().anyMatch(plan -> "VIP_12M".equals(plan.getPlanCode()))).isTrue();
                    assertThat(response.getPlans().stream()
                        .filter(plan -> "VIP_1M".equals(plan.getPlanCode()))
                        .findFirst()
                        .map(SubscriptionPlanResponseDto.PlanItem::getProfileImageUnlock))
                        .contains(true);
                    tc.completeNow();
                })));
        }

        @Test
        @Order(4)
        @DisplayName("실패 - 인증 없이 플랜 조회")
        void failGetPlansNoAuth(VertxTestContext tc) {
            reqGet(getUrl("/plans"))
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
        @Order(5)
        @DisplayName("성공 - 프리미엄 패키지 구독")
        void successSubscribe(VertxTestContext tc) {
            String accessToken = getAccessTokenOfUser(1L);
            
            JsonObject data = new JsonObject()
                .put("planCode", "VIP_PASS_7D");
            
            reqPost(getUrl("/subscribe"))
                .bearerTokenAuthentication(accessToken)
                .sendJson(data, tc.succeeding(res -> tc.verify(() -> {
                    log.info("Subscribe response: {}", res.bodyAsJsonObject());
                    assertThat(res.statusCode()).isEqualTo(200);
                    tc.completeNow();
                })));
        }
        
        @Test
        @Order(6)
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
