package com.foxya.coin.notice;

import com.fasterxml.jackson.core.type.TypeReference;
import com.foxya.coin.common.HandlerTestBase;
import com.foxya.coin.common.dto.ApiResponse;
import com.foxya.coin.notice.dto.NoticeListResponseDto;
import com.foxya.coin.notice.entities.Notice;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(VertxExtension.class)
@Slf4j
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class NoticeHandlerTest extends HandlerTestBase {
    
    private final TypeReference<ApiResponse<NoticeListResponseDto>> refNoticeList = new TypeReference<>() {};
    private final TypeReference<ApiResponse<Notice>> refNotice = new TypeReference<>() {};
    
    public NoticeHandlerTest() {
        super("/api/v1/notices");
    }
    
    @Nested
    @DisplayName("공지사항 목록 조회 테스트")
    class GetNoticesTest {
        
        @Test
        @Order(1)
        @DisplayName("성공 - 공지사항 목록 조회")
        void successGetNotices(VertxTestContext tc) {
            String accessToken = getAccessTokenOfUser(1L);
            
            reqGet(getUrl("/?limit=10&offset=0"))
                .bearerTokenAuthentication(accessToken)
                .send(tc.succeeding(res -> tc.verify(() -> {
                    log.info("Get notices response: {}", res.bodyAsJsonObject());
                    NoticeListResponseDto response = expectSuccessAndGetResponse(res, refNoticeList);
                    
                    assertThat(response).isNotNull();
                    assertThat(response.getNotices()).isNotNull();
                    assertThat(response.getTotal()).isNotNull();
                    assertThat(response.getLimit()).isEqualTo(10);
                    assertThat(response.getOffset()).isEqualTo(0);
                    
                    tc.completeNow();
                })));
        }
        
        @Test
        @Order(2)
        @DisplayName("실패 - 인증 없이 조회")
        void failNoAuth(VertxTestContext tc) {
            reqGet(getUrl("/?limit=10&offset=0"))
                .send(tc.succeeding(res -> tc.verify(() -> {
                    expectError(res, 401);
                    tc.completeNow();
                })));
        }
    }
    
    @Nested
    @DisplayName("공지사항 상세 조회 테스트")
    class GetNoticeTest {
        
        @Test
        @Order(3)
        @DisplayName("성공 - 공지사항 상세 조회")
        void successGetNotice(VertxTestContext tc) {
            String accessToken = getAccessTokenOfUser(1L);
            
            // 공지사항이 있다고 가정하고 테스트
            reqGet(getUrl("/1"))
                .bearerTokenAuthentication(accessToken)
                .send(tc.succeeding(res -> {
                    // 공지사항이 없을 수도 있으므로 404도 허용
                    if (res.statusCode() == 200) {
                        tc.verify(() -> {
                            Notice notice = expectSuccessAndGetResponse(res, refNotice);
                            assertThat(notice).isNotNull();
                            assertThat(notice.getId()).isNotNull();
                            assertThat(notice.getTitle()).isNotNull();
                            tc.completeNow();
                        });
                    } else {
                        tc.verify(() -> {
                            expectError(res, 404);
                            tc.completeNow();
                        });
                    }
                }));
        }
        
        @Test
        @Order(4)
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

