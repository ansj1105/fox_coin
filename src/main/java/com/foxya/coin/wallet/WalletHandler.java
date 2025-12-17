package com.foxya.coin.wallet;

import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.JWTAuthHandler;
import io.vertx.ext.validation.ValidationHandler;
import io.vertx.ext.validation.openapi3.OpenAPI3RequestValidationHandler;
import com.foxya.coin.common.BaseHandler;
import com.foxya.coin.common.enums.UserRole;
import com.foxya.coin.common.utils.AuthUtils;
import com.foxya.coin.common.utils.Utils;
import com.foxya.coin.wallet.dto.CreateWalletRequestDto;
import io.vertx.json.schema.SchemaParser;
import lombok.extern.slf4j.Slf4j;

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
}

