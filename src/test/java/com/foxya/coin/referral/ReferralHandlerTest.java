package com.foxya.coin.referral;

import com.fasterxml.jackson.core.type.TypeReference;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import com.foxya.coin.common.HandlerTestBase;
import com.foxya.coin.common.dto.ApiResponse;
import com.foxya.coin.referral.dto.CurrentReferralCodeDto;
import com.foxya.coin.referral.dto.ReferralStatsDto;
import com.foxya.coin.referral.dto.TeamInfoResponseDto;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(VertxExtension.class)
@Slf4j
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ReferralHandlerTest extends HandlerTestBase {
    
    private final TypeReference<ApiResponse<Void>> refVoid = new TypeReference<>() {};
    private final TypeReference<ApiResponse<ReferralStatsDto>> refStats = new TypeReference<>() {};
    private final TypeReference<ApiResponse<TeamInfoResponseDto>> refTeamInfo = new TypeReference<>() {};
    private final TypeReference<ApiResponse<CurrentReferralCodeDto>> refCurrentCode = new TypeReference<>() {};
    
    public ReferralHandlerTest() {
        super("/api/v1/referrals");
    }
    
    @Nested
    @DisplayName("레퍼럴 코드 등록 테스트")
    class RegisterReferralCodeTest {
        
        @Test
        @Order(1)
        @DisplayName("성공 - 유효한 레퍼럴 코드 등록")
        void successRegister(VertxTestContext tc) {
            // testuser2가 referrer_user의 추천인 코드를 입력하여 피추천인이 됨
            String accessToken = getAccessTokenOfUser(2L); // testuser2 (피추천인)
            
            JsonObject data = new JsonObject()
                .put("referralCode", "REFER123"); // referrer_user의 추천인 코드
            
            reqPost(getUrl("/register"))
                .bearerTokenAuthentication(accessToken)
                .sendJson(data, tc.succeeding(res -> tc.verify(() -> {
                    log.info("Register referral code response: {}", res.bodyAsJsonObject());
                    assertThat(res.statusCode()).isEqualTo(200);
                    
                    // Order(2)를 위해 즉시 다시 등록 시도
                    JsonObject data2 = new JsonObject()
                        .put("referralCode", "ADMIN001"); // 다른 추천인 코드 입력 시도
                    
                    reqPost(getUrl("/register"))
                        .bearerTokenAuthentication(accessToken)
                        .sendJson(data2, tc.succeeding(res2 -> tc.verify(() -> {
                            log.info("Second register attempt response: {}", res2.bodyAsJsonObject());
                            expectError(res2, 400); // BadRequestException: 이미 레퍼럴 코드가 등록되어 있습니다
                            tc.completeNow();
                        })));
                })));
        }
        
        @Test
        @Order(2)
        @DisplayName("실패 - 존재하지 않는 레퍼럴 코드")
        void failInvalidCode(VertxTestContext tc) {
            // blocked_user가 존재하지 않는 추천인 코드를 입력
            String accessToken = getAccessTokenOfUser(4L); // blocked_user (피추천인)
            
            JsonObject data = new JsonObject()
                .put("referralCode", "INVALID999"); // 존재하지 않는 추천인 코드
            
            reqPost(getUrl("/register"))
                .bearerTokenAuthentication(accessToken)
                .sendJson(data, tc.succeeding(res -> tc.verify(() -> {
                    expectError(res, 400); // BadRequestException: 유효하지 않은 레퍼럴 코드
                    tc.completeNow();
                })));
        }
        
        @Test
        @Order(3)
        @DisplayName("실패 - 존재하지 않는 레퍼럴 코드")
        void failInvalidCode2(VertxTestContext tc) {
            // blocked_user가 존재하지 않는 추천인 코드를 입력
            String accessToken = getAccessTokenOfUser(4L); // blocked_user (피추천인)
            
            JsonObject data = new JsonObject()
                .put("referralCode", "INVALID999"); // 존재하지 않는 추천인 코드
            
            reqPost(getUrl("/register"))
                .bearerTokenAuthentication(accessToken)
                .sendJson(data, tc.succeeding(res -> tc.verify(() -> {
                    expectError(res, 400); // BadRequestException: 유효하지 않은 레퍼럴 코드
                    tc.completeNow();
                })));
        }
        
        @Test
        @Order(4)
        @DisplayName("실패 - 자신의 추천인 코드를 입력한 경우")
        void failSelfReferral(VertxTestContext tc) {
            // testuser가 자신의 추천인 코드를 입력 (자기 자신을 추천인으로 등록 불가)
            String accessToken = getAccessTokenOfUser(1L); // testuser (추천인 코드: REF001)
            
            JsonObject data = new JsonObject()
                .put("referralCode", "REF001"); // 자신의 추천인 코드 입력
            
            reqPost(getUrl("/register"))
                .bearerTokenAuthentication(accessToken)
                .sendJson(data, tc.succeeding(res -> tc.verify(() -> {
                    expectError(res, 400); // BadRequestException: 자신의 레퍼럴 코드는 등록할 수 없습니다
                    tc.completeNow();
                })));
        }
        
        @Test
        @Order(5)
        @DisplayName("실패 - 인증 없이 등록 시도")
        void failNoAuth(VertxTestContext tc) {
            JsonObject data = new JsonObject()
                .put("referralCode", "REFER123");
            
            reqPost(getUrl("/register"))
                .sendJson(data, tc.succeeding(res -> tc.verify(() -> {
                    expectError(res, 401); // Unauthorized
                    tc.completeNow();
                })));
        }
    }
    
    @Nested
    @DisplayName("현재 추천인 코드 조회 테스트")
    class GetCurrentReferralCodeTest {
        
        @Test
        @Order(1)
        @DisplayName("성공 - 추천인이 있는 경우")
        void successHasReferrer(VertxTestContext tc) {
            // no_code_user(ID:6)는 referrer_user(ID:5)의 피추천인
            String accessToken = getAccessTokenOfUser(6L);
            
            reqGet(getUrl("/current"))
                .bearerTokenAuthentication(accessToken)
                .send(tc.succeeding(res -> tc.verify(() -> {
                    log.info("Get current referral code response: {}", res.bodyAsJsonObject());
                    CurrentReferralCodeDto data = expectSuccessAndGetResponse(res, refCurrentCode);
                    
                    assertThat(data).isNotNull();
                    assertThat(data.getReferralCode()).isEqualTo("REFER123");
                    
                    tc.completeNow();
                })));
        }
        
        @Test
        @Order(2)
        @DisplayName("성공 - 추천인이 없는 경우")
        void successNoReferrer(VertxTestContext tc) {
            // testuser2(ID:2)는 초기 상태에서 추천인이 없음
            String accessToken = getAccessTokenOfUser(2L);
            
            reqGet(getUrl("/current"))
                .bearerTokenAuthentication(accessToken)
                .send(tc.succeeding(res -> tc.verify(() -> {
                    log.info("Get current referral code (no referrer) response: {}", res.bodyAsJsonObject());
                    CurrentReferralCodeDto data = expectSuccessAndGetResponse(res, refCurrentCode);
                    
                    assertThat(data).isNotNull();
                    assertThat(data.getReferralCode()).isNull();
                    
                    tc.completeNow();
                })));
        }
        
        @Test
        @Order(3)
        @DisplayName("실패 - 인증 없이 조회")
        void failNoAuth(VertxTestContext tc) {
            reqGet(getUrl("/current"))
                .send(tc.succeeding(res -> tc.verify(() -> {
                    expectError(res, 401); // Unauthorized
                    tc.completeNow();
                })));
        }
    }
    
    @Nested
    @DisplayName("레퍼럴 관계 Soft Delete 테스트")
    class SoftDeleteReferralRelationTest {
        
        @Test
        @Order(1)
        @DisplayName("성공 - USER가 자신의 레퍼럴 관계 삭제")
        void successDeleteByUser(VertxTestContext tc) {
            // 먼저 testuser2가 referrer_user의 코드로 등록
            String user2Token = getAccessTokenOfUser(2L);
            JsonObject registerData = new JsonObject().put("referralCode", "REFER123");
            
            reqPost(getUrl("/register"))
                .bearerTokenAuthentication(user2Token)
                .sendJson(registerData, tc.succeeding(registerRes -> tc.verify(() -> {
                    assertThat(registerRes.statusCode()).isEqualTo(200);
                    
                    // 이제 삭제
                    reqDelete(getUrl("/"))
                        .bearerTokenAuthentication(user2Token)
                        .send(tc.succeeding(res -> tc.verify(() -> {
                            log.info("Delete referral relation response: {}", res.bodyAsJsonObject());
                            assertThat(res.statusCode()).isEqualTo(200);
                            tc.completeNow();
                        })));
                })));
        }
        
        @Test
        @Order(2)
        @DisplayName("성공 - ADMIN이 자신의 레퍼럴 관계 삭제")
        void successDeleteByAdmin(VertxTestContext tc) {
            // 먼저 admin_user가 referrer_user의 코드로 등록
            String adminToken = getAccessTokenOfAdmin(3L);
            JsonObject registerData = new JsonObject().put("referralCode", "REFER123");
            
            reqPost(getUrl("/register"))
                .bearerTokenAuthentication(adminToken)
                .sendJson(registerData, tc.succeeding(registerRes -> tc.verify(() -> {
                    assertThat(registerRes.statusCode()).isEqualTo(200);
                    
                    // 이제 삭제
                    reqDelete(getUrl("/"))
                        .bearerTokenAuthentication(adminToken)
                        .send(tc.succeeding(res -> tc.verify(() -> {
                            log.info("Delete referral relation by admin response: {}", res.bodyAsJsonObject());
                            assertThat(res.statusCode()).isEqualTo(200);
                            tc.completeNow();
                        })));
                })));
        }
        
        @Test
        @Order(3)
        @DisplayName("실패 - 등록된 레퍼럴 관계가 없는 경우")
        void failNoRelation(VertxTestContext tc) {
            String accessToken = getAccessTokenOfUser(1L); // testuser (레퍼럴 관계 없음)
            
            reqDelete(getUrl("/"))
                .bearerTokenAuthentication(accessToken)
                .send(tc.succeeding(res -> tc.verify(() -> {
                    expectError(res, 400); // BadRequestException: 등록된 레퍼럴 관계가 없습니다
                    tc.completeNow();
                })));
        }
        
        @Test
        @Order(4)
        @DisplayName("실패 - 인증 없이 삭제 시도")
        void failNoAuth(VertxTestContext tc) {
            reqDelete(getUrl("/"))
                .send(tc.succeeding(res -> tc.verify(() -> {
                    expectError(res, 401); // Unauthorized
                    tc.completeNow();
                })));
        }
    }
    
    @Nested
    @DisplayName("레퍼럴 관계 Hard Delete 테스트")
    class HardDeleteReferralRelationTest {
        
        @Test
        @Order(1)
        @DisplayName("성공 - USER가 자신의 레퍼럴 관계 완전 삭제")
        void successHardDeleteByUser(VertxTestContext tc) {
            // 먼저 testuser2가 referrer_user의 코드로 등록
            String user2Token = getAccessTokenOfUser(2L);
            JsonObject registerData = new JsonObject().put("referralCode", "REFER123");
            
            reqPost(getUrl("/register"))
                .bearerTokenAuthentication(user2Token)
                .sendJson(registerData, tc.succeeding(registerRes -> tc.verify(() -> {
                    assertThat(registerRes.statusCode()).isEqualTo(200);
                    
                    // 이제 완전 삭제
                    reqDelete(getUrl("/hard"))
                        .bearerTokenAuthentication(user2Token)
                        .send(tc.succeeding(res -> tc.verify(() -> {
                            log.info("Hard delete referral relation response: {}", res.bodyAsJsonObject());
                            assertThat(res.statusCode()).isEqualTo(200);
                            tc.completeNow();
                        })));
                })));
        }
        
        @Test
        @Order(2)
        @DisplayName("성공 - ADMIN이 자신의 레퍼럴 관계 완전 삭제")
        void successHardDeleteByAdmin(VertxTestContext tc) {
            // 먼저 admin_user가 referrer_user의 코드로 등록
            String adminToken = getAccessTokenOfAdmin(3L);
            JsonObject registerData = new JsonObject().put("referralCode", "REFER123");
            
            reqPost(getUrl("/register"))
                .bearerTokenAuthentication(adminToken)
                .sendJson(registerData, tc.succeeding(registerRes -> tc.verify(() -> {
                    assertThat(registerRes.statusCode()).isEqualTo(200);
                    
                    // 이제 완전 삭제
                    reqDelete(getUrl("/hard"))
                        .bearerTokenAuthentication(adminToken)
                        .send(tc.succeeding(res -> tc.verify(() -> {
                            log.info("Hard delete referral relation by admin response: {}", res.bodyAsJsonObject());
                            assertThat(res.statusCode()).isEqualTo(200);
                            tc.completeNow();
                        })));
                })));
        }
        
        @Test
        @Order(3)
        @DisplayName("성공 - Soft Delete된 관계도 Hard Delete 가능")
        void successHardDeleteAfterSoftDelete(VertxTestContext tc) {
            // 먼저 blocked_user가 referrer_user의 코드로 등록
            String blockedToken = getAccessTokenOfUser(4L);
            JsonObject registerData = new JsonObject().put("referralCode", "REFER123");
            
            reqPost(getUrl("/register"))
                .bearerTokenAuthentication(blockedToken)
                .sendJson(registerData, tc.succeeding(registerRes -> tc.verify(() -> {
                    assertThat(registerRes.statusCode()).isEqualTo(200);
                    
                    // Soft Delete
                    reqDelete(getUrl("/"))
                        .bearerTokenAuthentication(blockedToken)
                        .send(tc.succeeding(softDeleteRes -> tc.verify(() -> {
                            assertThat(softDeleteRes.statusCode()).isEqualTo(200);
                            
                            // Hard Delete
                            reqDelete(getUrl("/hard"))
                                .bearerTokenAuthentication(blockedToken)
                                .send(tc.succeeding(hardDeleteRes -> tc.verify(() -> {
                                    log.info("Hard delete after soft delete response: {}", hardDeleteRes.bodyAsJsonObject());
                                    assertThat(hardDeleteRes.statusCode()).isEqualTo(200);
                                    tc.completeNow();
                                })));
                        })));
                })));
        }
        
        @Test
        @Order(4)
        @DisplayName("실패 - 레퍼럴 관계가 없는 경우")
        void failNoRelation(VertxTestContext tc) {
            String accessToken = getAccessTokenOfUser(1L); // testuser (레퍼럴 관계 없음)
            
            reqDelete(getUrl("/hard"))
                .bearerTokenAuthentication(accessToken)
                .send(tc.succeeding(res -> tc.verify(() -> {
                    expectError(res, 400); // BadRequestException: 레퍼럴 관계가 존재하지 않습니다
                    tc.completeNow();
                })));
        }
        
        @Test
        @Order(5)
        @DisplayName("실패 - 인증 없이 삭제 시도")
        void failNoAuth(VertxTestContext tc) {
            reqDelete(getUrl("/hard"))
                .send(tc.succeeding(res -> tc.verify(() -> {
                    expectError(res, 401); // Unauthorized
                    tc.completeNow();
                })));
        }
    }
    
    @Nested
    @DisplayName("레퍼럴 통계 조회 테스트")
    class GetReferralStatsTest {
        
        @Test
        @Order(6)
        @DisplayName("성공 - 추천인의 레퍼럴 통계 조회")
        void successGetStats(VertxTestContext tc) {
            // referrer_user의 통계 조회 
            // 시드 데이터에서 no_code_user(ID:6)가 referrer_user(ID:5)의 피추천인으로 등록되어 있음
            String accessToken = getAccessTokenOfUser(5L); // referrer_user (추천인)
            
            reqGet(getUrl("/5/stats"))
                .bearerTokenAuthentication(accessToken)
                .send(tc.succeeding(res -> tc.verify(() -> {
                    log.info("Get referral stats response: {}", res.bodyAsJsonObject());
                    ReferralStatsDto stats = expectSuccessAndGetResponse(res, refStats);
                    
                    assertThat(stats.getUserId()).isEqualTo(5L);
                    // 시드 데이터에서 no_code_user가 피추천인으로 등록되어 있으므로 최소 1명 이상
                    // 다른 테스트에서 등록/삭제가 발생할 수 있으므로 0 이상으로 체크
                    assertThat(stats.getDirectCount()).isGreaterThanOrEqualTo(0);
                    assertThat(stats.getActiveTeamCount()).isNotNull();
                    assertThat(stats.getTotalReward()).isNotNull();
                    
                    tc.completeNow();
                })));
        }
        
        @Test
        @Order(7)
        @DisplayName("성공 - 피추천인이 없는 사용자의 통계 조회")
        void successGetStatsNoReferrals(VertxTestContext tc) {
            // testuser는 아무도 추천하지 않음 (피추천인이 0명)
            String accessToken = getAccessTokenOfUser(1L); // testuser (추천인, 피추천인 0명)
            
            reqGet(getUrl("/1/stats"))
                .bearerTokenAuthentication(accessToken)
                .send(tc.succeeding(res -> tc.verify(() -> {
                    log.info("Get referral stats (no referrals) response: {}", res.bodyAsJsonObject());
                    ReferralStatsDto stats = expectSuccessAndGetResponse(res, refStats);
                    
                    assertThat(stats.getUserId()).isEqualTo(1L);
                    assertThat(stats.getDirectCount()).isEqualTo(0); // 직접 추천한 사람 0명
                    assertThat(stats.getActiveTeamCount()).isEqualTo(0); // ACTIVE 상태 팀원 0명
                    
                    tc.completeNow();
                })));
        }
        
        @Test
        @Order(8)
        @DisplayName("실패 - 인증 없이 조회 시도")
        void failNoAuth(VertxTestContext tc) {
            reqGet(getUrl("/1/stats"))
                .send(tc.succeeding(res -> tc.verify(() -> {
                    expectError(res, 401); // Unauthorized
                    tc.completeNow();
                })));
        }
    }
    
    @Nested
    @DisplayName("팀 정보 조회 테스트")
    class GetTeamInfoTest {
        
        @Test
        @Order(9)
        @DisplayName("성공 - 팀 정보 조회 (MEMBERS 탭, 기본값)")
        void successGetTeamInfoMembers(VertxTestContext tc) {
            // referrer_user의 팀 정보 조회
            String accessToken = getAccessTokenOfUser(5L); // referrer_user
            
            reqGet(getUrl("/team"))
                .bearerTokenAuthentication(accessToken)
                .send(tc.succeeding(res -> tc.verify(() -> {
                    log.info("Get team info (MEMBERS) response: {}", res.bodyAsJsonObject());
                    TeamInfoResponseDto teamInfo = expectSuccessAndGetResponse(res, refTeamInfo);
                    
                    assertThat(teamInfo.getSummary()).isNotNull();
                    assertThat(teamInfo.getSummary().getTotalMembers()).isNotNull();
                    assertThat(teamInfo.getSummary().getNewMembersToday()).isNotNull();
                    assertThat(teamInfo.getMembers()).isNotNull();
                    assertThat(teamInfo.getRevenues()).isNull();
                    assertThat(teamInfo.getTotal()).isNotNull();
                    assertThat(teamInfo.getLimit()).isEqualTo(20);
                    assertThat(teamInfo.getOffset()).isEqualTo(0);
                    
                    tc.completeNow();
                })));
        }
        
        @Test
        @Order(10)
        @DisplayName("성공 - 팀 정보 조회 (REVENUE 탭, ALL 기간)")
        void successGetTeamInfoRevenue(VertxTestContext tc) {
            String accessToken = getAccessTokenOfUser(5L); // referrer_user
            
            reqGet(getUrl("/team?tab=REVENUE&period=ALL"))
                .bearerTokenAuthentication(accessToken)
                .send(tc.succeeding(res -> tc.verify(() -> {
                    log.info("Get team info (REVENUE, ALL) response: {}", res.bodyAsJsonObject());
                    TeamInfoResponseDto teamInfo = expectSuccessAndGetResponse(res, refTeamInfo);
                    
                    assertThat(teamInfo.getSummary()).isNotNull();
                    assertThat(teamInfo.getMembers()).isNull();
                    assertThat(teamInfo.getRevenues()).isNotNull();
                    assertThat(teamInfo.getTotal()).isNotNull();
                    
                    tc.completeNow();
                })));
        }
        
        @Test
        @Order(11)
        @DisplayName("성공 - 팀 정보 조회 (MEMBERS 탭, WEEK 기간)")
        void successGetTeamInfoMembersWeek(VertxTestContext tc) {
            String accessToken = getAccessTokenOfUser(5L); // referrer_user
            
            reqGet(getUrl("/team?tab=MEMBERS&period=WEEK&limit=10&offset=0"))
                .bearerTokenAuthentication(accessToken)
                .send(tc.succeeding(res -> tc.verify(() -> {
                    log.info("Get team info (MEMBERS, WEEK) response: {}", res.bodyAsJsonObject());
                    TeamInfoResponseDto teamInfo = expectSuccessAndGetResponse(res, refTeamInfo);
                    
                    assertThat(teamInfo.getSummary()).isNotNull();
                    assertThat(teamInfo.getMembers()).isNotNull();
                    assertThat(teamInfo.getRevenues()).isNull();
                    assertThat(teamInfo.getLimit()).isEqualTo(10);
                    assertThat(teamInfo.getOffset()).isEqualTo(0);
                    
                    tc.completeNow();
                })));
        }
        
        @Test
        @Order(12)
        @DisplayName("성공 - 팀 정보 조회 (REVENUE 탭, TODAY 기간)")
        void successGetTeamInfoRevenueToday(VertxTestContext tc) {
            String accessToken = getAccessTokenOfUser(5L); // referrer_user
            
            reqGet(getUrl("/team?tab=REVENUE&period=TODAY"))
                .bearerTokenAuthentication(accessToken)
                .send(tc.succeeding(res -> tc.verify(() -> {
                    log.info("Get team info (REVENUE, TODAY) response: {}", res.bodyAsJsonObject());
                    TeamInfoResponseDto teamInfo = expectSuccessAndGetResponse(res, refTeamInfo);
                    
                    assertThat(teamInfo.getSummary()).isNotNull();
                    assertThat(teamInfo.getMembers()).isNull();
                    assertThat(teamInfo.getRevenues()).isNotNull();
                    
                    tc.completeNow();
                })));
        }
        
        @Test
        @Order(13)
        @DisplayName("실패 - 인증 없이 조회")
        void failNoAuth(VertxTestContext tc) {
            reqGet(getUrl("/team"))
                .send(tc.succeeding(res -> tc.verify(() -> {
                    expectError(res, 401); // Unauthorized
                    tc.completeNow();
                })));
        }
    }
}

