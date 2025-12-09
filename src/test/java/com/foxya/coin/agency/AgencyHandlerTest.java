package com.foxya.coin.agency;

import com.fasterxml.jackson.core.type.TypeReference;
import com.foxya.coin.common.HandlerTestBase;
import com.foxya.coin.common.dto.ApiResponse;
import com.foxya.coin.agency.dto.AgencyStatusResponseDto;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(VertxExtension.class)
@Slf4j
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class AgencyHandlerTest extends HandlerTestBase {
    
    private final TypeReference<ApiResponse<AgencyStatusResponseDto>> refAgencyStatus = new TypeReference<>() {};
    
    public AgencyHandlerTest() {
        super("/api/v1/agency");
    }
    
    @Nested
    @DisplayName("에이전시 가입 상태 조회 테스트")
    class GetAgencyStatusTest {
        
        @Test
        @Order(1)
        @DisplayName("성공 - 에이전시 가입 상태 조회")
        void successGetAgencyStatus(VertxTestContext tc) {
            String accessToken = getAccessTokenOfUser(1L);
            
            reqGet(getUrl("/status"))
                .bearerTokenAuthentication(accessToken)
                .send(tc.succeeding(res -> tc.verify(() -> {
                    log.info("Get agency status response: {}", res.bodyAsJsonObject());
                    AgencyStatusResponseDto response = expectSuccessAndGetResponse(res, refAgencyStatus);
                    
                    assertThat(response).isNotNull();
                    assertThat(response.getIsJoined()).isNotNull();
                    
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
}

