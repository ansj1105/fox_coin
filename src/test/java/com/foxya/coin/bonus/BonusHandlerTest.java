package com.foxya.coin.bonus;

import com.fasterxml.jackson.core.type.TypeReference;
import com.foxya.coin.common.HandlerTestBase;
import com.foxya.coin.common.dto.ApiResponse;
import com.foxya.coin.bonus.dto.BonusEfficiencyResponseDto;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(VertxExtension.class)
@Slf4j
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class BonusHandlerTest extends HandlerTestBase {
    
    private final TypeReference<ApiResponse<BonusEfficiencyResponseDto>> refBonusEfficiency = new TypeReference<>() {};
    
    public BonusHandlerTest() {
        super("/api/v1/bonus");
    }
    
    @Nested
    @DisplayName("보너스 채굴 효율 조회 테스트")
    class GetBonusEfficiencyTest {
        
        @Test
        @Order(1)
        @DisplayName("성공 - 보너스 채굴 효율 조회")
        void successGetBonusEfficiency(VertxTestContext tc) {
            String accessToken = getAccessTokenOfUser(1L);
            
            reqGet(getUrl("/efficiency"))
                .bearerTokenAuthentication(accessToken)
                .send(tc.succeeding(res -> tc.verify(() -> {
                    log.info("Get bonus efficiency response: {}", res.bodyAsJsonObject());
                    BonusEfficiencyResponseDto response = expectSuccessAndGetResponse(res, refBonusEfficiency);
                    
                    assertThat(response).isNotNull();
                    assertThat(response.getTotalEfficiency()).isNotNull();
                    assertThat(response.getBonuses()).isNotNull();
                    assertThat(response.getBonuses().size()).isGreaterThan(0);
                    
                    // 각 보너스 타입 확인
                    boolean hasSocialLink = response.getBonuses().stream()
                        .anyMatch(b -> "SOCIAL_LINK".equals(b.getType()));
                    assertThat(hasSocialLink).isTrue();
                    
                    tc.completeNow();
                })));
        }
        
        @Test
        @Order(2)
        @DisplayName("실패 - 인증 없이 조회")
        void failNoAuth(VertxTestContext tc) {
            reqGet(getUrl("/efficiency"))
                .send(tc.succeeding(res -> tc.verify(() -> {
                    expectError(res, 401);
                    tc.completeNow();
                })));
        }
    }
}

