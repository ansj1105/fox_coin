package com.foxya.coin.user;

import com.fasterxml.jackson.core.type.TypeReference;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import com.foxya.coin.common.HandlerTestBase;
import com.foxya.coin.common.dto.ApiResponse;
import com.foxya.coin.user.entities.User;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(VertxExtension.class)
@Slf4j
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class UserHandlerTest extends HandlerTestBase {
    
    private final TypeReference<ApiResponse<User>> refUser = new TypeReference<>() {};
    
    public UserHandlerTest() {
        super("/api/v1/users");
    }
    
    @Nested
    @DisplayName("사용자 조회 테스트")
    class GetUserTest {
        
        @Test
        @Order(1)
        @DisplayName("성공 - ADMIN 권한으로 조회")
        void successAsAdmin(VertxTestContext tc) {
            String accessToken = getAccessTokenOfAdmin(1L);
            
            reqGet(getUrl("/1"))
                .bearerTokenAuthentication(accessToken)
                .send(tc.succeeding(res -> tc.verify(() -> {
                    log.info("Get user response: {}", res.bodyAsJsonObject());
                    User user = expectSuccessAndGetResponse(res, refUser);
                    
                    assertThat(user.getId()).isEqualTo(1L);
                    assertThat(user.getLoginId()).isNotNull();
                    
                    tc.completeNow();
                })));
        }
        
        @Test
        @Order(2)
        @DisplayName("성공 - USER 권한으로 조회")
        void successAsUser(VertxTestContext tc) {
            String accessToken = getAccessTokenOfUser(1L);
            
            reqGet(getUrl("/1"))
                .bearerTokenAuthentication(accessToken)
                .send(tc.succeeding(res -> tc.verify(() -> {
                    log.info("Get user response: {}", res.bodyAsJsonObject());
                    User user = expectSuccessAndGetResponse(res, refUser);
                    
                    assertThat(user.getId()).isEqualTo(1L);
                    
                    tc.completeNow();
                })));
        }
        
        @Test
        @Order(3)
        @DisplayName("실패 - 인증 없이 조회")
        void failNoAuth(VertxTestContext tc) {
            reqGet(getUrl("/1"))
                .send(tc.succeeding(res -> tc.verify(() -> {
                    expectError(res, 401);
                    tc.completeNow();
                })));
        }
    }
}

