package com.foxya.coin.app;

import com.foxya.coin.common.HandlerTestBase;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AppConfigRepository 테스트. V38 마이그레이션 후 app_config.min_app_version = 1.1.8 기준.
 */
@ExtendWith(VertxExtension.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class AppConfigRepositoryTest extends HandlerTestBase {

    public AppConfigRepositoryTest() {
        super("/api/v1/app");
    }

    @Test
    @Order(1)
    @DisplayName("getMinAppVersion - DB app_config에서 1.1.8 반환")
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
