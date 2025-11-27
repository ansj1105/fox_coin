package com.foxya.coin.wallet;

import com.fasterxml.jackson.core.type.TypeReference;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import com.foxya.coin.common.HandlerTestBase;
import com.foxya.coin.common.dto.ApiResponse;
import com.foxya.coin.wallet.entities.Wallet;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(VertxExtension.class)
@Slf4j
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class WalletHandlerTest extends HandlerTestBase {
    
    private final TypeReference<ApiResponse<List<Wallet>>> refWalletList = new TypeReference<>() {};
    
    public WalletHandlerTest() {
        super("/api/v1/wallets");
    }
    
    @Nested
    @DisplayName("내 지갑 조회 테스트")
    class GetMyWalletsTest {
        
        @Test
        @Order(1)
        @DisplayName("성공 - USER 권한으로 조회")
        void successAsUser(VertxTestContext tc) {
            String accessToken = getAccessTokenOfUser(1L);
            
            reqGet(getUrl("/my"))
                .bearerTokenAuthentication(accessToken)
                .send(tc.succeeding(res -> tc.verify(() -> {
                    log.info("Get wallets response: {}", res.bodyAsJsonObject());
                    List<Wallet> wallets = expectSuccessAndGetResponse(res, refWalletList);
                    
                    assertThat(wallets).isNotNull();
                    log.info("User has {} wallets", wallets.size());
                    
                    tc.completeNow();
                })));
        }
        
        @Test
        @Order(2)
        @DisplayName("성공 - ADMIN 권한으로 조회")
        void successAsAdmin(VertxTestContext tc) {
            String accessToken = getAccessTokenOfAdmin(1L);
            
            reqGet(getUrl("/my"))
                .bearerTokenAuthentication(accessToken)
                .send(tc.succeeding(res -> tc.verify(() -> {
                    log.info("Get wallets response: {}", res.bodyAsJsonObject());
                    List<Wallet> wallets = expectSuccessAndGetResponse(res, refWalletList);
                    
                    assertThat(wallets).isNotNull();
                    
                    tc.completeNow();
                })));
        }
        
        @Test
        @Order(3)
        @DisplayName("실패 - 인증 없이 조회")
        void failNoAuth(VertxTestContext tc) {
            reqGet(getUrl("/my"))
                .send(tc.succeeding(res -> tc.verify(() -> {
                    expectError(res, 401);
                    tc.completeNow();
                })));
        }
    }
}

