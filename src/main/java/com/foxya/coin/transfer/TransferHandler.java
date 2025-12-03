package com.foxya.coin.transfer;

import com.foxya.coin.common.BaseHandler;
import com.foxya.coin.common.enums.UserRole;
import com.foxya.coin.common.utils.AuthUtils;
import com.foxya.coin.common.utils.Utils;
import com.foxya.coin.transfer.dto.ExternalTransferRequestDto;
import com.foxya.coin.transfer.dto.InternalTransferRequestDto;
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
public class TransferHandler extends BaseHandler {
    
    private final TransferService transferService;
    private final JWTAuth jwtAuth;
    
    public TransferHandler(Vertx vertx, TransferService transferService, JWTAuth jwtAuth) {
        super(vertx);
        this.transferService = transferService;
        this.jwtAuth = jwtAuth;
    }
    
    @Override
    public Router getRouter() {
        Router router = Router.router(getVertx());
        
        SchemaParser parser = createSchemaParser();
        
        // 모든 전송 API에 JWT 인증 적용
        router.route().handler(JWTAuthHandler.create(jwtAuth));
        
        // 내부 전송 (지갑주소/추천인코드/유저ID로 전송)
        router.post("/internal")
            .handler(AuthUtils.hasRole(UserRole.USER, UserRole.ADMIN))
            .handler(internalTransferValidation(parser))
            .handler(this::executeInternalTransfer);
        
        // 외부 전송 (출금 요청)
        router.post("/external")
            .handler(AuthUtils.hasRole(UserRole.USER, UserRole.ADMIN))
            .handler(externalTransferValidation(parser))
            .handler(this::requestExternalTransfer);
        
        // 전송 내역 조회
        router.get("/history")
            .handler(AuthUtils.hasRole(UserRole.USER, UserRole.ADMIN))
            .handler(this::getTransferHistory);
        
        // 전송 상세 조회
        router.get("/:transferId")
            .handler(AuthUtils.hasRole(UserRole.USER, UserRole.ADMIN))
            .handler(this::getTransferDetail);
        
        return router;
    }
    
    /**
     * 내부 전송 Validation
     */
    private Handler<RoutingContext> internalTransferValidation(SchemaParser parser) {
        return ValidationHandler.builder(parser)
            .body(json(
                objectSchema()
                    .requiredProperty("receiverType", stringSchema().with(minLength(1), maxLength(20)))
                    .requiredProperty("receiverValue", stringSchema().with(minLength(1), maxLength(255)))
                    .requiredProperty("currencyCode", stringSchema().with(minLength(1), maxLength(10)))
                    .requiredProperty("amount", numberSchema())
                    .optionalProperty("memo", stringSchema().with(maxLength(255)))
                    .allowAdditionalProperties(false)
            ))
            .build();
    }
    
    /**
     * 외부 전송 Validation
     */
    private Handler<RoutingContext> externalTransferValidation(SchemaParser parser) {
        return ValidationHandler.builder(parser)
            .body(json(
                objectSchema()
                    .requiredProperty("toAddress", stringSchema().with(minLength(10), maxLength(255)))
                    .requiredProperty("currencyCode", stringSchema().with(minLength(1), maxLength(10)))
                    .requiredProperty("amount", numberSchema())
                    .requiredProperty("chain", stringSchema().with(minLength(1), maxLength(20)))
                    .optionalProperty("memo", stringSchema().with(maxLength(255)))
                    .allowAdditionalProperties(false)
            ))
            .build();
    }
    
    /**
     * 내부 전송 실행
     */
    private void executeInternalTransfer(RoutingContext ctx) {
        Long userId = AuthUtils.getUserIdOf(ctx.user());
        String requestIp = ctx.request().remoteAddress().host();
        
        InternalTransferRequestDto dto = getObjectMapper().convertValue(
            Utils.getMapFromJsonObject(ctx.getBodyAsJson()),
            InternalTransferRequestDto.class
        );
        
        log.info("내부 전송 요청 - userId: {}, receiverType: {}, receiverValue: {}, amount: {}", 
            userId, dto.getReceiverType(), dto.getReceiverValue(), dto.getAmount());
        
        response(ctx, transferService.executeInternalTransfer(userId, dto, requestIp));
    }
    
    /**
     * 외부 전송 요청
     */
    private void requestExternalTransfer(RoutingContext ctx) {
        Long userId = AuthUtils.getUserIdOf(ctx.user());
        String requestIp = ctx.request().remoteAddress().host();
        
        ExternalTransferRequestDto dto = getObjectMapper().convertValue(
            Utils.getMapFromJsonObject(ctx.getBodyAsJson()),
            ExternalTransferRequestDto.class
        );
        
        log.info("외부 전송 요청 - userId: {}, toAddress: {}, amount: {}, chain: {}", 
            userId, dto.getToAddress(), dto.getAmount(), dto.getChain());
        
        response(ctx, transferService.requestExternalTransfer(userId, dto, requestIp));
    }
    
    /**
     * 전송 내역 조회
     */
    private void getTransferHistory(RoutingContext ctx) {
        Long userId = AuthUtils.getUserIdOf(ctx.user());
        int limit = Integer.parseInt(ctx.request().getParam("limit", "20"));
        int offset = Integer.parseInt(ctx.request().getParam("offset", "0"));
        
        log.info("전송 내역 조회 - userId: {}, limit: {}, offset: {}", userId, limit, offset);
        
        response(ctx, transferService.getTransferHistory(userId, limit, offset));
    }
    
    /**
     * 전송 상세 조회
     */
    private void getTransferDetail(RoutingContext ctx) {
        String transferId = ctx.pathParam("transferId");
        
        log.info("전송 상세 조회 - transferId: {}", transferId);
        
        response(ctx, transferService.getTransferDetail(transferId));
    }
}

