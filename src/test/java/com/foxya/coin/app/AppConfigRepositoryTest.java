package com.foxya.coin.app;

import com.foxya.coin.common.HandlerTestBase;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AppConfigRepository 테스트. V38/V40 마이그레이션 후 app_config (config_value, config_value_apple) 기준.
 */
@ExtendWith(VertxExtension.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class AppConfigRepositoryTest extends HandlerTestBase {

    public AppConfigRepositoryTest() {
        super("/api/v1/app");
    }

    @Test
    @Order(1)
    @DisplayName("getMinAppVersion(client) - Android 기준 config_value 1.1.8 반환")
    void getMinAppVersionReturnsFromDb(VertxTestContext tc) {
        AppConfigRepository repo = new AppConfigRepository();
        repo.getMinAppVersion(sqlClient)
            .onComplete(ar -> {
                tc.verify(() -> {
                    assertThat(ar.succeeded()).isTrue();
                    assertThat(ar.result()).isEqualTo("1.1.8");
                });
                tc.completeNow();
            });
    }

    @Test
    @Order(2)
    @DisplayName("getMinAppVersion(client, false) - Android면 config_value 반환")
    void getMinAppVersionAndroidReturnsConfigValue(VertxTestContext tc) {
        AppConfigRepository repo = new AppConfigRepository();
        repo.getMinAppVersion(sqlClient, false)
            .onComplete(ar -> {
                tc.verify(() -> {
                    assertThat(ar.succeeded()).isTrue();
                    assertThat(ar.result()).isEqualTo("1.1.8");
                });
                tc.completeNow();
            });
    }

    @Test
    @Order(3)
    @DisplayName("getMinAppVersion(client, true) - iOS, config_value_apple 비어 있으면 config_value 반환")
    void getMinAppVersionIosFallbackToConfigValue(VertxTestContext tc) {
        AppConfigRepository repo = new AppConfigRepository();
        repo.getMinAppVersion(sqlClient, true)
            .onComplete(ar -> {
                tc.verify(() -> {
                    assertThat(ar.succeeded()).isTrue();
                    assertThat(ar.result()).isEqualTo("1.1.8");
                });
                tc.completeNow();
            });
    }

    @Test
    @Order(4)
    @DisplayName("getMinAppVersion(client, true) - iOS, config_value_apple 있으면 해당 값 반환")
    void getMinAppVersionIosReturnsConfigValueApple(VertxTestContext tc) {
        AppConfigRepository repo = new AppConfigRepository();
        sqlClient.query("UPDATE app_config SET config_value_apple = '2.0.0' WHERE config_key = 'min_app_version'")
            .execute()
            .onComplete(arUpdate -> {
                if (arUpdate.failed()) {
                    tc.failNow(arUpdate.cause());
                    return;
                }
                repo.getMinAppVersion(sqlClient, true)
                    .onComplete(ar -> {
                        tc.verify(() -> {
                            assertThat(ar.succeeded()).isTrue();
                            assertThat(ar.result()).isEqualTo("2.0.0");
                        });
                        // 원복 (다음 테스트를 위해)
                        sqlClient.query("UPDATE app_config SET config_value_apple = '' WHERE config_key = 'min_app_version'")
                            .execute()
                            .onComplete(ignore -> tc.completeNow());
                    });
            });
    }

    @Test
    @Order(5)
    @DisplayName("getByKey - 존재하지 않는 키는 null")
    void getByKeyUnknownReturnsNull(VertxTestContext tc) {
        AppConfigRepository repo = new AppConfigRepository();
        repo.getByKey(sqlClient, "unknown_key")
            .onComplete(ar -> {
                tc.verify(() -> {
                    assertThat(ar.succeeded()).isTrue();
                    assertThat(ar.result()).isNull();
                });
                tc.completeNow();
            });
    }
}
