package com.foxya.coin.app;

import com.fasterxml.jackson.core.type.TypeReference;
import com.foxya.coin.common.HandlerTestBase;
import com.foxya.coin.common.dto.ApiResponse;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * GET /api/v1/app/config (minAppVersion 등) 테스트.
 * app_config 테이블 초기값 min_app_version = 1.1.8 기준.
 */
@ExtendWith(VertxExtension.class)
@Slf4j
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class AppHandlerTest extends HandlerTestBase {

    private static final TypeReference<ApiResponse<AppConfigResponse>> REF_APP_CONFIG = new TypeReference<>() {};

    public AppHandlerTest() {
        super("/api/v1/app");
    }

    @Nested
    @DisplayName("GET /config - 앱 최소 버전 조회")
    class GetConfigTest {

        @Test
        @Order(1)
        @DisplayName("성공 - 인증 없이 minAppVersion 조회 (기본 Android 기준 config_value)")
        void successGetConfigWithoutAuth(VertxTestContext tc) {
            reqGet(getUrl("/config"))
                .send(tc.succeeding(res -> tc.verify(() -> {
                    expectSuccess(res);
                    AppConfigResponse data = expectSuccessAndGetResponse(res, REF_APP_CONFIG);
                    assertThat(data).isNotNull();
                    assertThat(data.getMinAppVersion()).isEqualTo("1.1.8");
                    tc.completeNow();
                })));
        }

        @Test
        @Order(2)
        @DisplayName("성공 - X-Device-Os: ANDROID 이면 config_value 기준 minAppVersion")
        void getConfigWithAndroidHeader(VertxTestContext tc) {
            webClient.get(port, "localhost", getUrl("/config"))
                .putHeader("X-Device-Os", "ANDROID")
                .send(tc.succeeding(res -> tc.verify(() -> {
                    expectSuccess(res);
                    AppConfigResponse data = expectSuccessAndGetResponse(res, REF_APP_CONFIG);
                    assertThat(data.getMinAppVersion()).isEqualTo("1.1.8");
                    tc.completeNow();
                })));
        }

        @Test
        @Order(3)
        @DisplayName("성공 - X-Device-Os: IOS 이면 config_value_apple(비어있으면 config_value) 기준 minAppVersion")
        void getConfigWithIosHeader(VertxTestContext tc) {
            webClient.get(port, "localhost", getUrl("/config"))
                .putHeader("X-Device-Os", "IOS")
                .send(tc.succeeding(res -> tc.verify(() -> {
                    expectSuccess(res);
                    AppConfigResponse data = expectSuccessAndGetResponse(res, REF_APP_CONFIG);
                    assertThat(data.getMinAppVersion()).isEqualTo("1.1.8");
                    tc.completeNow();
                })));
        }

        @Test
        @Order(4)
        @DisplayName("성공 - platform=ios 쿼리 파라미터면 iOS 기준 minAppVersion")
        void getConfigWithPlatformQueryParam(VertxTestContext tc) {
            webClient.get(port, "localhost", getUrl("/config") + "?platform=ios")
                .send(tc.succeeding(res -> tc.verify(() -> {
                    expectSuccess(res);
                    AppConfigResponse data = expectSuccessAndGetResponse(res, REF_APP_CONFIG);
                    assertThat(data.getMinAppVersion()).isEqualTo("1.1.8");
                    tc.completeNow();
                })));
        }
    }

    /** GET f 응답 data 타입 */
    @lombok.Getter
    @lombok.Setter
    public static class AppConfigResponse {
        private String minAppVersion;
    }
}
