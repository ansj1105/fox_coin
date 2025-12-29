package com.foxya.coin.inquiry;

import com.foxya.coin.common.BaseService;
import com.foxya.coin.common.exceptions.BadRequestException;
import com.foxya.coin.inquiry.dto.CreateInquiryRequestDto;
import com.foxya.coin.inquiry.dto.InquiryResponseDto;
import com.foxya.coin.inquiry.entities.Inquiry;
import com.foxya.coin.user.UserService;
import io.vertx.core.Future;
import io.vertx.pgclient.PgPool;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class InquiryService extends BaseService {
    
    private final InquiryRepository inquiryRepository;
    private final UserService userService;
    
    public InquiryService(PgPool pool, InquiryRepository inquiryRepository, UserService userService) {
        super(pool);
        this.inquiryRepository = inquiryRepository;
        this.userService = userService;
    }
    
    /**
     * 문의 생성
     */
    public Future<InquiryResponseDto> createInquiry(Long userId, CreateInquiryRequestDto dto) {
        // 필수 항목 검증
        if (dto.getSubject() == null || dto.getSubject().trim().isEmpty()) {
            return Future.failedFuture(new BadRequestException("필수 항목을 모두 입력해주세요."));
        }
        if (dto.getContent() == null || dto.getContent().trim().isEmpty()) {
            return Future.failedFuture(new BadRequestException("필수 항목을 모두 입력해주세요."));
        }
        
        // 길이 제한 검증
        if (dto.getSubject().length() > 20) {
            return Future.failedFuture(new BadRequestException("문의 제목은 최대 20자까지 입력 가능합니다."));
        }
        if (dto.getContent().length() > 200) {
            return Future.failedFuture(new BadRequestException("문의 내용은 최대 200자까지 입력 가능합니다."));
        }
        
        // 이메일 가져오기 (이메일 설정에서 가져온 값)
        return userService.getEmailInfo(userId)
            .compose(emailInfo -> {
                String email = emailInfo.getEmail();
                if (email == null || email.trim().isEmpty()) {
                    // 이메일이 없으면 요청에서 받은 이메일 사용 (또는 에러 반환)
                    email = dto.getEmail();
                    if (email == null || email.trim().isEmpty()) {
                        return Future.failedFuture(new BadRequestException("이메일을 설정해주세요."));
                    }
                }
                
                // 문의 생성
                return inquiryRepository.createInquiry(pool, userId, dto.getSubject().trim(), 
                                                      dto.getContent().trim(), email)
                    .map(inquiry -> InquiryResponseDto.builder()
                        .id(inquiry.getId())
                        .subject(inquiry.getSubject())
                        .content(inquiry.getContent())
                        .email(inquiry.getEmail())
                        .status(inquiry.getStatus())
                        .createdAt(inquiry.getCreatedAt())
                        .build());
            });
    }
}

