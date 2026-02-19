package com.foxya.coin.deposit;

import com.foxya.coin.common.utils.OrderNumberUtils;
import com.foxya.coin.currency.CurrencyRepository;
import com.foxya.coin.currency.entities.Currency;
import com.foxya.coin.deposit.entities.TokenDeposit;
import com.foxya.coin.transfer.TransferRepository;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 토큰 입금 완료(completeTokenDeposit) 테스트.
 * 입금 조회 후 내부 지갑 반영 로직 검증.
 */
@ExtendWith(VertxExtension.class)
@Slf4j
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class TokenDepositServiceTest {

    private static PgPool pool;
    private static TokenDepositRepository tokenDepositRepository;
    private static CurrencyRepository currencyRepository;
    private static TransferRepository transferRepository;
    private static TokenDepositService tokenDepositService;
    private static Flyway flyway;

    private static final Long TEST_USER_ID = 1L;
    private static final String CURRENCY_CODE = "FOXYA";
    private static final String CHAIN = "TRON";
    private static final BigDecimal DEPOSIT_AMOUNT = new BigDecimal("100.50");

    @BeforeAll
    static void setup(Vertx vertx, VertxTestContext tc) {
        String configContent = vertx.fileSystem().readFileBlocking("src/test/resources/config.json").toString();
        JsonObject fullConfig = new JsonObject(configContent);
        JsonObject config = fullConfig.getJsonObject("test");
        JsonObject dbConfig = config.getJsonObject("database");
        JsonObject flywayConfig = config.getJsonObject("flyway");

        PgConnectOptions connectOptions = new PgConnectOptions()
            .setHost(dbConfig.getString("host"))
            .setPort(dbConfig.getInteger("port"))
            .setDatabase(dbConfig.getString("database"))
            .setUser(dbConfig.getString("user"))
            .setPassword(dbConfig.getString("password"));
        PoolOptions poolOptions = new PoolOptions().setMaxSize(5);
        pool = PgPool.pool(vertx, connectOptions, poolOptions);

        flyway = Flyway.configure()
            .dataSource(flywayConfig.getString("url"), flywayConfig.getString("user"), flywayConfig.getString("password"))
            .locations("filesystem:src/test/resources/db/migration", "filesystem:src/test/resources/db/seed")
            .cleanDisabled(false)
            .load();

        tokenDepositRepository = new TokenDepositRepository();
        currencyRepository = new CurrencyRepository();
        transferRepository = new TransferRepository();
        tokenDepositService = new TokenDepositService(pool, tokenDepositRepository, currencyRepository, transferRepository, null);

        tc.completeNow();
    }

    @BeforeEach
    void migrate() {
        flyway.clean();
        flyway.migrate();
    }

    @AfterAll
    static void teardown() {
        if (pool != null) {
            pool.close();
        }
    }

    private Future<Currency> getFoxyTronCurrency() {
        return currencyRepository.getCurrencyByCodeAndChain(pool, CURRENCY_CODE, CHAIN);
    }

    private Future<BigDecimal> getWalletBalance(Long userId, Integer currencyId) {
        return transferRepository.getWalletByUserIdAndCurrencyId(pool, userId, currencyId)
            .map(w -> w != null ? w.getBalance() : null);
    }

    @Test
    @Order(1)
    @DisplayName("성공 - 토큰 입금 완료 시 내부 지갑 반영")
    void successCompleteTokenDepositReflectsInternalWallet(VertxTestContext tc) {
        String depositId = UUID.randomUUID().toString();
        String orderNumber = OrderNumberUtils.generateOrderNumber();
        String txHash = "0x" + "a".repeat(64);
        String senderAddress = "TSenderAddress12345678901234567890";

        getFoxyTronCurrency()
            .compose(currency -> {
                if (currency == null) {
                    return Future.failedFuture(new IllegalStateException("FOXYA TRON currency not found"));
                }
                TokenDeposit deposit = TokenDeposit.builder()
                    .depositId(depositId)
                    .userId(TEST_USER_ID)
                    .orderNumber(orderNumber)
                    .currencyId(currency.getId())
                    .amount(DEPOSIT_AMOUNT)
                    .network(CHAIN)
                    .senderAddress(senderAddress)
                    .toAddress("TReceiverAddress12345678901234567890")
                    .logIndex(1)
                    .blockNumber(123456L)
                    .txHash(txHash)
                    .status(TokenDeposit.STATUS_PENDING)
                    .build();
                return tokenDepositRepository.createTokenDeposit(pool, deposit).map(d -> new Object[] { currency, d });
            })
            .compose(pair -> {
                Currency currency = (Currency) ((Object[]) pair)[0];
                TokenDeposit created = (TokenDeposit) ((Object[]) pair)[1];
                return getWalletBalance(TEST_USER_ID, currency.getId())
                    .map(bal -> new Object[] { created.getDepositId(), currency.getId(), bal });
            })
            .compose(triple -> {
                String dId = (String) ((Object[]) triple)[0];
                Integer currencyId = (Integer) ((Object[]) triple)[1];
                BigDecimal balanceBefore = (BigDecimal) ((Object[]) triple)[2];
                return tokenDepositService.completeTokenDeposit(dId)
                    .map(completed -> new Object[] { currencyId, balanceBefore, completed });
            })
            .compose(result -> {
                Integer currencyId = (Integer) ((Object[]) result)[0];
                BigDecimal balanceBefore = (BigDecimal) ((Object[]) result)[1];
                TokenDeposit completed = (TokenDeposit) ((Object[]) result)[2];
                assertThat(completed.getStatus()).isEqualTo(TokenDeposit.STATUS_COMPLETED);
                assertThat(completed.getConfirmedAt()).isNotNull();
                return getWalletBalance(TEST_USER_ID, currencyId)
                    .map(balanceAfter -> new Object[] { balanceBefore, balanceAfter });
            })
            .onSuccess(pair -> tc.verify(() -> {
                BigDecimal balanceBefore = (BigDecimal) ((Object[]) pair)[0];
                BigDecimal balanceAfter = (BigDecimal) ((Object[]) pair)[1];
                assertThat(balanceBefore).isNotNull();
                assertThat(balanceAfter).isNotNull();
                assertThat(balanceAfter).isEqualByComparingTo(balanceBefore.add(DEPOSIT_AMOUNT));
                log.info("Token deposit completed - balance before: {}, after: {}, amount: {}", balanceBefore, balanceAfter, DEPOSIT_AMOUNT);
                tc.completeNow();
            }))
            .onFailure(tc::failNow);
    }

    @Test
    @Order(2)
    @DisplayName("실패 - userId 없는 입금은 완료 불가")
    void failCompleteTokenDepositWithoutUserId(VertxTestContext tc) {
        String depositId = UUID.randomUUID().toString();
        getFoxyTronCurrency()
            .compose(currency -> {
                if (currency == null) {
                    return Future.failedFuture(new IllegalStateException("FOXYA TRON currency not found"));
                }
                TokenDeposit deposit = TokenDeposit.builder()
                    .depositId(depositId)
                    .userId(null)
                    .orderNumber(OrderNumberUtils.generateOrderNumber())
                    .currencyId(currency.getId())
                    .amount(DEPOSIT_AMOUNT)
                    .network(CHAIN)
                    .senderAddress("TSender")
                    .toAddress("TReceiver")
                    .logIndex(0)
                    .blockNumber(1L)
                    .txHash(null)
                    .status(TokenDeposit.STATUS_PENDING)
                    .build();
                return tokenDepositRepository.createTokenDeposit(pool, deposit).map(d -> d.getDepositId());
            })
            .compose(dId -> tokenDepositService.completeTokenDeposit(dId))
            .onSuccess(ignored -> tc.failNow(new AssertionError("Should have failed")))
            .onFailure(throwable -> tc.verify(() -> {
                assertThat(throwable.getMessage()).contains("사용자 매칭이 없습니다");
                tc.completeNow();
            }));
    }

    @Test
    @Order(3)
    @DisplayName("실패 - 이미 완료된 입금 재처리 시 에러 (멱등)")
    void failCompleteTokenDepositAlreadyCompleted(VertxTestContext tc) {
        String depositId = UUID.randomUUID().toString();
        getFoxyTronCurrency()
            .compose(currency -> {
                if (currency == null) {
                    return Future.failedFuture(new IllegalStateException("FOXYA TRON currency not found"));
                }
                TokenDeposit deposit = TokenDeposit.builder()
                    .depositId(depositId)
                    .userId(TEST_USER_ID)
                    .orderNumber(OrderNumberUtils.generateOrderNumber())
                    .currencyId(currency.getId())
                    .amount(DEPOSIT_AMOUNT)
                    .network(CHAIN)
                    .senderAddress("TSender2")
                    .toAddress("TReceiver2")
                    .logIndex(2)
                    .blockNumber(999L)
                    .txHash("0x" + "b".repeat(64))
                    .status(TokenDeposit.STATUS_PENDING)
                    .build();
                return tokenDepositRepository.createTokenDeposit(pool, deposit).map(d -> d.getDepositId());
            })
            .compose(dId -> tokenDepositService.completeTokenDeposit(dId)
                .compose(completed -> tokenDepositService.completeTokenDeposit(dId)))
            .onSuccess(ignored -> tc.failNow(new AssertionError("Second complete should have failed")))
            .onFailure(throwable -> tc.verify(() -> {
                assertThat(throwable.getMessage()).contains("이미 처리된 입금");
                tc.completeNow();
            }));
    }
}
