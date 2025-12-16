package com.foxya.coin.common.utils;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mail.LoginOption;
import io.vertx.ext.mail.MailClient;
import io.vertx.ext.mail.MailConfig;
import io.vertx.ext.mail.MailMessage;
import io.vertx.ext.mail.StartTLSOptions;
import lombok.extern.slf4j.Slf4j;

import java.security.SecureRandom;

/**
 * 이메일 전송 유틸 (SMTP 연동)
 */
@Slf4j
public class EmailService {

    private final String fromAddress;
    private final MailClient mailClient;
    private final boolean smtpEnabled;

    public EmailService(Vertx vertx, JsonObject smtpConfig) {
        if (smtpConfig != null && smtpConfig.containsKey("host") && !smtpConfig.getString("host").isEmpty()) {
            // SMTP 설정이 있으면 MailClient 생성
            this.smtpEnabled = true;
            this.fromAddress = smtpConfig.getString("from", "no-reply@foxya.com");
            
            MailConfig mailConfig = new MailConfig();
            mailConfig.setHostname(smtpConfig.getString("host"));
            mailConfig.setPort(smtpConfig.getInteger("port", 587));
            
            // StartTLS 설정 (enum 변환)
            String starttlsStr = smtpConfig.getString("starttls", "OPTIONAL").toUpperCase();
            StartTLSOptions starttls = switch (starttlsStr) {
                case "NONE" -> StartTLSOptions.DISABLED;
                case "REQUIRED" -> StartTLSOptions.REQUIRED;
                default -> StartTLSOptions.OPTIONAL;
            };
            mailConfig.setStarttls(starttls);
            
            // Login 설정 (enum 변환)
            String loginStr = smtpConfig.getString("login", "LOGIN").toUpperCase();
            LoginOption login = "NONE".equals(loginStr) ? LoginOption.NONE : LoginOption.REQUIRED;
            mailConfig.setLogin(login);
            
            mailConfig.setUsername(smtpConfig.getString("username", ""));
            mailConfig.setPassword(smtpConfig.getString("password", ""));
            mailConfig.setAuthMethods(smtpConfig.getString("authMethods", "PLAIN"));
            
            this.mailClient = MailClient.createShared(vertx, mailConfig);
            log.info("[EmailService] SMTP enabled. Host: {}, Port: {}, From: {}", 
                smtpConfig.getString("host"), smtpConfig.getInteger("port", 587), fromAddress);
        } else {
            // SMTP 설정이 없으면 로그만 출력
            this.smtpEnabled = false;
            this.fromAddress = smtpConfig != null ? smtpConfig.getString("from", "no-reply@foxya.com") : "no-reply@foxya.com";
            this.mailClient = null;
            log.warn("[EmailService] SMTP not configured. Email sending will be logged only.");
        }
    }

    /**
     * 이메일 인증 코드 발송
     */
    public Future<Void> sendVerificationCode(String email, String code) {
        log.info("[EmailService] sendVerificationCode called. smtpEnabled: {}, mailClient: {}, to: {}, code: {}", 
            smtpEnabled, mailClient != null, email, code);
        
        if (!smtpEnabled || mailClient == null) {
            log.warn("[EmailService] SMTP disabled or mailClient is null. Sending verification code (SMTP disabled). from: {}, to: {}, code: {}", 
                fromAddress, email, code);
            return Future.succeededFuture();
        }

        log.info("[EmailService] Preparing to send email. from: {}, to: {}", fromAddress, email);
        
        MailMessage message = new MailMessage();
        message.setFrom(fromAddress);
        message.setTo(email);
        message.setSubject("[Foxya] 이메일 인증 코드");
        message.setHtml(getEmailTemplate(code));

        log.info("[EmailService] Sending mail via SMTP. Host: {}, Port: {}", 
            mailClient.getClass().getName(), "checking...");
        
        Future<Void> sendFuture = mailClient.sendMail(message)
            .map(result -> {
                log.info("[EmailService] Verification code sent successfully. to: {}, messageId: {}", 
                    email, result != null ? result.getMessageID() : "N/A");
                return (Void) null;
            });
        
        return sendFuture.recover(throwable -> {
            log.error("[EmailService] Failed to send verification code. to: {}, error: {}, cause: {}", 
                email, throwable.getMessage(), throwable.getClass().getName(), throwable);
            // 실패해도 Future는 성공으로 반환 (사용자 경험을 위해)
            return Future.succeededFuture((Void) null);
        });
    }

    /**
     * 이메일 템플릿 생성
     */
    private String getEmailTemplate(String code) {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <style>
                    body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; }
                    .container { max-width: 600px; margin: 0 auto; padding: 20px; }
                    .header { background-color: #4CAF50; color: white; padding: 20px; text-align: center; }
                    .content { padding: 20px; background-color: #f9f9f9; }
                    .code-box { background-color: #fff; border: 2px dashed #4CAF50; padding: 20px; text-align: center; margin: 20px 0; }
                    .code { font-size: 32px; font-weight: bold; color: #4CAF50; letter-spacing: 5px; }
                    .footer { text-align: center; padding: 20px; color: #666; font-size: 12px; }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header">
                        <h1>Foxya 이메일 인증</h1>
                    </div>
                    <div class="content">
                        <p>안녕하세요,</p>
                        <p>Foxya 서비스 이메일 인증을 위한 인증 코드입니다.</p>
                        <div class="code-box">
                            <div class="code">%s</div>
                        </div>
                        <p>위 인증 코드를 입력하여 이메일 인증을 완료해주세요.</p>
                        <p><strong>인증 코드는 10분간 유효합니다.</strong></p>
                        <p>본인이 요청하지 않은 경우 이 메일을 무시하셔도 됩니다.</p>
                    </div>
                    <div class="footer">
                        <p>© 2024 Foxya. All rights reserved.</p>
                    </div>
                </div>
            </body>
            </html>
            """.formatted(code);
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


