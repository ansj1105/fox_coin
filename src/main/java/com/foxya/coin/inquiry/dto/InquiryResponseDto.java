package com.foxya.coin.inquiry.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.foxya.coin.inquiry.enums.InquiryStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class InquiryResponseDto {
    private Long id;
    private String subject;
    private String content;
    private String email;
    private InquiryStatus status;
    private LocalDateTime createdAt;
}

