package com.foxya.coin.common.utils;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import lombok.extern.slf4j.Slf4j;

import java.security.SecureRandom;

/**
 * 이메일 전송 유틸 (현재는 SMTP 연동 대신 로그 출력만 수행)
 * 실제 SMTP 연동이 필요하면 이 클래스를 확장하면 됩니다.
 */
@Slf4j
public class EmailService {

    private final String fromAddress;

    public EmailService(JsonObject smtpConfig) {
        // 실제 SMTP 연동 시 host/port/user/password 등을 사용
        this.fromAddress = smtpConfig != null ? smtpConfig.getString("from", "no-reply@foxya.com") : "no-reply@foxya.com";
    }

    /**
     * 이메일 인증 코드 발송 (현재는 로그만 출력)
     */
    public Future<Void> sendVerificationCode(String email, String code) {
        log.info("[EmailService] Sending verification code to email. from: {}, to: {}, code: {}", fromAddress, email, code);
        // TODO: SMTP 연동 시 이 위치에서 실제 메일 전송 로직 추가
        return Future.succeededFuture();
    }

    /**
     * 6자리 숫자 인증 코드 생성
     */
    public String generateVerificationCode() {
        SecureRandom random = new SecureRandom();
        int number = 100000 + random.nextInt(900000); // 100000 ~ 999999
        return String.valueOf(number);
    }
}


