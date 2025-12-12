package com.foxya.coin.ranking;

import com.fasterxml.jackson.core.type.TypeReference;
import com.foxya.coin.common.HandlerTestBase;
import com.foxya.coin.common.dto.ApiResponse;
import com.foxya.coin.ranking.dto.CountryRankingResponseDto;
import com.foxya.coin.ranking.dto.RankingResponseDto;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(VertxExtension.class)
@Slf4j
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class RankingHandlerTest extends HandlerTestBase {
    
    private final TypeReference<ApiResponse<CountryRankingResponseDto>> refCountryRanking = new TypeReference<>() {};
    private final TypeReference<ApiResponse<RankingResponseDto>> refRanking = new TypeReference<>() {};
    
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
    
    @Nested
    @DisplayName("개인 랭킹 조회 테스트")
    class GetRankingsTest {
        
        @Test
        @Order(4)
        @DisplayName("성공 - 개인 랭킹 조회 (REGIONAL, TODAY)")
        void successGetRankingsRegionalToday(VertxTestContext tc) {
            String accessToken = getAccessTokenOfUser(1L);
            
            reqGet(getUrl("/?scope=REGIONAL&period=TODAY"))
                .bearerTokenAuthentication(accessToken)
                .send(tc.succeeding(res -> tc.verify(() -> {
                    log.info("Get rankings (REGIONAL, TODAY) response: {}", res.bodyAsJsonObject());
                    RankingResponseDto response = expectSuccessAndGetResponse(res, refRanking);
                    
                    assertThat(response).isNotNull();
                    assertThat(response.getTop3()).isNotNull();
                    assertThat(response.getRankings()).isNotNull();
                    assertThat(response.getTotalCount()).isNotNull();
                    assertThat(response.getAggregation()).isNotNull();
                    assertThat(response.getAggregation().getFormula()).isEqualTo("(체굴된 코인+래퍼럴 수익)+(팀원1x20코인)");
                    assertThat(response.getAggregation().getCalculation()).isNotNull();
                    
                    // Top 3 확인
                    if (response.getTop3() != null && !response.getTop3().isEmpty()) {
                        for (RankingResponseDto.RankingInfo info : response.getTop3()) {
                            assertThat(info.getUserId()).isNotNull();
                            assertThat(info.getRank()).isNotNull();
                            assertThat(info.getNickname()).isNotNull();
                            assertThat(info.getLevel()).isNotNull();
                            assertThat(info.getLevelName()).isNotNull();
                            assertThat(info.getTotalAmount()).isNotNull();
                            assertThat(info.getTeamCount()).isNotNull();
                            assertThat(info.getCountry()).isNotNull();
                            assertThat(info.getFlag()).isNotNull();
                        }
                    }
                    
                    tc.completeNow();
                })));
        }
        
        @Test
        @Order(5)
        @DisplayName("성공 - 개인 랭킹 조회 (GLOBAL, ALL)")
        void successGetRankingsGlobalAll(VertxTestContext tc) {
            String accessToken = getAccessTokenOfUser(1L);
            
            reqGet(getUrl("/?scope=GLOBAL&period=ALL"))
                .bearerTokenAuthentication(accessToken)
                .send(tc.succeeding(res -> tc.verify(() -> {
                    log.info("Get rankings (GLOBAL, ALL) response: {}", res.bodyAsJsonObject());
                    RankingResponseDto response = expectSuccessAndGetResponse(res, refRanking);
                    
                    assertThat(response).isNotNull();
                    assertThat(response.getTop3()).isNotNull();
                    assertThat(response.getRankings()).isNotNull();
                    assertThat(response.getTotalCount()).isNotNull();
                    assertThat(response.getAggregation()).isNotNull();
                    
                    tc.completeNow();
                })));
        }
        
        @Test
        @Order(6)
        @DisplayName("성공 - 개인 랭킹 조회 (기본값)")
        void successGetRankingsDefault(VertxTestContext tc) {
            String accessToken = getAccessTokenOfUser(1L);
            
            reqGet(getUrl("/"))
                .bearerTokenAuthentication(accessToken)
                .send(tc.succeeding(res -> tc.verify(() -> {
                    log.info("Get rankings (default) response: {}", res.bodyAsJsonObject());
                    RankingResponseDto response = expectSuccessAndGetResponse(res, refRanking);
                    
                    assertThat(response).isNotNull();
                    assertThat(response.getTop3()).isNotNull();
                    assertThat(response.getRankings()).isNotNull();
                    assertThat(response.getTotalCount()).isNotNull();
                    assertThat(response.getMyRank()).isNotNull(); // 본인 랭킹 확인
                    
                    if (response.getMyRank() != null) {
                        assertThat(response.getMyRank().getUserId()).isEqualTo(1L);
                        assertThat(response.getMyRank().getRank()).isNotNull();
                        assertThat(response.getMyRank().getNickname()).isNotNull();
                        assertThat(response.getMyRank().getTotalAmount()).isNotNull();
                        assertThat(response.getMyRank().getTeamCount()).isNotNull();
                    }
                    
                    tc.completeNow();
                })));
        }
        
        @Test
        @Order(7)
        @DisplayName("성공 - 개인 랭킹 조회 (WEEK)")
        void successGetRankingsWeek(VertxTestContext tc) {
            String accessToken = getAccessTokenOfUser(1L);
            
            reqGet(getUrl("/?scope=REGIONAL&period=WEEK"))
                .bearerTokenAuthentication(accessToken)
                .send(tc.succeeding(res -> tc.verify(() -> {
                    log.info("Get rankings (WEEK) response: {}", res.bodyAsJsonObject());
                    RankingResponseDto response = expectSuccessAndGetResponse(res, refRanking);
                    
                    assertThat(response).isNotNull();
                    assertThat(response.getTop3()).isNotNull();
                    assertThat(response.getRankings()).isNotNull();
                    assertThat(response.getTotalCount()).isNotNull();
                    
                    tc.completeNow();
                })));
        }
        
        @Test
        @Order(8)
        @DisplayName("실패 - 인증 없이 조회")
        void failGetRankingsWithoutAuth(VertxTestContext tc) {
            reqGet(getUrl("/"))
                .send(tc.succeeding(res -> tc.verify(() -> {
                    expectError(res, 401);
                    tc.completeNow();
                })));
        }
    }
}

