package com.foxya.coin.ranking;

import com.fasterxml.jackson.core.type.TypeReference;
import com.foxya.coin.common.HandlerTestBase;
import com.foxya.coin.common.dto.ApiResponse;
import com.foxya.coin.ranking.dto.CountryRankingResponseDto;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(VertxExtension.class)
@Slf4j
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class RankingHandlerTest extends HandlerTestBase {
    
    private final TypeReference<ApiResponse<CountryRankingResponseDto>> refCountryRanking = new TypeReference<>() {};
    
    public RankingHandlerTest() {
        super("/api/v1/ranking");
    }
    
    @Nested
    @DisplayName("국가별 팀 랭킹 조회 테스트")
    class GetCountryRankingsTest {
        
        @Test
        @Order(1)
        @DisplayName("성공 - 국가별 팀 랭킹 조회 (TODAY)")
        void successGetCountryRankingsToday(VertxTestContext tc) {
            String accessToken = getAccessTokenOfUser(1L);
            
            reqGet(getUrl("/country?period=TODAY"))
                .bearerTokenAuthentication(accessToken)
                .send(tc.succeeding(res -> tc.verify(() -> {
                    log.info("Get country rankings response: {}", res.bodyAsJsonObject());
                    CountryRankingResponseDto response = expectSuccessAndGetResponse(res, refCountryRanking);
                    
                    assertThat(response).isNotNull();
                    assertThat(response.getTop3()).isNotNull();
                    assertThat(response.getRankings()).isNotNull();
                    assertThat(response.getTotalCount()).isNotNull();
                    
                    // Top 3 확인
                    if (response.getTop3() != null && !response.getTop3().isEmpty()) {
                        for (CountryRankingResponseDto.CountryRankingInfo info : response.getTop3()) {
                            assertThat(info.getRank()).isNotNull();
                            assertThat(info.getCountry()).isNotNull();
                            assertThat(info.getCountryName()).isNotNull();
                            assertThat(info.getFlag()).isNotNull();
                            assertThat(info.getTotalMinedCoins()).isNotNull();
                            assertThat(info.getTotalMembers()).isNotNull();
                            assertThat(info.getAggregation()).isNotNull();
                        }
                    }
                    
                    tc.completeNow();
                })));
        }
        
        @Test
        @Order(2)
        @DisplayName("성공 - 국가별 팀 랭킹 조회 (ALL)")
        void successGetCountryRankingsAll(VertxTestContext tc) {
            String accessToken = getAccessTokenOfUser(1L);
            
            reqGet(getUrl("/country?period=ALL"))
                .bearerTokenAuthentication(accessToken)
                .send(tc.succeeding(res -> tc.verify(() -> {
                    log.info("Get country rankings (ALL) response: {}", res.bodyAsJsonObject());
                    CountryRankingResponseDto response = expectSuccessAndGetResponse(res, refCountryRanking);
                    
                    assertThat(response).isNotNull();
                    assertThat(response.getTotalCount()).isNotNull();
                    
                    tc.completeNow();
                })));
        }
        
        @Test
        @Order(3)
        @DisplayName("실패 - 인증 없이 조회")
        void failGetCountryRankingsWithoutAuth(VertxTestContext tc) {
            reqGet(getUrl("/country"))
                .send(tc.succeeding(res -> tc.verify(() -> {
                    assertThat(res.statusCode()).isEqualTo(401);
                    tc.completeNow();
                })));
        }
    }
}

