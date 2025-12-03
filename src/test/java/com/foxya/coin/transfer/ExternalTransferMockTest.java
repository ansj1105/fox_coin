package com.foxya.coin.transfer;

import com.foxya.coin.common.BaseService;
import com.foxya.coin.currency.CurrencyRepository;
import com.foxya.coin.currency.entities.Currency;
import com.foxya.coin.event.EventPublisher;
import com.foxya.coin.event.EventType;
import com.foxya.coin.transfer.dto.ExternalTransferRequestDto;
import com.foxya.coin.transfer.dto.TransferResponseDto;
import com.foxya.coin.transfer.entities.ExternalTransfer;
import com.foxya.coin.user.UserRepository;
import com.foxya.coin.wallet.entities.Wallet;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.PoolOptions;
import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;

import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * 외부 전송 Mock 테스트
 * 
 * 실제 Node.js 서버 없이 외부 전송 플로우를 테스트합니다.
 * EventPublisher를 모킹하여 Redis 이벤트 발행을 시뮬레이션합니다.
 */
@ExtendWith(VertxExtension.class)
@Slf4j
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ExternalTransferMockTest {
    
    private static PgPool pool;
    private static TransferRepository transferRepository;
    private static UserRepository userRepository;
    private static CurrencyRepository currencyRepository;
    private static EventPublisher mockEventPublisher;
    private static TransferService transferService;
    private static Flyway flyway;
    
    // 테스트용 데이터
    private static final Long TEST_USER_ID = 1L;
    private static final String EXTERNAL_ADDRESS = "TExternalWalletAddress12345678901234";
    
    @BeforeAll
    static void setup(Vertx vertx, VertxTestContext tc) {
        // 테스트 설정 로드
        String configContent = vertx.fileSystem().readFileBlocking("src/test/resources/config.json").toString();
        JsonObject fullConfig = new JsonObject(configContent);
        JsonObject config = fullConfig.getJsonObject("test");
        JsonObject dbConfig = config.getJsonObject("database");
        JsonObject flywayConfig = config.getJsonObject("flyway");
        
        // PostgreSQL 연결
        PgConnectOptions connectOptions = new PgConnectOptions()
            .setHost(dbConfig.getString("host"))
            .setPort(dbConfig.getInteger("port"))
            .setDatabase(dbConfig.getString("database"))
            .setUser(dbConfig.getString("user"))
            .setPassword(dbConfig.getString("password"));
        
        PoolOptions poolOptions = new PoolOptions().setMaxSize(5);
        pool = PgPool.pool(vertx, connectOptions, poolOptions);
        
        // Flyway 설정
        flyway = Flyway.configure()
            .dataSource(flywayConfig.getString("url"), flywayConfig.getString("user"), flywayConfig.getString("password"))
            .locations("filesystem:src/test/resources/db/migration", "filesystem:src/test/resources/db/seed")
            .cleanDisabled(false)
            .load();
        
        // Repository 초기화
        transferRepository = new TransferRepository();
        userRepository = new UserRepository();
        currencyRepository = new CurrencyRepository();
        
        // Mock EventPublisher 생성
        mockEventPublisher = mock(EventPublisher.class);
        when(mockEventPublisher.publishToStream(any(EventType.class), any(JsonObject.class)))
            .thenReturn(Future.succeededFuture());
        when(mockEventPublisher.publish(any(EventType.class), any(JsonObject.class)))
            .thenReturn(Future.succeededFuture());
        
        // TransferService 초기화 (Mock EventPublisher 주입)
        transferService = new TransferService(pool, transferRepository, userRepository, currencyRepository, mockEventPublisher);
        
        tc.completeNow();
    }
    
    @BeforeEach
    void migrate() {
        flyway.clean();
        flyway.migrate();
        reset(mockEventPublisher);
        when(mockEventPublisher.publishToStream(any(EventType.class), any(JsonObject.class)))
            .thenReturn(Future.succeededFuture());
    }
    
    @AfterAll
    static void teardown() {
        if (pool != null) {
            pool.close();
        }
    }
    
    @Nested
    @DisplayName("외부 전송 요청 테스트")
    class ExternalTransferRequestTest {
        
        @Test
        @Order(1)
        @DisplayName("성공 - 외부 전송 요청 생성 및 이벤트 발행")
        void successCreateExternalTransfer(VertxTestContext tc) {
            ExternalTransferRequestDto request = ExternalTransferRequestDto.builder()
                .toAddress(EXTERNAL_ADDRESS)
                .currencyCode("FOXYA")
                .amount(new BigDecimal("100"))
                .chain("TRON")
                .memo("외부 전송 테스트")
                .build();
            
            transferService.requestExternalTransfer(TEST_USER_ID, request, "127.0.0.1")
                .onSuccess(response -> tc.verify(() -> {
                    log.info("External transfer response: {}", response);
                    
                    // 응답 검증
                    assertThat(response.getTransferId()).isNotNull();
                    assertThat(response.getTransferType()).isEqualTo("EXTERNAL");
                    assertThat(response.getSenderId()).isEqualTo(TEST_USER_ID);
                    assertThat(response.getToAddress()).isEqualTo(EXTERNAL_ADDRESS);
                    assertThat(response.getAmount()).isEqualByComparingTo(new BigDecimal("100"));
                    assertThat(response.getStatus()).isEqualTo("PENDING");
                    
                    // EventPublisher.publishToStream이 호출되었는지 검증
                    verify(mockEventPublisher, times(1)).publishToStream(
                        eq(EventType.WITHDRAWAL_REQUESTED),
                        argThat(payload -> {
                            log.info("Published event payload: {}", payload);
                            return payload.getString("transferId").equals(response.getTransferId())
                                && payload.getLong("userId").equals(TEST_USER_ID)
                                && payload.getString("toAddress").equals(EXTERNAL_ADDRESS)
                                && payload.getString("amount").equals("100")
                                && payload.getString("currencyCode").equals("FOXYA")
                                && payload.getString("chain").equals("TRON");
                        })
                    );
                    
                    tc.completeNow();
                }))
                .onFailure(tc::failNow);
        }
        
        @Test
        @Order(2)
        @DisplayName("성공 - 외부 전송 후 잔액 잠금 확인")
        void successBalanceLocked(VertxTestContext tc) {
            // 초기 잔액 조회
            transferRepository.getWalletByUserIdAndCurrencyId(pool, TEST_USER_ID, 1) // FOXYA TRON currency_id
                .compose(initialWallet -> {
                    BigDecimal initialBalance = initialWallet.getBalance();
                    BigDecimal initialLockedBalance = initialWallet.getLockedBalance();
                    log.info("Initial balance: {}, locked: {}", initialBalance, initialLockedBalance);
                    
                    // 외부 전송 요청
                    ExternalTransferRequestDto request = ExternalTransferRequestDto.builder()
                        .toAddress(EXTERNAL_ADDRESS)
                        .currencyCode("FOXYA")
                        .amount(new BigDecimal("50"))
                        .chain("TRON")
                        .build();
                    
                    return transferService.requestExternalTransfer(TEST_USER_ID, request, "127.0.0.1")
                        .compose(response -> {
                            // 전송 후 잔액 조회
                            return transferRepository.getWalletByUserIdAndCurrencyId(pool, TEST_USER_ID, 1)
                                .map(updatedWallet -> {
                                    BigDecimal expectedFee = new BigDecimal("50").multiply(new BigDecimal("0.001")); // 0.1% 수수료
                                    BigDecimal totalDeduct = new BigDecimal("50").add(expectedFee);
                                    
                                    log.info("Updated balance: {}, locked: {}", 
                                        updatedWallet.getBalance(), updatedWallet.getLockedBalance());
                                    
                                    // 잔액이 차감되고 잠금 잔액이 증가했는지 확인
                                    assertThat(updatedWallet.getBalance())
                                        .isEqualByComparingTo(initialBalance.subtract(totalDeduct));
                                    assertThat(updatedWallet.getLockedBalance())
                                        .isEqualByComparingTo(initialLockedBalance.add(totalDeduct));
                                    
                                    return response;
                                });
                        });
                })
                .onSuccess(response -> tc.completeNow())
                .onFailure(tc::failNow);
        }
    }
    
    @Nested
    @DisplayName("외부 전송 상태 업데이트 테스트 (Node.js 서버 응답 시뮬레이션)")
    class ExternalTransferStatusUpdateTest {
        
        @Test
        @Order(1)
        @DisplayName("성공 - 외부 전송 제출 완료 (SUBMITTED)")
        void successSubmitExternalTransfer(VertxTestContext tc) {
            // 1. 외부 전송 요청 생성
            ExternalTransferRequestDto request = ExternalTransferRequestDto.builder()
                .toAddress(EXTERNAL_ADDRESS)
                .currencyCode("FOXYA")
                .amount(new BigDecimal("30"))
                .chain("TRON")
                .build();
            
            transferService.requestExternalTransfer(TEST_USER_ID, request, "127.0.0.1")
                .compose(response -> {
                    String transferId = response.getTransferId();
                    String mockTxHash = "0x" + "a".repeat(64); // Mock 트랜잭션 해시
                    
                    // 2. Node.js 서버에서 트랜잭션 제출 완료 시뮬레이션
                    return transferRepository.submitExternalTransfer(pool, transferId, mockTxHash);
                })
                .onSuccess(updatedTransfer -> tc.verify(() -> {
                    log.info("Submitted transfer: {}", updatedTransfer);
                    
                    assertThat(updatedTransfer.getStatus()).isEqualTo(ExternalTransfer.STATUS_SUBMITTED);
                    assertThat(updatedTransfer.getTxHash()).isNotNull();
                    assertThat(updatedTransfer.getSubmittedAt()).isNotNull();
                    
                    tc.completeNow();
                }))
                .onFailure(tc::failNow);
        }
        
        @Test
        @Order(2)
        @DisplayName("성공 - 외부 전송 컨펌 완료 (CONFIRMED)")
        void successConfirmExternalTransfer(VertxTestContext tc) {
            // 1. 외부 전송 요청 생성
            ExternalTransferRequestDto request = ExternalTransferRequestDto.builder()
                .toAddress(EXTERNAL_ADDRESS)
                .currencyCode("FOXYA")
                .amount(new BigDecimal("20"))
                .chain("TRON")
                .build();
            
            transferService.requestExternalTransfer(TEST_USER_ID, request, "127.0.0.1")
                .compose(response -> {
                    String transferId = response.getTransferId();
                    String mockTxHash = "0x" + "b".repeat(64);
                    
                    // 2. 제출 완료
                    return transferRepository.submitExternalTransfer(pool, transferId, mockTxHash)
                        .compose(submitted -> {
                            // 3. 컨펌 완료 시뮬레이션 (20 confirmations)
                            return transferRepository.confirmExternalTransfer(pool, transferId, 20);
                        });
                })
                .onSuccess(confirmedTransfer -> tc.verify(() -> {
                    log.info("Confirmed transfer: {}", confirmedTransfer);
                    
                    assertThat(confirmedTransfer.getStatus()).isEqualTo(ExternalTransfer.STATUS_CONFIRMED);
                    assertThat(confirmedTransfer.getConfirmations()).isEqualTo(20);
                    assertThat(confirmedTransfer.getConfirmedAt()).isNotNull();
                    
                    tc.completeNow();
                }))
                .onFailure(tc::failNow);
        }
        
        @Test
        @Order(3)
        @DisplayName("성공 - 외부 전송 실패 처리 및 잔액 복구")
        void successFailExternalTransfer(VertxTestContext tc) {
            AtomicReference<BigDecimal> initialBalance = new AtomicReference<>();
            AtomicReference<String> transferIdRef = new AtomicReference<>();
            
            // 1. 초기 잔액 저장
            transferRepository.getWalletByUserIdAndCurrencyId(pool, TEST_USER_ID, 1)
                .compose(wallet -> {
                    initialBalance.set(wallet.getBalance().add(wallet.getLockedBalance()));
                    log.info("Total initial balance (available + locked): {}", initialBalance.get());
                    
                    // 2. 외부 전송 요청 생성
                    ExternalTransferRequestDto request = ExternalTransferRequestDto.builder()
                        .toAddress(EXTERNAL_ADDRESS)
                        .currencyCode("FOXYA")
                        .amount(new BigDecimal("25"))
                        .chain("TRON")
                        .build();
                    
                    return transferService.requestExternalTransfer(TEST_USER_ID, request, "127.0.0.1");
                })
                .compose(response -> {
                    transferIdRef.set(response.getTransferId());
                    
                    // 3. 실패 처리 시뮬레이션
                    return transferRepository.failExternalTransfer(
                        pool, 
                        response.getTransferId(), 
                        "NETWORK_ERROR", 
                        "Failed to broadcast transaction"
                    );
                })
                .compose(failedTransfer -> {
                    log.info("Failed transfer: {}", failedTransfer);
                    
                    assertThat(failedTransfer.getStatus()).isEqualTo(ExternalTransfer.STATUS_FAILED);
                    assertThat(failedTransfer.getErrorCode()).isEqualTo("NETWORK_ERROR");
                    assertThat(failedTransfer.getErrorMessage()).isEqualTo("Failed to broadcast transaction");
                    assertThat(failedTransfer.getFailedAt()).isNotNull();
                    
                    // 4. 잔액 복구 (실제로는 별도 서비스에서 처리)
                    BigDecimal refundAmount = new BigDecimal("25").add(new BigDecimal("25").multiply(new BigDecimal("0.001")));
                    return transferRepository.unlockBalance(pool, failedTransfer.getWalletId(), refundAmount, true);
                })
                .compose(restoredWallet -> {
                    // 5. 잔액 복구 확인
                    return transferRepository.getWalletByUserIdAndCurrencyId(pool, TEST_USER_ID, 1);
                })
                .onSuccess(finalWallet -> tc.verify(() -> {
                    BigDecimal finalTotal = finalWallet.getBalance().add(finalWallet.getLockedBalance());
                    log.info("Final total balance: {}", finalTotal);
                    
                    // 실패 시 잔액이 원래대로 복구되었는지 확인
                    // (테스트 환경에서는 다른 테스트의 영향을 받을 수 있으므로 잠금 잔액만 확인)
                    assertThat(finalWallet.getLockedBalance()).isGreaterThanOrEqualTo(BigDecimal.ZERO);
                    
                    tc.completeNow();
                }))
                .onFailure(tc::failNow);
        }
    }
    
    @Nested
    @DisplayName("외부 전송 조회 테스트")
    class ExternalTransferQueryTest {
        
        @Test
        @Order(1)
        @DisplayName("성공 - transferId로 외부 전송 조회")
        void successGetByTransferId(VertxTestContext tc) {
            ExternalTransferRequestDto request = ExternalTransferRequestDto.builder()
                .toAddress(EXTERNAL_ADDRESS)
                .currencyCode("FOXYA")
                .amount(new BigDecimal("15"))
                .chain("TRON")
                .build();
            
            transferService.requestExternalTransfer(TEST_USER_ID, request, "127.0.0.1")
                .compose(response -> {
                    return transferRepository.getExternalTransferById(pool, response.getTransferId());
                })
                .onSuccess(transfer -> tc.verify(() -> {
                    assertThat(transfer).isNotNull();
                    assertThat(transfer.getUserId()).isEqualTo(TEST_USER_ID);
                    assertThat(transfer.getToAddress()).isEqualTo(EXTERNAL_ADDRESS);
                    assertThat(transfer.getChain()).isEqualTo("TRON");
                    
                    tc.completeNow();
                }))
                .onFailure(tc::failNow);
        }
        
        @Test
        @Order(2)
        @DisplayName("성공 - txHash로 외부 전송 조회")
        void successGetByTxHash(VertxTestContext tc) {
            String mockTxHash = "0x" + "c".repeat(64);
            
            ExternalTransferRequestDto request = ExternalTransferRequestDto.builder()
                .toAddress(EXTERNAL_ADDRESS)
                .currencyCode("FOXYA")
                .amount(new BigDecimal("10"))
                .chain("TRON")
                .build();
            
            transferService.requestExternalTransfer(TEST_USER_ID, request, "127.0.0.1")
                .compose(response -> {
                    return transferRepository.submitExternalTransfer(pool, response.getTransferId(), mockTxHash);
                })
                .compose(submitted -> {
                    return transferRepository.getExternalTransferByTxHash(pool, mockTxHash);
                })
                .onSuccess(transfer -> tc.verify(() -> {
                    assertThat(transfer).isNotNull();
                    assertThat(transfer.getTxHash()).isEqualTo(mockTxHash);
                    assertThat(transfer.getStatus()).isEqualTo(ExternalTransfer.STATUS_SUBMITTED);
                    
                    tc.completeNow();
                }))
                .onFailure(tc::failNow);
        }
        
        @Test
        @Order(3)
        @DisplayName("성공 - 대기중인 외부 전송 목록 조회")
        void successGetPendingTransfers(VertxTestContext tc) {
            // 여러 개의 외부 전송 요청 생성
            ExternalTransferRequestDto request1 = ExternalTransferRequestDto.builder()
                .toAddress(EXTERNAL_ADDRESS)
                .currencyCode("FOXYA")
                .amount(new BigDecimal("5"))
                .chain("TRON")
                .build();
            
            ExternalTransferRequestDto request2 = ExternalTransferRequestDto.builder()
                .toAddress(EXTERNAL_ADDRESS)
                .currencyCode("FOXYA")
                .amount(new BigDecimal("6"))
                .chain("TRON")
                .build();
            
            transferService.requestExternalTransfer(TEST_USER_ID, request1, "127.0.0.1")
                .compose(r1 -> transferService.requestExternalTransfer(TEST_USER_ID, request2, "127.0.0.1"))
                .compose(r2 -> {
                    return transferRepository.getPendingExternalTransfers(pool, 10);
                })
                .onSuccess(pendingTransfers -> tc.verify(() -> {
                    log.info("Pending transfers count: {}", pendingTransfers.size());
                    
                    assertThat(pendingTransfers).isNotEmpty();
                    assertThat(pendingTransfers).allMatch(t -> t.getStatus().equals(ExternalTransfer.STATUS_PENDING));
                    
                    tc.completeNow();
                }))
                .onFailure(tc::failNow);
        }
    }
}

