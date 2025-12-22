package com.foxya.coin.notification;

import com.fasterxml.jackson.core.type.TypeReference;
import com.foxya.coin.common.HandlerTestBase;
import com.foxya.coin.common.dto.ApiResponse;
import com.foxya.coin.notification.dto.NotificationListResponseDto;
import com.foxya.coin.notification.dto.UnreadCountResponseDto;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(VertxExtension.class)
@Slf4j
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class NotificationHandlerTest extends HandlerTestBase {
    
    private final TypeReference<ApiResponse<NotificationListResponseDto>> refNotificationList = new TypeReference<>() {};
    private final TypeReference<ApiResponse<UnreadCountResponseDto>> refUnreadCount = new TypeReference<>() {};
    private final TypeReference<ApiResponse<Void>> refVoid = new TypeReference<>() {};
    
    public NotificationHandlerTest() {
        super("/api/v1/notifications");
    }
    
    @Nested
    @DisplayName("알림 목록 조회 테스트")
    class GetNotificationsTest {
        
        @Test
        @Order(1)
        @DisplayName("성공 - 알림 목록 조회 (기본값)")
        void successGetNotificationsDefault(VertxTestContext tc) {
            String accessToken = getAccessTokenOfUser(1L);
            
            reqGet(getUrl("/"))
                .bearerTokenAuthentication(accessToken)
                .send(tc.succeeding(res -> tc.verify(() -> {
                    log.info("Get notifications response: {}", res.bodyAsJsonObject());
                    NotificationListResponseDto data = expectSuccessAndGetResponse(res, refNotificationList);
                    
                    assertThat(data.getNotifications()).isNotNull();
                    assertThat(data.getUnreadCount()).isNotNull();
                    assertThat(data.getTotal()).isNotNull();
                    assertThat(data.getLimit()).isEqualTo(20);
                    assertThat(data.getOffset()).isEqualTo(0);
                    
                    tc.completeNow();
                })));
        }
        
        @Test
        @Order(2)
        @DisplayName("성공 - 알림 목록 조회 (limit, offset 지정)")
        void successGetNotificationsWithParams(VertxTestContext tc) {
            String accessToken = getAccessTokenOfUser(1L);
            
            reqGet(getUrl("/?limit=10&offset=0"))
                .bearerTokenAuthentication(accessToken)
                .send(tc.succeeding(res -> tc.verify(() -> {
                    log.info("Get notifications with params response: {}", res.bodyAsJsonObject());
                    NotificationListResponseDto data = expectSuccessAndGetResponse(res, refNotificationList);
                    
                    assertThat(data.getNotifications()).isNotNull();
                    assertThat(data.getLimit()).isEqualTo(10);
                    assertThat(data.getOffset()).isEqualTo(0);
                    
                    tc.completeNow();
                })));
        }
        
        @Test
        @Order(3)
        @DisplayName("실패 - 인증 없이 조회")
        void failNoAuth(VertxTestContext tc) {
            reqGet(getUrl("/"))
                .send(tc.succeeding(res -> tc.verify(() -> {
                    expectError(res, 401);
                    tc.completeNow();
                })));
        }
    }
    
    @Nested
    @DisplayName("읽지 않은 알림 개수 조회 테스트")
    class GetUnreadCountTest {
        
        @Test
        @Order(10)
        @DisplayName("성공 - 읽지 않은 알림 개수 조회")
        void successGetUnreadCount(VertxTestContext tc) {
            String accessToken = getAccessTokenOfUser(1L);
            
            reqGet(getUrl("/unread-count"))
                .bearerTokenAuthentication(accessToken)
                .send(tc.succeeding(res -> tc.verify(() -> {
                    log.info("Get unread count response: {}", res.bodyAsJsonObject());
                    UnreadCountResponseDto data = expectSuccessAndGetResponse(res, refUnreadCount);
                    
                    assertThat(data.getCount()).isNotNull();
                    assertThat(data.getCount()).isGreaterThanOrEqualTo(0);
                    
                    tc.completeNow();
                })));
        }
        
        @Test
        @Order(11)
        @DisplayName("실패 - 인증 없이 조회")
        void failNoAuth(VertxTestContext tc) {
            reqGet(getUrl("/unread-count"))
                .send(tc.succeeding(res -> tc.verify(() -> {
                    expectError(res, 401);
                    tc.completeNow();
                })));
        }
    }
    
    @Nested
    @DisplayName("알림 읽음 처리 테스트")
    class MarkAsReadTest {
        
        @Test
        @Order(20)
        @DisplayName("성공 - 특정 알림 읽음 처리")
        void successMarkAsRead(VertxTestContext tc) {
            String accessToken = getAccessTokenOfUser(1L);
            
            // 먼저 알림 목록을 조회하여 알림 ID를 가져옴
            reqGet(getUrl("/?limit=1"))
                .bearerTokenAuthentication(accessToken)
                .send(tc.succeeding(res1 -> tc.verify(() -> {
                    NotificationListResponseDto listData = expectSuccessAndGetResponse(res1, refNotificationList);
                    
                    if (listData.getNotifications() != null && !listData.getNotifications().isEmpty()) {
                        Long notificationId = listData.getNotifications().get(0).getId();
                        
                        // 알림을 읽음 처리
                        reqPatch(getUrl("/" + notificationId + "/read"))
                            .bearerTokenAuthentication(accessToken)
                            .send(tc.succeeding(res2 -> tc.verify(() -> {
                                expectSuccess(res2);
                                tc.completeNow();
                            })));
                    } else {
                        log.info("No notifications to mark as read");
                        tc.completeNow();
                    }
                })));
        }
        
        @Test
        @Order(21)
        @DisplayName("실패 - 존재하지 않는 알림 읽음 처리")
        void failMarkAsReadNotFound(VertxTestContext tc) {
            String accessToken = getAccessTokenOfUser(1L);
            
            reqPatch(getUrl("/999999/read"))
                .bearerTokenAuthentication(accessToken)
                .send(tc.succeeding(res -> tc.verify(() -> {
                    expectError(res, 404);
                    tc.completeNow();
                })));
        }
        
        @Test
        @Order(22)
        @DisplayName("실패 - 인증 없이 읽음 처리")
        void failNoAuth(VertxTestContext tc) {
            reqPatch(getUrl("/1/read"))
                .send(tc.succeeding(res -> tc.verify(() -> {
                    expectError(res, 401);
                    tc.completeNow();
                })));
        }
    }
    
    @Nested
    @DisplayName("모든 알림 읽음 처리 테스트")
    class MarkAllAsReadTest {
        
        @Test
        @Order(30)
        @DisplayName("성공 - 모든 알림 읽음 처리")
        void successMarkAllAsRead(VertxTestContext tc) {
            String accessToken = getAccessTokenOfUser(1L);
            
            reqPatch(getUrl("/read-all"))
                .bearerTokenAuthentication(accessToken)
                .send(tc.succeeding(res -> tc.verify(() -> {
                    expectSuccess(res);
                    tc.completeNow();
                })));
        }
        
        @Test
        @Order(31)
        @DisplayName("실패 - 인증 없이 모든 알림 읽음 처리")
        void failNoAuth(VertxTestContext tc) {
            reqPatch(getUrl("/read-all"))
                .send(tc.succeeding(res -> tc.verify(() -> {
                    expectError(res, 401);
                    tc.completeNow();
                })));
        }
    }
}

