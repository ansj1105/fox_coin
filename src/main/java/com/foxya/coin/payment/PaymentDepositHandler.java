package com.foxya.coin.payment;

import com.foxya.coin.common.BaseHandler;
import com.foxya.coin.common.enums.UserRole;
import com.foxya.coin.common.utils.AuthUtils;
import com.foxya.coin.common.utils.Utils;
import com.foxya.coin.payment.dto.PaymentDepositRequestDto;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.JWTAuthHandler;
import io.vertx.ext.web.validation.ValidationHandler;
import io.vertx.json.schema.SchemaParser;
import lombok.extern.slf4j.Slf4j;

import static io.vertx.ext.web.validation.builder.Bodies.json;
import static io.vertx.json.schema.common.dsl.Keywords.*;
import static io.vertx.json.schema.common.dsl.Schemas.*;

@Slf4j
public class PaymentDepositHandler extends BaseHandler {
    
    private final PaymentDepositService paymentDepositService;
    private final JWTAuth jwtAuth;
    
    public PaymentDepositHandler(Vertx vertx, PaymentDepositService paymentDepositService, JWTAuth jwtAuth) {
        super(vertx);
        this.paymentDepositService = paymentDepositService;
        this.jwtAuth = jwtAuth;
    }
    
    @Override
    public Router getRouter() {
        Router router = Router.router(getVertx());
        
        SchemaParser parser = createSchemaParser();
        
        // 모든 결제 입금 API에 JWT 인증 적용
        router.route().handler(JWTAuthHandler.create(jwtAuth));
        
        // 결제 입금 요청
        router.post("/deposit")
            .handler(AuthUtils.hasRole(UserRole.USER, UserRole.ADMIN))
            .handler(paymentDepositValidation(parser))
            .handler(this::requestPaymentDeposit);
        
        // 결제 입금 상세 조회
        router.get("/deposit/:depositId")
            .handler(AuthUtils.hasRole(UserRole.USER, UserRole.ADMIN))
            .handler(this::getPaymentDeposit);
        
        return router;
    }
    
    /**
     * 결제 입금 Validation
     */
    private Handler<RoutingContext> paymentDepositValidation(SchemaParser parser) {
        return ValidationHandler.builder(parser)
            .body(json(
                objectSchema()
                    .requiredProperty("currencyCode", stringSchema().with(minLength(1), maxLength(10)))
                    .requiredProperty("amount", numberSchema())
                    .requiredProperty("depositMethod", stringSchema().with(minLength(1), maxLength(20)))
                    .requiredProperty("paymentAmount", numberSchema())
                    .allowAdditionalProperties(false)
            ))
            .build();
    }
    
    /**
     * 결제 입금 요청
     */
    private void requestPaymentDeposit(RoutingContext ctx) {
        Long userId = AuthUtils.getUserIdOf(ctx.user());
        String requestIp = ctx.request().remoteAddress() != null 
            ? ctx.request().remoteAddress().host() 
            : null;
        
        PaymentDepositRequestDto dto = getObjectMapper().convertValue(
            Utils.getMapFromJsonObject(ctx.getBodyAsJson()),
            PaymentDepositRequestDto.class
        );
        
        log.info("결제 입금 요청 - userId: {}, currencyCode: {}, amount: {}, depositMethod: {}", 
            userId, dto.getCurrencyCode(), dto.getAmount(), dto.getDepositMethod());
        
        response(ctx, paymentDepositService.requestPaymentDeposit(userId, dto, requestIp));
    }
    
    /**
     * 결제 입금 상세 조회
     */
    private void getPaymentDeposit(RoutingContext ctx) {
        Long userId = AuthUtils.getUserIdOf(ctx.user());
        String depositId = ctx.pathParam("depositId");
        
        log.info("결제 입금 상세 조회 - userId: {}, depositId: {}", userId, depositId);
        
        response(ctx, paymentDepositService.getPaymentDeposit(userId, depositId));
    }
}

