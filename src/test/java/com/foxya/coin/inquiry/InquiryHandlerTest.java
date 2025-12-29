package com.foxya.coin.inquiry;

import com.fasterxml.jackson.core.type.TypeReference;
import com.foxya.coin.common.HandlerTestBase;
import com.foxya.coin.common.dto.ApiResponse;
import com.foxya.coin.inquiry.dto.InquiryResponseDto;
import com.foxya.coin.inquiry.enums.InquiryStatus;
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
public class InquiryHandlerTest extends HandlerTestBase {
    
    private final TypeReference<ApiResponse<InquiryResponseDto>> refInquiryResponse = new TypeReference<>() {};
    
    public InquiryHandlerTest() {
        super("/api/v1/inquiries");
    }
    
    @Nested
    @DisplayName("문의 제출 테스트")
    class CreateInquiryTest {
        
        @Test
        @Order(1)
        @DisplayName("성공 - 문의 제출 (이메일 설정된 사용자)")
        void successCreateInquiryWithEmail(VertxTestContext tc) {
            String accessToken = getAccessTokenOfUser(1L); // testuser - 이메일 설정됨
            
            JsonObject body = new JsonObject()
                .put("subject", "문의 제목입니다")
                .put("content", "문의 내용입니다. 이것은 테스트 문의입니다.");
            
            reqPost(getUrl("/"))
                .bearerTokenAuthentication(accessToken)
                .sendJson(body, tc.succeeding(res -> tc.verify(() -> {
                    log.info("Create inquiry response: {}", res.bodyAsJsonObject());
                    InquiryResponseDto data = expectSuccessAndGetResponse(res, refInquiryResponse);
                    
                    assertThat(data.getId()).isNotNull();
                    assertThat(data.getSubject()).isEqualTo("문의 제목입니다");
                    assertThat(data.getContent()).isEqualTo("문의 내용입니다. 이것은 테스트 문의입니다.");
                    assertThat(data.getEmail()).isEqualTo("testuser1@example.com");
                    assertThat(data.getStatus()).isEqualTo(InquiryStatus.PENDING);
                    assertThat(data.getCreatedAt()).isNotNull();
                    
                    tc.completeNow();
                })));
        }
        
        @Test
        @Order(2)
        @DisplayName("성공 - 문의 제출 (요청에 이메일 포함)")
        void successCreateInquiryWithEmailInRequest(VertxTestContext tc) {
            String accessToken = getAccessTokenOfUser(1L);
            
            JsonObject body = new JsonObject()
                .put("subject", "이메일 포함 문의")
                .put("content", "요청에 이메일을 포함한 문의입니다.")
                .put("email", "custom@example.com");
            
            reqPost(getUrl("/"))
                .bearerTokenAuthentication(accessToken)
                .sendJson(body, tc.succeeding(res -> tc.verify(() -> {
                    log.info("Create inquiry with email in request response: {}", res.bodyAsJsonObject());
                    InquiryResponseDto data = expectSuccessAndGetResponse(res, refInquiryResponse);
                    
                    assertThat(data.getId()).isNotNull();
                    assertThat(data.getSubject()).isEqualTo("이메일 포함 문의");
                    assertThat(data.getContent()).isEqualTo("요청에 이메일을 포함한 문의입니다.");
                    // 이메일 설정이 있으면 설정된 이메일 사용, 없으면 요청의 이메일 사용
                    assertThat(data.getEmail()).isNotNull();
                    assertThat(data.getStatus()).isEqualTo(InquiryStatus.PENDING);
                    
                    tc.completeNow();
                })));
        }
        
        @Test
        @Order(3)
        @DisplayName("성공 - 문의 제출 (최대 길이)")
        void successCreateInquiryMaxLength(VertxTestContext tc) {
            String accessToken = getAccessTokenOfUser(1L);
            
            // 최대 길이: subject 20자, content 200자
            String maxSubject = "가".repeat(20);
            String maxContent = "나".repeat(200);
            
            JsonObject body = new JsonObject()
                .put("subject", maxSubject)
                .put("content", maxContent);
            
            reqPost(getUrl("/"))
                .bearerTokenAuthentication(accessToken)
                .sendJson(body, tc.succeeding(res -> tc.verify(() -> {
                    log.info("Create inquiry max length response: {}", res.bodyAsJsonObject());
                    InquiryResponseDto data = expectSuccessAndGetResponse(res, refInquiryResponse);
                    
                    assertThat(data.getSubject()).hasSize(20);
                    assertThat(data.getContent()).hasSize(200);
                    
                    tc.completeNow();
                })));
        }
        
        @Test
        @Order(10)
        @DisplayName("실패 - subject 누락")
        void failMissingSubject(VertxTestContext tc) {
            String accessToken = getAccessTokenOfUser(1L);
            
            JsonObject body = new JsonObject()
                .put("content", "문의 내용입니다.");
            
            reqPost(getUrl("/"))
                .bearerTokenAuthentication(accessToken)
                .sendJson(body, tc.succeeding(res -> tc.verify(() -> {
                    log.info("Create inquiry missing subject response: {}", res.bodyAsJsonObject());
                    expectError(res, 400);
                    
                    JsonObject errorBody = res.bodyAsJsonObject();
                    assertThat(errorBody.getString("status")).isEqualTo("ERROR");
                    assertThat(errorBody.getString("message")).contains("필수 항목");
                    
                    tc.completeNow();
                })));
        }
        
        @Test
        @Order(11)
        @DisplayName("실패 - content 누락")
        void failMissingContent(VertxTestContext tc) {
            String accessToken = getAccessTokenOfUser(1L);
            
            JsonObject body = new JsonObject()
                .put("subject", "문의 제목입니다");
            
            reqPost(getUrl("/"))
                .bearerTokenAuthentication(accessToken)
                .sendJson(body, tc.succeeding(res -> tc.verify(() -> {
                    log.info("Create inquiry missing content response: {}", res.bodyAsJsonObject());
                    expectError(res, 400);
                    
                    JsonObject errorBody = res.bodyAsJsonObject();
                    assertThat(errorBody.getString("status")).isEqualTo("ERROR");
                    assertThat(errorBody.getString("message")).contains("필수 항목");
                    
                    tc.completeNow();
                })));
        }
        
        @Test
        @Order(12)
        @DisplayName("실패 - subject 빈 문자열")
        void failEmptySubject(VertxTestContext tc) {
            String accessToken = getAccessTokenOfUser(1L);
            
            JsonObject body = new JsonObject()
                .put("subject", "   ")
                .put("content", "문의 내용입니다.");
            
            reqPost(getUrl("/"))
                .bearerTokenAuthentication(accessToken)
                .sendJson(body, tc.succeeding(res -> tc.verify(() -> {
                    log.info("Create inquiry empty subject response: {}", res.bodyAsJsonObject());
                    expectError(res, 400);
                    
                    JsonObject errorBody = res.bodyAsJsonObject();
                    assertThat(errorBody.getString("status")).isEqualTo("ERROR");
                    assertThat(errorBody.getString("message")).contains("필수 항목");
                    
                    tc.completeNow();
                })));
        }
        
        @Test
        @Order(13)
        @DisplayName("실패 - content 빈 문자열")
        void failEmptyContent(VertxTestContext tc) {
            String accessToken = getAccessTokenOfUser(1L);
            
            JsonObject body = new JsonObject()
                .put("subject", "문의 제목입니다")
                .put("content", "   ");
            
            reqPost(getUrl("/"))
                .bearerTokenAuthentication(accessToken)
                .sendJson(body, tc.succeeding(res -> tc.verify(() -> {
                    log.info("Create inquiry empty content response: {}", res.bodyAsJsonObject());
                    expectError(res, 400);
                    
                    JsonObject errorBody = res.bodyAsJsonObject();
                    assertThat(errorBody.getString("status")).isEqualTo("ERROR");
                    assertThat(errorBody.getString("message")).contains("필수 항목");
                    
                    tc.completeNow();
                })));
        }
        
        @Test
        @Order(14)
        @DisplayName("실패 - subject 길이 초과 (21자)")
        void failSubjectTooLong(VertxTestContext tc) {
            String accessToken = getAccessTokenOfUser(1L);
            
            String tooLongSubject = "가".repeat(21); // 21자
            
            JsonObject body = new JsonObject()
                .put("subject", tooLongSubject)
                .put("content", "문의 내용입니다.");
            
            reqPost(getUrl("/"))
                .bearerTokenAuthentication(accessToken)
                .sendJson(body, tc.succeeding(res -> tc.verify(() -> {
                    log.info("Create inquiry subject too long response: {}", res.bodyAsJsonObject());
                    expectError(res, 400);
                    
                    JsonObject errorBody = res.bodyAsJsonObject();
                    assertThat(errorBody.getString("status")).isEqualTo("ERROR");
                    assertThat(errorBody.getString("message")).contains("20자");
                    
                    tc.completeNow();
                })));
        }
        
        @Test
        @Order(15)
        @DisplayName("실패 - content 길이 초과 (201자)")
        void failContentTooLong(VertxTestContext tc) {
            String accessToken = getAccessTokenOfUser(1L);
            
            String tooLongContent = "나".repeat(201); // 201자
            
            JsonObject body = new JsonObject()
                .put("subject", "문의 제목입니다")
                .put("content", tooLongContent);
            
            reqPost(getUrl("/"))
                .bearerTokenAuthentication(accessToken)
                .sendJson(body, tc.succeeding(res -> tc.verify(() -> {
                    log.info("Create inquiry content too long response: {}", res.bodyAsJsonObject());
                    expectError(res, 400);
                    
                    JsonObject errorBody = res.bodyAsJsonObject();
                    assertThat(errorBody.getString("status")).isEqualTo("ERROR");
                    assertThat(errorBody.getString("message")).contains("200자");
                    
                    tc.completeNow();
                })));
        }
        
        @Test
        @Order(20)
        @DisplayName("실패 - 인증 없이 문의 제출")
        void failNoAuth(VertxTestContext tc) {
            JsonObject body = new JsonObject()
                .put("subject", "문의 제목입니다")
                .put("content", "문의 내용입니다.");
            
            reqPost(getUrl("/"))
                .sendJson(body, tc.succeeding(res -> tc.verify(() -> {
                    expectError(res, 401);
                    tc.completeNow();
                })));
        }
        
        @Test
        @Order(21)
        @DisplayName("실패 - 이메일이 설정되지 않은 경우")
        void failNoEmail(VertxTestContext tc) {
            String accessToken = getAccessTokenOfUser(6L); // no_code_user - 이메일 없음
            
            JsonObject body = new JsonObject()
                .put("subject", "문의 제목입니다")
                .put("content", "문의 내용입니다.");
            // email도 제공하지 않음
            
            reqPost(getUrl("/"))
                .bearerTokenAuthentication(accessToken)
                .sendJson(body, tc.succeeding(res -> tc.verify(() -> {
                    log.info("Create inquiry no email response: {}", res.bodyAsJsonObject());
                    expectError(res, 400);
                    
                    JsonObject errorBody = res.bodyAsJsonObject();
                    assertThat(errorBody.getString("status")).isEqualTo("ERROR");
                    assertThat(errorBody.getString("message")).contains("이메일");
                    
                    tc.completeNow();
                })));
        }
        
        @Test
        @Order(22)
        @DisplayName("성공 - 이메일이 없지만 요청에 이메일 제공")
        void successNoEmailButProvidedInRequest(VertxTestContext tc) {
            String accessToken = getAccessTokenOfUser(6L); // no_code_user - 이메일 없음
            
            JsonObject body = new JsonObject()
                .put("subject", "이메일 제공 문의")
                .put("content", "이메일이 설정되지 않았지만 요청에 이메일을 제공했습니다.")
                .put("email", "provided@example.com");
            
            reqPost(getUrl("/"))
                .bearerTokenAuthentication(accessToken)
                .sendJson(body, tc.succeeding(res -> tc.verify(() -> {
                    log.info("Create inquiry with provided email response: {}", res.bodyAsJsonObject());
                    InquiryResponseDto data = expectSuccessAndGetResponse(res, refInquiryResponse);
                    
                    assertThat(data.getId()).isNotNull();
                    assertThat(data.getEmail()).isEqualTo("provided@example.com");
                    
                    tc.completeNow();
                })));
        }
    }
}

