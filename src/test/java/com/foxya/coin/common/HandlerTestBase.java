package com.foxya.coin.common;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.JWTOptions;
import io.vertx.ext.auth.PubSecKeyOptions;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.auth.jwt.JWTAuthOptions;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.junit5.VertxTestContext;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.PoolOptions;
import io.vertx.sqlclient.SqlClient;
import com.foxya.coin.MainVerticle;
import com.foxya.coin.common.dto.ApiResponse;
import com.foxya.coin.common.enums.UserRole;
import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Slf4j
public class HandlerTestBase {
    
    protected static int port = 8089;
    protected static SqlClient sqlClient;
    protected static JWTAuth jwtAuth;
    protected static WebClient webClient;
    protected static ObjectMapper objectMapper;
    protected static Flyway flyway;
    
    protected final String apiUrl;
    
    public HandlerTestBase(String baseUrl) {
        this.apiUrl = baseUrl;
    }
    
    @BeforeAll
    protected static void deployVerticle(final Vertx vertx, final VertxTestContext testContext) throws IOException {
        log.info("Test deployVerticle start");
        
        webClient = WebClient.create(vertx);
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        
        // test용 config.json에서 test 환경 설정 로드
        String configContent = vertx.fileSystem().readFileBlocking("src/test/resources/config.json").toString();
        JsonObject fullConfig = new JsonObject(configContent);
        JsonObject config = fullConfig.getJsonObject("test");
        
        if (config == null) {
            throw new IllegalStateException("Test environment config not found in src/test/resources/config.json");
        }
        
        JsonObject dbConfig = config.getJsonObject("database");
        JsonObject jwtConfig = config.getJsonObject("jwt");
        JsonObject flywayConfig = config.getJsonObject("flyway");
        
        // JWT Auth 설정
        jwtAuth = JWTAuth.create(vertx, new JWTAuthOptions()
            .addPubSecKey(new PubSecKeyOptions()
                .setAlgorithm("HS256")
                .setBuffer(jwtConfig.getString("secret"))));
        
        // PostgreSQL 연결
        PgConnectOptions connectOptions = new PgConnectOptions()
            .setHost(dbConfig.getString("host"))
            .setPort(dbConfig.getInteger("port"))
            .setDatabase(dbConfig.getString("database"))
            .setUser(dbConfig.getString("user"))
            .setPassword(dbConfig.getString("password"));
        
        PoolOptions poolOptions = new PoolOptions()
            .setMaxSize(dbConfig.getInteger("pool_size", 5));
        sqlClient = PgPool.client(vertx, connectOptions, poolOptions);
        
        testContext.verify(() -> {
            // Flyway 설정
            configureFlyway(flywayConfig);
            
            // MainVerticle 배포 (test용 config.json과 test 환경 지정)
            vertx.deployVerticle(
                new MainVerticle("src/test/resources/config.json", "test"),
                testContext.succeedingThenComplete()
            );
        });
    }
    
    @BeforeEach
    protected void init(Vertx vertx, VertxTestContext testContext) {
        testContext.verify(() -> {
            try {
                migration();
                testContext.completeNow();
            } catch (Exception e) {
                testContext.failNow(e.toString());
            }
        });
    }
    
    @AfterAll
    protected static void close(final Vertx vertx) {
        if (sqlClient != null) {
            sqlClient.close();
        }
        vertx.close();
    }
    
    private static void configureFlyway(JsonObject flywayConfig) {
        String jdbcUrl = flywayConfig.getString("url");
        String user = flywayConfig.getString("user");
        String password = flywayConfig.getString("password");
        
        flyway = Flyway.configure()
            .dataSource(jdbcUrl, user, password)
            .locations("filesystem:src/test/resources/db/migration")
            .cleanDisabled(false)
            .load();
    }
    
    private void migration() {
        log.debug("TEST migration - clean and migrate");
        flyway.clean();  // 테스트마다 DB 초기화
        flyway.migrate();
    }
    
    /**
     * Access Token 생성 (테스트용)
     */
    protected String getAccessToken(Long userId, UserRole role) {
        JsonObject payload = new JsonObject()
            .put("userId", userId.toString())
            .put("role", role.name());
        
        return jwtAuth.generateToken(payload, new JWTOptions().setExpiresInMinutes(30));
    }
    
    /**
     * Admin Access Token 생성
     */
    protected String getAccessTokenOfAdmin(Long userId) {
        return getAccessToken(userId, UserRole.ADMIN);
    }
    
    /**
     * User Access Token 생성
     */
    protected String getAccessTokenOfUser(Long userId) {
        return getAccessToken(userId, UserRole.USER);
    }
    
    /**
     * URL 생성
     */
    protected String getUrl(String path) {
        return apiUrl + path;
    }
    
    /**
     * GET 요청
     */
    protected HttpRequest<Buffer> reqGet(String url) {
        return webClient.get(port, "localhost", url);
    }
    
    /**
     * POST 요청
     */
    protected HttpRequest<Buffer> reqPost(String url) {
        return webClient.post(port, "localhost", url);
    }
    
    /**
     * PUT 요청
     */
    protected HttpRequest<Buffer> reqPut(String url) {
        return webClient.put(port, "localhost", url);
    }
    
    /**
     * DELETE 요청
     */
    protected HttpRequest<Buffer> reqDelete(String url) {
        return webClient.delete(port, "localhost", url);
    }
    
    /**
     * 성공 응답 검증 및 데이터 추출
     */
    protected <T> T expectSuccessAndGetResponse(HttpResponse<Buffer> res, TypeReference<ApiResponse<T>> typeRef) {
        assertEquals(HttpResponseStatus.OK.code(), res.statusCode());
        try {
            ApiResponse<T> response = objectMapper.readValue(res.bodyAsString(), typeRef);
            assertEquals("OK", response.getStatus());
            return response.getData();
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse response", e);
        }
    }
    
    /**
     * 에러 응답 검증
     */
    protected void expectError(HttpResponse<Buffer> res, int expectedStatusCode) {
        assertEquals(expectedStatusCode, res.statusCode());
    }
}

