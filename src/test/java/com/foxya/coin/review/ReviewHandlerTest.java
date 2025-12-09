package com.foxya.coin.review;

import com.fasterxml.jackson.core.type.TypeReference;
import com.foxya.coin.common.HandlerTestBase;
import com.foxya.coin.common.dto.ApiResponse;
import com.foxya.coin.review.dto.ReviewStatusResponseDto;
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
public class ReviewHandlerTest extends HandlerTestBase {
    
    private final TypeReference<ApiResponse<ReviewStatusResponseDto>> refReviewStatus = new TypeReference<>() {};
    
    public ReviewHandlerTest() {
        super("/api/v1/review");
    }
    
    @Nested
    @DisplayName("리뷰 작성 여부 조회 테스트")
    class GetReviewStatusTest {
        
        @Test
        @Order(1)
        @DisplayName("성공 - 리뷰 작성 여부 조회")
        void successGetReviewStatus(VertxTestContext tc) {
            String accessToken = getAccessTokenOfUser(1L);
            
            reqGet(getUrl("/status"))
                .bearerTokenAuthentication(accessToken)
                .send(tc.succeeding(res -> tc.verify(() -> {
                    log.info("Get review status response: {}", res.bodyAsJsonObject());
                    ReviewStatusResponseDto response = expectSuccessAndGetResponse(res, refReviewStatus);
                    
                    assertThat(response).isNotNull();
                    assertThat(response.getHasWrittenReview()).isNotNull();
                    
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
    @DisplayName("리뷰 작성 테스트")
    class WriteReviewTest {
        
        @Test
        @Order(3)
        @DisplayName("성공 - 리뷰 작성")
        void successWriteReview(VertxTestContext tc) {
            String accessToken = getAccessTokenOfUser(1L);
            
            JsonObject data = new JsonObject()
                .put("platform", "GOOGLE_PLAY")
                .put("reviewId", "review_123");
            
            reqPost(getUrl("/write"))
                .bearerTokenAuthentication(accessToken)
                .sendJson(data, tc.succeeding(res -> tc.verify(() -> {
                    log.info("Write review response: {}", res.bodyAsJsonObject());
                    assertThat(res.statusCode()).isEqualTo(200);
                    tc.completeNow();
                })));
        }
        
        @Test
        @Order(4)
        @DisplayName("실패 - 인증 없이 작성")
        void failNoAuth(VertxTestContext tc) {
            JsonObject data = new JsonObject()
                .put("platform", "GOOGLE_PLAY")
                .put("reviewId", "review_123");
            
            reqPost(getUrl("/write"))
                .sendJson(data, tc.succeeding(res -> tc.verify(() -> {
                    expectError(res, 401);
                    tc.completeNow();
                })));
        }
    }
}

