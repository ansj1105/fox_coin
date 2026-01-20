package com.foxya.coin.wallet;

import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.JWTAuthHandler;
import io.vertx.ext.web.validation.ValidationHandler;
import com.foxya.coin.common.BaseHandler;
import com.foxya.coin.common.enums.UserRole;
import com.foxya.coin.common.utils.AuthUtils;
import com.foxya.coin.common.utils.Utils;
import com.foxya.coin.wallet.dto.CreateWalletRequestDto;
import com.foxya.coin.wallet.dto.RegisterWalletChallengeRequestDto;
import com.foxya.coin.wallet.dto.RegisterWalletChallengeResponseDto;
import com.foxya.coin.wallet.dto.RegisterWalletRequestDto;
import io.vertx.json.schema.SchemaParser;
import lombok.extern.slf4j.Slf4j;

import static io.vertx.ext.web.validation.builder.Bodies.json;
import static io.vertx.json.schema.common.dsl.Keywords.*;
import static io.vertx.json.schema.common.dsl.Schemas.*;
import static com.foxya.coin.common.jsonschema.Schemas.*;

@Slf4j
public class WalletHandler extends BaseHandler {
    
    private final WalletService walletService;
    
    public WalletHandler(Vertx vertx, WalletService walletService) {
        super(vertx);
        this.walletService = walletService;
    }
    
    @Override
    public Router getRouter() {
        Router router = Router.router(getVertx());
        
        SchemaParser parser = createSchemaParser();
        
        // 인증 필요 - 본인 지갑만 조회 가능
        router.get("/my")
            .handler(AuthUtils.hasRole(UserRole.USER, UserRole.ADMIN))
            .handler(this::getMyWallets);
        
        // 지갑 생성
        router.post("/")
            .handler(AuthUtils.hasRole(UserRole.USER, UserRole.ADMIN))
            .handler(createWalletValidation(parser))
            .handler(this::createWallet);

        // 시드 기반 지갑 등록 - 챌린지 발급
        router.post("/register-challenge")
            .handler(AuthUtils.hasRole(UserRole.USER, UserRole.ADMIN))
            .handler(registerWalletChallengeValidation(parser))
            .handler(this::registerWalletChallenge);

        // 시드 기반 지갑 등록
        router.post("/register")
            .handler(AuthUtils.hasRole(UserRole.USER, UserRole.ADMIN))
            .handler(registerWalletValidation(parser))
            .handler(this::registerWallet);
        
        return router;
    }
    
    /**
     * 지갑 생성 Validation
     */
    private Handler<RoutingContext> createWalletValidation(SchemaParser parser) {
        return ValidationHandler.builder(parser)
            .body(json(
                objectSchema()
                    .requiredProperty("currencyCode", stringSchema().with(minLength(1), maxLength(10)))
                    .allowAdditionalProperties(false)
            ))
            .build();
    }

    private Handler<RoutingContext> registerWalletChallengeValidation(SchemaParser parser) {
        return ValidationHandler.builder(parser)
            .body(json(
                objectSchema()
                    .requiredProperty("address", stringSchema().with(minLength(10), maxLength(255)))
                    .requiredProperty("chain", enumStringSchema(new String[]{"ETH", "TRON", "BTC"}))
                    .allowAdditionalProperties(false)
            ))
            .build();
    }

    private Handler<RoutingContext> registerWalletValidation(SchemaParser parser) {
        return ValidationHandler.builder(parser)
            .body(json(
                objectSchema()
                    .requiredProperty("currencyCode", stringSchema().with(minLength(1), maxLength(10)))
                    .requiredProperty("address", stringSchema().with(minLength(10), maxLength(255)))
                    .requiredProperty("chain", enumStringSchema(new String[]{"ETH", "TRON", "BTC"}))
                    .requiredProperty("signature", stringSchema().with(minLength(32), maxLength(256)))
                    .allowAdditionalProperties(false)
            ))
            .build();
    }
    
    private void getMyWallets(RoutingContext ctx) {
        // JWT에서 userId 추출
        Long userId = AuthUtils.getUserIdOf(ctx.user());
        log.info("Fetching wallets for user: {}", userId);
        response(ctx, walletService.getUserWallets(userId));
    }
    
    private void createWallet(RoutingContext ctx) {
        Long userId = AuthUtils.getUserIdOf(ctx.user());
        
        CreateWalletRequestDto dto = getObjectMapper().convertValue(
            Utils.getMapFromJsonObject(ctx.getBodyAsJson()),
            CreateWalletRequestDto.class
        );
        
        log.info("Creating wallet for user: {}, currencyCode: {}", userId, dto.getCurrencyCode());
        response(ctx, walletService.createWallet(userId, dto.getCurrencyCode()));
    }

    private void registerWalletChallenge(RoutingContext ctx) {
        Long userId = AuthUtils.getUserIdOf(ctx.user());
        RegisterWalletChallengeRequestDto dto = getObjectMapper().convertValue(
            Utils.getMapFromJsonObject(ctx.getBodyAsJson()),
            RegisterWalletChallengeRequestDto.class
        );
        log.info("Wallet register challenge - userId: {}, chain: {}", userId, dto.getChain());
        response(ctx, walletService.requestWalletRegistrationChallenge(userId, dto.getAddress(), dto.getChain()),
            message -> RegisterWalletChallengeResponseDto.builder()
                .message(message)
                .expiresInSeconds(600)
                .build());
    }

    private void registerWallet(RoutingContext ctx) {
        Long userId = AuthUtils.getUserIdOf(ctx.user());
        RegisterWalletRequestDto dto = getObjectMapper().convertValue(
            Utils.getMapFromJsonObject(ctx.getBodyAsJson()),
            RegisterWalletRequestDto.class
        );
        log.info("Wallet register - userId: {}, currencyCode: {}", userId, dto.getCurrencyCode());
        response(ctx, walletService.registerWalletWithSignature(userId, dto.getCurrencyCode(), dto.getChain(), dto.getAddress(), dto.getSignature()));
    }
}
