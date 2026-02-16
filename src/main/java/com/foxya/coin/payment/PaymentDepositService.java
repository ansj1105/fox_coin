package com.foxya.coin.payment;

import com.foxya.coin.common.BaseService;
import com.foxya.coin.common.exceptions.BadRequestException;
import com.foxya.coin.common.exceptions.NotFoundException;
import com.foxya.coin.common.utils.OrderNumberUtils;
import com.foxya.coin.currency.CurrencyRepository;
import com.foxya.coin.notification.NotificationService;
import com.foxya.coin.notification.enums.NotificationType;
import com.foxya.coin.payment.dto.PaymentDepositRequestDto;
import com.foxya.coin.payment.dto.PaymentDepositResponseDto;
import com.foxya.coin.payment.entities.PaymentDeposit;
import com.foxya.coin.transfer.TransferRepository;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.pgclient.PgPool;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.util.UUID;

@Slf4j
public class PaymentDepositService extends BaseService {

    private final PaymentDepositRepository paymentDepositRepository;
    private final CurrencyRepository currencyRepository;
    private final TransferRepository transferRepository;
    private final NotificationService notificationService;

    private static final BigDecimal MIN_DEPOSIT_AMOUNT = new BigDecimal("0.000001");

    public PaymentDepositService(PgPool pool, PaymentDepositRepository paymentDepositRepository,
                                CurrencyRepository currencyRepository,
                                TransferRepository transferRepository) {
        this(pool, paymentDepositRepository, currencyRepository, transferRepository, null);
    }

    public PaymentDepositService(PgPool pool, PaymentDepositRepository paymentDepositRepository,
                                CurrencyRepository currencyRepository,
                                TransferRepository transferRepository,
                                NotificationService notificationService) {
        super(pool);
        this.paymentDepositRepository = paymentDepositRepository;
        this.currencyRepository = currencyRepository;
        this.transferRepository = transferRepository;
        this.notificationService = notificationService;
    }

    public Future<PaymentDepositResponseDto> requestPaymentDeposit(Long userId, PaymentDepositRequestDto request, String requestIp) {
        log.info("Normalized log message",
            userId, request.getCurrencyCode(), request.getAmount(), request.getDepositMethod(), request.getPaymentAmount());

        if (request.getAmount() == null || request.getAmount().compareTo(MIN_DEPOSIT_AMOUNT) < 0) {
            return Future.failedFuture(new BadRequestException("Invalid request." + MIN_DEPOSIT_AMOUNT + "Invalid request."));
        }

        if (request.getPaymentAmount() == null || request.getPaymentAmount().compareTo(BigDecimal.ZERO) <= 0) {
            return Future.failedFuture(new BadRequestException("Invalid request."));
        }

        return currencyRepository.getCurrencyByCode(pool, request.getCurrencyCode())
            .compose(currency -> {
                if (currency == null) {
                    return Future.failedFuture(new NotFoundException("Resource not found." + request.getCurrencyCode()));
                }

                String depositId = UUID.randomUUID().toString();
                String orderNumber = OrderNumberUtils.generateOrderNumber();

                PaymentDeposit deposit = PaymentDeposit.builder()
                    .depositId(depositId)
                    .userId(userId)
                    .orderNumber(orderNumber)
                    .currencyId(currency.getId())
                    .amount(request.getAmount())
                    .depositMethod(request.getDepositMethod())
                    .paymentAmount(request.getPaymentAmount())
                    .status(PaymentDeposit.STATUS_PENDING)
                    .build();

                return paymentDepositRepository.createPaymentDeposit(pool, deposit)
                    .map(createdDeposit -> PaymentDepositResponseDto.builder()
                        .depositId(createdDeposit.getDepositId())
                        .orderNumber(createdDeposit.getOrderNumber())
                        .currencyCode(currency.getCode())
                        .amount(createdDeposit.getAmount())
                        .depositMethod(createdDeposit.getDepositMethod())
                        .paymentAmount(createdDeposit.getPaymentAmount())
                        .status(createdDeposit.getStatus())
                        .createdAt(createdDeposit.getCreatedAt())
                        .build());
            });
    }

    public Future<PaymentDepositResponseDto> completePaymentDeposit(String depositId) {
        return paymentDepositRepository.getPaymentDepositByDepositId(pool, depositId)
            .compose(deposit -> {
                if (deposit == null) {
                    return Future.failedFuture(new NotFoundException("Resource not found."));
                }

                if (!PaymentDeposit.STATUS_PENDING.equals(deposit.getStatus())) {
                    return Future.failedFuture(new BadRequestException("Invalid request."));
                }

                return pool.withTransaction(client ->
                    transferRepository.getWalletByUserIdAndCurrencyId(client, deposit.getUserId(), deposit.getCurrencyId())
                        .compose(wallet -> {
                            if (wallet == null) {
                                return Future.failedFuture(new NotFoundException("Resource not found."));
                            }

                            return transferRepository.addBalance(client, wallet.getId(), deposit.getAmount())
                                .compose(updatedWallet ->
                                    paymentDepositRepository.completePaymentDeposit(client, depositId)
                                        .compose(completedDeposit ->
                                            currencyRepository.getCurrencyById(client, completedDeposit.getCurrencyId())
                                                .map(currency -> PaymentDepositResponseDto.builder()
                                                    .depositId(completedDeposit.getDepositId())
                                                    .orderNumber(completedDeposit.getOrderNumber())
                                                    .currencyCode(currency.getCode())
                                                    .amount(completedDeposit.getAmount())
                                                    .depositMethod(completedDeposit.getDepositMethod())
                                                    .paymentAmount(completedDeposit.getPaymentAmount())
                                                    .status(completedDeposit.getStatus())
                                                    .createdAt(completedDeposit.getCreatedAt())
                                                    .build())));
                        })
                ).compose(this::createPaymentDepositCompletedNotification);
            });
    }

    private Future<PaymentDepositResponseDto> createPaymentDepositCompletedNotification(PaymentDepositResponseDto responseDto) {
        if (notificationService == null || responseDto == null || responseDto.getDepositId() == null) {
            return Future.succeededFuture(responseDto);
        }

        return paymentDepositRepository.getPaymentDepositByDepositId(pool, responseDto.getDepositId())
            .compose(deposit -> {
                if (deposit == null || deposit.getUserId() == null) {
                    return Future.succeededFuture();
                }

                String title = "\uC785\uAE08 \uC644\uB8CC";
                String message = responseDto.getAmount() + " " + responseDto.getCurrencyCode() + " \uC785\uAE08\uC774 \uC644\uB8CC\uB418\uC5C8\uC2B5\uB2C8\uB2E4.";
                JsonObject metadata = new JsonObject()
                    .put("depositId", responseDto.getDepositId())
                    .put("orderNumber", responseDto.getOrderNumber())
                    .put("currencyCode", responseDto.getCurrencyCode())
                    .put("amount", responseDto.getAmount() != null ? responseDto.getAmount().toPlainString() : null)
                    .put("status", responseDto.getStatus());

                return notificationService.createNotificationIfAbsentByRelatedId(
                    deposit.getUserId(),
                    NotificationType.DEPOSIT_SUCCESS,
                    title,
                    message,
                    deposit.getId(),
                    metadata.encode()
                ).mapEmpty();
            })
            .map(v -> responseDto)
            .recover(err -> {
                log.warn("Normalized log message", responseDto.getDepositId(), err);
                return Future.succeededFuture(responseDto);
            });
    }

    public Future<PaymentDepositResponseDto> getPaymentDeposit(Long userId, String depositId) {
        return paymentDepositRepository.getPaymentDepositByDepositId(pool, depositId)
            .compose(deposit -> {
                if (deposit == null) {
                    return Future.failedFuture(new NotFoundException("Resource not found."));
                }

                if (!deposit.getUserId().equals(userId)) {
                    return Future.failedFuture(new BadRequestException("Invalid request."));
                }

                return currencyRepository.getCurrencyById(pool, deposit.getCurrencyId())
                    .map(currency -> PaymentDepositResponseDto.builder()
                        .depositId(deposit.getDepositId())
                        .orderNumber(deposit.getOrderNumber())
                        .currencyCode(currency.getCode())
                        .amount(deposit.getAmount())
                        .depositMethod(deposit.getDepositMethod())
                        .paymentAmount(deposit.getPaymentAmount())
                        .status(deposit.getStatus())
                        .createdAt(deposit.getCreatedAt())
                        .build());
            });
    }
}
