package com.foxya.coin.inquiry.entities;

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
public class Inquiry {
    private Long id;
    private Long userId;
    private String subject;
    private String content;
    private String email;
    private InquiryStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

