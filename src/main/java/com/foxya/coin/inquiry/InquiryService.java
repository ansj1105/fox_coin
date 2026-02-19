package com.foxya.coin.inquiry;

import com.foxya.coin.common.BaseService;
import com.foxya.coin.common.exceptions.BadRequestException;
import com.foxya.coin.common.exceptions.NotFoundException;
import com.foxya.coin.inquiry.dto.CreateInquiryRequestDto;
import com.foxya.coin.inquiry.dto.InquiryResponseDto;
import com.foxya.coin.inquiry.entities.Inquiry;
import com.foxya.coin.inquiry.enums.InquiryStatus;
import com.foxya.coin.notification.NotificationService;
import com.foxya.coin.notification.enums.NotificationType;
import com.foxya.coin.user.UserService;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.pgclient.PgPool;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class InquiryService extends BaseService {

    private final InquiryRepository inquiryRepository;
    private final UserService userService;
    private final NotificationService notificationService;

    public InquiryService(PgPool pool, InquiryRepository inquiryRepository, UserService userService) {
        this(pool, inquiryRepository, userService, null);
    }

    public InquiryService(PgPool pool, InquiryRepository inquiryRepository, UserService userService,
                          NotificationService notificationService) {
        super(pool);
        this.inquiryRepository = inquiryRepository;
        this.userService = userService;
        this.notificationService = notificationService;
    }

    public Future<InquiryResponseDto> createInquiry(Long userId, CreateInquiryRequestDto dto) {
        if (dto.getSubject() == null || dto.getSubject().trim().isEmpty()) {
            return Future.failedFuture(new BadRequestException("필수 항목을 입력해 주세요."));
        }
        if (dto.getContent() == null || dto.getContent().trim().isEmpty()) {
            return Future.failedFuture(new BadRequestException("필수 항목을 입력해 주세요."));
        }

        if (dto.getSubject().length() > 20) {
            return Future.failedFuture(new BadRequestException("문의 제목은 20자까지 입력할 수 있습니다."));
        }
        if (dto.getContent().length() > 200) {
            return Future.failedFuture(new BadRequestException("문의 내용은 200자까지 입력할 수 있습니다."));
        }

        return userService.getEmailInfo(userId)
            .compose(emailInfo -> {
                String email = emailInfo.getEmail();
                if (email == null || email.trim().isEmpty()) {
                    email = dto.getEmail();
                    if (email == null || email.trim().isEmpty()) {
                        return Future.failedFuture(new BadRequestException("이메일을 설정해 주세요."));
                    }
                }

                return inquiryRepository.createInquiry(pool, userId, dto.getSubject().trim(), dto.getContent().trim(), email)
                    .compose(inquiry -> createInquirySuccessNotification(inquiry)
                        .map(v -> InquiryResponseDto.builder()
                            .id(inquiry.getId())
                            .subject(inquiry.getSubject())
                            .content(inquiry.getContent())
                            .email(inquiry.getEmail())
                            .status(inquiry.getStatus())
                            .createdAt(inquiry.getCreatedAt())
                            .build()));
            });
    }

    public Future<InquiryResponseDto> markInquiryAnswered(Long inquiryId) {
        return inquiryRepository.getInquiryById(pool, inquiryId)
            .compose(inquiry -> {
                if (inquiry == null) {
                    return Future.failedFuture(new NotFoundException("Inquiry not found."));
                }
                if (inquiry.getStatus() == InquiryStatus.ANSWERED) {
                    return Future.succeededFuture(inquiry);
                }
                return inquiryRepository.updateInquiryStatus(pool, inquiryId, InquiryStatus.ANSWERED);
            })
            .compose(updated -> createInquiryAnsweredNotification(updated)
                .map(v -> InquiryResponseDto.builder()
                    .id(updated.getId())
                    .subject(updated.getSubject())
                    .content(updated.getContent())
                    .email(updated.getEmail())
                    .status(updated.getStatus())
                    .createdAt(updated.getCreatedAt())
                    .build()));
    }

    private Future<Void> createInquirySuccessNotification(Inquiry inquiry) {
        if (notificationService == null || inquiry == null || inquiry.getUserId() == null || inquiry.getId() == null) {
            return Future.<Void>succeededFuture();
        }

        JsonObject metadata = new JsonObject()
            .put("inquiryId", inquiry.getId())
            .put("status", inquiry.getStatus() != null ? inquiry.getStatus().name() : null)
            .put("createdAt", inquiry.getCreatedAt() != null ? inquiry.getCreatedAt().toString() : null);

        return notificationService.createNotificationIfAbsentByRelatedId(
                inquiry.getUserId(),
                NotificationType.INQUIRY_SUCCESS,
                "Inquiry Submitted",
                "Your inquiry has been received.",
                inquiry.getId(),
                metadata.encode())
            .compose(v -> Future.<Void>succeededFuture())
            .recover(err -> {
                log.warn("Inquiry submit notification failed (ignored): inquiryId={}", inquiry.getId(), err);
                return Future.<Void>succeededFuture();
            });
    }

    private Future<Void> createInquiryAnsweredNotification(Inquiry inquiry) {
        if (notificationService == null || inquiry == null || inquiry.getUserId() == null || inquiry.getId() == null) {
            return Future.<Void>succeededFuture();
        }

        JsonObject metadata = new JsonObject()
            .put("inquiryId", inquiry.getId())
            .put("status", inquiry.getStatus() != null ? inquiry.getStatus().name() : null);

        return notificationService.createNotificationIfAbsentByRelatedId(
                inquiry.getUserId(),
                NotificationType.INQUIRY_ANSWERED,
                "Inquiry Answered",
                "Your inquiry has been answered.",
                inquiry.getId(),
                metadata.encode())
            .compose(v -> Future.<Void>succeededFuture())
            .recover(err -> {
                log.warn("Inquiry answered notification failed (ignored): inquiryId={}", inquiry.getId(), err);
                return Future.<Void>succeededFuture();
            });
    }
}
