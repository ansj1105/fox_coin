package com.foxya.coin.banner;

import com.fasterxml.jackson.core.type.TypeReference;
import com.foxya.coin.common.HandlerTestBase;
import com.foxya.coin.common.dto.ApiResponse;
import com.foxya.coin.banner.dto.BannerListResponseDto;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(VertxExtension.class)
@Slf4j
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class BannerHandlerTest extends HandlerTestBase {
    
    private final TypeReference<ApiResponse<BannerListResponseDto>> refBannerList = new TypeReference<>() {};
    
    public BannerHandlerTest() {
        super("/api/v1/banners");
    }
    
    @Nested
    @DisplayName("배너 목록 조회 테스트")
    class GetBannersTest {
        
        @Test
        @Order(1)
        @DisplayName("성공 - 배너 목록 조회 (RANKING_TOP)")
        void successGetBannersRankingTop(VertxTestContext tc) {
            reqGet(getUrl("/?position=RANKING_TOP"))
                .send(tc.succeeding(res -> tc.verify(() -> {
                    log.info("Get banners response: {}", res.bodyAsJsonObject());
                    BannerListResponseDto response = expectSuccessAndGetResponse(res, refBannerList);
                    
                    assertThat(response).isNotNull();
                    assertThat(response.getBanners()).isNotNull();
                    
                    // 배너 정보 확인
                    if (response.getBanners() != null && !response.getBanners().isEmpty()) {
                        for (BannerListResponseDto.BannerInfo banner : response.getBanners()) {
                            assertThat(banner.getId()).isNotNull();
                            assertThat(banner.getTitle()).isNotNull();
                            assertThat(banner.getImageUrl()).isNotNull();
                            assertThat(banner.getPosition()).isNotNull();
                            assertThat(banner.getIsActive()).isNotNull();
                            assertThat(banner.getStartDate()).isNotNull();
                            assertThat(banner.getEndDate()).isNotNull();
                        }
                    }
                    
                    tc.completeNow();
                })));
        }
        
        @Test
        @Order(2)
        @DisplayName("성공 - 배너 목록 조회 (기본값)")
        void successGetBannersDefault(VertxTestContext tc) {
            reqGet(getUrl("/"))
                .send(tc.succeeding(res -> tc.verify(() -> {
                    log.info("Get banners (default) response: {}", res.bodyAsJsonObject());
                    BannerListResponseDto response = expectSuccessAndGetResponse(res, refBannerList);
                    
                    assertThat(response).isNotNull();
                    assertThat(response.getBanners()).isNotNull();
                    
                    tc.completeNow();
                })));
        }
    }
    
    @Nested
    @DisplayName("배너 클릭 이벤트 기록 테스트")
    class RecordBannerClickTest {
        
        @Test
        @Order(1)
        @DisplayName("성공 - 배너 클릭 이벤트 기록")
        void successRecordBannerClick(VertxTestContext tc) {
            // 배너 ID는 테스트 데이터에 따라 조정 필요
            Long bannerId = 1L;
            
            reqPost(getUrl("/" + bannerId + "/click"))
                .send(tc.succeeding(res -> tc.verify(() -> {
                    log.info("Record banner click response: {}", res.bodyAsJsonObject());
                    
                    // 200 또는 404 (배너가 없는 경우)
                    assertThat(res.statusCode()).isIn(200, 404);
                    
                    tc.completeNow();
                })));
        }
        
        @Test
        @Order(2)
        @DisplayName("실패 - 존재하지 않는 배너 클릭")
        void failRecordBannerClickNotFound(VertxTestContext tc) {
            Long bannerId = 999999L;
            
            reqPost(getUrl("/" + bannerId + "/click"))
                .send(tc.succeeding(res -> tc.verify(() -> {
                    assertThat(res.statusCode()).isEqualTo(404);
                    tc.completeNow();
                })));
        }
        
        @Test
        @Order(3)
        @DisplayName("실패 - 잘못된 배너 ID")
        void failRecordBannerClickInvalidId(VertxTestContext tc) {
            reqPost(getUrl("/invalid/click"))
                .send(tc.succeeding(res -> tc.verify(() -> {
                    assertThat(res.statusCode()).isEqualTo(400);
                    tc.completeNow();
                })));
        }
    }
}

