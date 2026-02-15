package com.foxya.coin.common.utils;

import com.foxya.coin.retry.EmailRetryJob;
import com.foxya.coin.retry.EmailRetryType;
import com.foxya.coin.retry.RetryQueuePublisher;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mail.LoginOption;
import io.vertx.ext.mail.MailAttachment;
import io.vertx.ext.mail.MailClient;
import io.vertx.ext.mail.MailConfig;
import io.vertx.ext.mail.MailMessage;
import io.vertx.ext.mail.StartTLSOptions;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.SecureRandom;

/**
 * 이메일 전송 유틸 (SMTP 연동)
 * 실패 시 Redis 재시도 큐 적재 → 재시도 → 최종 실패 시 DLQ/에러 처리
 */
@Slf4j
public class EmailService {

    private final String fromAddress;
    private final MailClient mailClient;
    private final boolean smtpEnabled;
    private final String ctaBaseUrl;
    private final Buffer logoBuffer;
    private final String logoContentId;
    private final RetryQueuePublisher retryQueue;

    private static final String DEFAULT_LOGO_PATH = "public/images/korion_b.png";
    private static final String DEFAULT_LOGO_CONTENT_ID = "korion-logo";

    public EmailService(Vertx vertx, JsonObject smtpConfig, JsonObject frontendConfig) {
        this(vertx, smtpConfig, frontendConfig, null);
    }

    public EmailService(Vertx vertx, JsonObject smtpConfig, JsonObject frontendConfig, RetryQueuePublisher retryQueue) {
        this.retryQueue = retryQueue;
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

        String baseUrl = frontendConfig != null ? frontendConfig.getString("baseUrl", "") : "";
        this.ctaBaseUrl = normalizeBaseUrl(baseUrl);
        String configuredLogoPath = smtpConfig != null ? smtpConfig.getString("logoPath", DEFAULT_LOGO_PATH) : DEFAULT_LOGO_PATH;
        this.logoBuffer = loadLogoBuffer(configuredLogoPath);
        this.logoContentId = DEFAULT_LOGO_CONTENT_ID;
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
        message.setSubject("[KORION] 이메일 인증 코드");
        message.setText(getVerificationEmailText(code));
        message.setHtml(getEmailTemplate(code));
        addLogoInlineAttachmentIfAvailable(message);

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
            if (retryQueue != null) {
                retryQueue.enqueueEmailRetry(EmailRetryType.VERIFICATION_CODE, email, code);
            }
            return Future.succeededFuture((Void) null);
        });
    }

    /**
     * 재시도 큐에서 꺼낸 작업 실행 (실패 시 호출자가 재적재/DLQ 처리)
     */
    public Future<Void> processRetryJob(EmailRetryJob job) {
        if (job.getType() == EmailRetryType.VERIFICATION_CODE) {
            return doSendVerificationCode(job.getEmail(), job.getCodeOrPassword());
        }
        if (job.getType() == EmailRetryType.TEMP_PASSWORD) {
            return doSendTemporaryPassword(job.getEmail(), job.getCodeOrPassword());
        }
        return Future.failedFuture("Unknown email retry type: " + job.getType());
    }

    /**
     * 인증 코드 메일 실제 발송 (재시도/큐 로직 없음)
     */
    private Future<Void> doSendVerificationCode(String email, String code) {
        if (!smtpEnabled || mailClient == null) {
            return Future.failedFuture("SMTP not configured");
        }
        MailMessage message = new MailMessage();
        message.setFrom(fromAddress);
        message.setTo(email);
        message.setSubject("[KORION] 이메일 인증 코드");
        message.setText(getVerificationEmailText(code));
        message.setHtml(getEmailTemplate(code));
        addLogoInlineAttachmentIfAvailable(message);
        return mailClient.sendMail(message).map(ok -> (Void) null);
    }

    /**
     * 이메일 템플릿 생성
     */
    private String getEmailTemplate(String code) {
        return """
            <!DOCTYPE html>
            <html lang="ko">
            <head>
              <meta charset="UTF-8">
              <meta name="viewport" content="width=device-width, initial-scale=1.0">
              <meta http-equiv="x-apple-disable-message-reformatting">
              <title>KORION 이메일 인증</title>
            </head>
            <body style="margin:0; padding:0; background-color:#0d0a1b;">
              <div style="display:none; max-height:0; overflow:hidden; opacity:0; color:#0d0a1b;">
                KORION WALLET 이메일 인증 코드입니다.
              </div>
              <table role="presentation" cellpadding="0" cellspacing="0" width="100%%" style="background-color:#0d0a1b; padding:24px 0;">
                <tr>
                  <td align="center">
                    <table role="presentation" cellpadding="0" cellspacing="0" width="600" style="width:600px; max-width:600px; background-color:#111827; border:1px solid #1f2937; border-radius:18px; overflow:hidden;">
                      <tr>
                        <td style="padding:28px 32px; background:linear-gradient(135deg,#6c5cff,#8b5cf6); color:#ffffff; font-family:Helvetica,Arial,sans-serif;">
                          %s
                          <div style="font-size:14px; letter-spacing:2px; text-transform:uppercase; opacity:0.9;">KORION WALLET</div>
                          <div style="font-size:24px; font-weight:700; margin-top:6px;">이메일 인증 코드</div>
                        </td>
                      </tr>
                      <tr>
                        <td style="padding:28px 32px; font-family:Helvetica,Arial,sans-serif; color:#e5e7eb; font-size:15px; line-height:1.6;">
                          <p style="margin:0 0 10px;">안녕하세요,</p>
                          <p style="margin:0 0 16px;">아래 인증 코드를 입력하여 이메일 인증을 완료해주세요.</p>
                          <div style="text-align:center; margin:20px 0 22px;">
                            <div style="display:inline-block; padding:14px 22px; background-color:#0b1020; border:1px dashed #7c3aed; border-radius:12px;">
                              <span style="font-size:28px; font-weight:700; letter-spacing:6px; color:#c4b5fd;">%s</span>
                            </div>
                          </div>
                          <p style="margin:0 0 6px; color:#cbd5f5;">인증 코드는 <strong>10분간 유효</strong>합니다.</p>
                          <p style="margin:0; color:#9ca3af;">본인이 요청하지 않았다면 이 메일을 무시해 주세요.</p>
                          %s
                        </td>
                      </tr>
                      <tr>
                        <td style="padding:18px 32px 24px; font-family:Helvetica,Arial,sans-serif; color:#9ca3af; font-size:12px; text-align:center;">
                          © 2026 KORION. All rights reserved.
                        </td>
                      </tr>
                    </table>
                    <div style="font-family:Helvetica,Arial,sans-serif; color:#6b7280; font-size:11px; margin-top:14px;">
                      이 메일은 발신 전용입니다.
                    </div>
                  </td>
                </tr>
              </table>
            </body>
            </html>
            """.formatted(buildLogoImgHtml(), code, buildCtaButtonHtml("KORION WALLET 열기", ctaBaseUrl));
    }

    /**
     * 6자리 숫자 인증 코드 생성
     */
    public String generateVerificationCode() {
        SecureRandom random = new SecureRandom();
        int number = 100000 + random.nextInt(900000); // 100000 ~ 999999
        return String.valueOf(number);
    }

    private static final String TEMP_PASSWORD_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789";
    private static final int TEMP_PASSWORD_LENGTH = 10;

    /**
     * 임시 비밀번호 생성 (영문+숫자, 1,I,0,O 제외)
     */
    public String generateTemporaryPassword() {
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder(TEMP_PASSWORD_LENGTH);
        for (int i = 0; i < TEMP_PASSWORD_LENGTH; i++) {
            sb.append(TEMP_PASSWORD_CHARS.charAt(random.nextInt(TEMP_PASSWORD_CHARS.length())));
        }
        return sb.toString();
    }

    /**
     * 임시 비밀번호 이메일 발송
     * SMTP 미설정 또는 발송 실패 시 Future.failedFuture 반환
     */
    public Future<Void> sendTemporaryPassword(String email, String tempPassword) {
        if (!smtpEnabled || mailClient == null) {
            log.warn("[EmailService] SMTP disabled or mailClient is null. Cannot send temporary password to: {}", email);
            return Future.failedFuture(new IllegalStateException("임시 비밀번호 발송에 실패했습니다."));
        }
        return doSendTemporaryPassword(email, tempPassword)
            .recover(throwable -> {
                log.error("[EmailService] Failed to send temporary password. to: {}, error: {}", email, throwable.getMessage(), throwable);
                if (retryQueue != null) {
                    retryQueue.enqueueEmailRetry(EmailRetryType.TEMP_PASSWORD, email, tempPassword);
                }
                return Future.failedFuture(new IllegalStateException("임시 비밀번호 발송에 실패했습니다."));
            });
    }

    /**
     * 임시 비밀번호 메일 실제 발송 (재시도/큐 로직 없음)
     */
    private Future<Void> doSendTemporaryPassword(String email, String tempPassword) {
        if (!smtpEnabled || mailClient == null) {
            return Future.failedFuture("SMTP not configured");
        }
        MailMessage message = new MailMessage();
        message.setFrom(fromAddress);
        message.setTo(email);
        message.setSubject("[KORION] 임시 비밀번호 안내");
        message.setText(getTemporaryPasswordText(tempPassword));
        message.setHtml(getTemporaryPasswordTemplate(tempPassword));
        addLogoInlineAttachmentIfAvailable(message);
        return mailClient.sendMail(message).map(ok -> (Void) null);
    }

    private String getTemporaryPasswordTemplate(String tempPassword) {
        return """
            <!DOCTYPE html>
            <html lang="ko">
            <head>
              <meta charset="UTF-8">
              <meta name="viewport" content="width=device-width, initial-scale=1.0">
              <meta http-equiv="x-apple-disable-message-reformatting">
              <title>KORION 임시 비밀번호</title>
            </head>
            <body style="margin:0; padding:0; background-color:#0d0a1b;">
              <div style="display:none; max-height:0; overflow:hidden; opacity:0; color:#0d0a1b;">
                KORION WALLET 임시 비밀번호 안내입니다.
              </div>
              <table role="presentation" cellpadding="0" cellspacing="0" width="100%%" style="background-color:#0d0a1b; padding:24px 0;">
                <tr>
                  <td align="center">
                    <table role="presentation" cellpadding="0" cellspacing="0" width="600" style="width:600px; max-width:600px; background-color:#111827; border:1px solid #1f2937; border-radius:18px; overflow:hidden;">
                      <tr>
                        <td style="padding:28px 32px; background:linear-gradient(135deg,#6c5cff,#8b5cf6); color:#ffffff; font-family:Helvetica,Arial,sans-serif;">
                          %s
                          <div style="font-size:14px; letter-spacing:2px; text-transform:uppercase; opacity:0.9;">KORION WALLET</div>
                          <div style="font-size:24px; font-weight:700; margin-top:6px;">임시 비밀번호 안내</div>
                        </td>
                      </tr>
                      <tr>
                        <td style="padding:28px 32px; font-family:Helvetica,Arial,sans-serif; color:#e5e7eb; font-size:15px; line-height:1.6;">
                          <p style="margin:0 0 10px;">안녕하세요,</p>
                          <p style="margin:0 0 16px;">요청하신 임시 비밀번호입니다. 로그인 후 반드시 비밀번호를 변경해주세요.</p>
                          <div style="text-align:center; margin:20px 0 22px;">
                            <div style="display:inline-block; padding:12px 18px; background-color:#0b1020; border:1px dashed #7c3aed; border-radius:12px;">
                              <span style="font-size:22px; font-weight:700; letter-spacing:3px; color:#c4b5fd;">%s</span>
                            </div>
                          </div>
                          <p style="margin:0; color:#9ca3af;">보안을 위해 로그인 후 즉시 비밀번호를 변경하시기 바랍니다.</p>
                          %s
                        </td>
                      </tr>
                      <tr>
                        <td style="padding:18px 32px 24px; font-family:Helvetica,Arial,sans-serif; color:#9ca3af; font-size:12px; text-align:center;">
                          © 2026 KORION. All rights reserved.
                        </td>
                      </tr>
                    </table>
                    <div style="font-family:Helvetica,Arial,sans-serif; color:#6b7280; font-size:11px; margin-top:14px;">
                      이 메일은 발신 전용입니다.
                    </div>
                  </td>
                </tr>
              </table>
            </body>
            </html>
            """.formatted(buildLogoImgHtml(), tempPassword, buildCtaButtonHtml("로그인하기", buildCtaUrl("/login")));
    }

    private String getVerificationEmailText(String code) {
        String ctaLine = buildCtaTextLine("앱 열기", ctaBaseUrl);
        return """
            KORION WALLET 이메일 인증 코드

            안녕하세요.
            아래 인증 코드를 입력하여 이메일 인증을 완료해주세요.

            인증 코드: %s

            인증 코드는 10분간 유효합니다.
            본인이 요청하지 않았다면 이 메일을 무시해 주세요.

            %s

            © 2026 KORION. All rights reserved.
            """.formatted(code, ctaLine);
    }

    private String getTemporaryPasswordText(String tempPassword) {
        String ctaLine = buildCtaTextLine("로그인", buildCtaUrl("/login"));
        return """
            KORION WALLET 임시 비밀번호 안내

            안녕하세요.
            요청하신 임시 비밀번호입니다. 로그인 후 반드시 비밀번호를 변경해주세요.

            임시 비밀번호: %s

            보안을 위해 로그인 후 즉시 비밀번호를 변경하시기 바랍니다.

            %s

            © 2026 KORION. All rights reserved.
            """.formatted(tempPassword, ctaLine);
    }

    private void addLogoInlineAttachmentIfAvailable(MailMessage message) {
        if (logoBuffer == null || logoBuffer.length() == 0) {
            return;
        }
        MailAttachment attachment = MailAttachment.create()
            .setData(logoBuffer)
            .setContentType("image/png")
            .setName("korion_b.png")
            .setDisposition("inline")
            .setContentId(logoContentId);
        message.setInlineAttachment(attachment);
    }

    private String buildLogoImgHtml() {
        if (logoBuffer == null || logoBuffer.length() == 0) {
            return "";
        }
        return """
          <div style="margin-bottom:10px;">
            <img src="cid:%s" width="120" alt="KORION" style="display:block; height:auto; border:0;">
          </div>
          """.formatted(logoContentId);
    }

    private String buildCtaButtonHtml(String label, String url) {
        if (url == null || url.isBlank()) {
            return "";
        }
        return """
          <div style="margin-top:22px;">
            <a href="%s" style="display:inline-block; background:#6c5cff; color:#ffffff; text-decoration:none; padding:12px 22px; border-radius:10px; font-weight:600;">
              %s
            </a>
          </div>
          """.formatted(url, label);
    }

    private String buildCtaTextLine(String label, String url) {
        if (url == null || url.isBlank()) {
            return "";
        }
        return label + ": " + url;
    }

    private String normalizeBaseUrl(String baseUrl) {
        if (baseUrl == null) {
            return "";
        }
        return baseUrl.replaceAll("/+$", "");
    }

    private String buildCtaUrl(String path) {
        if (ctaBaseUrl == null || ctaBaseUrl.isBlank()) {
            return "";
        }
        if (path == null || path.isBlank()) {
            return ctaBaseUrl;
        }
        if (path.startsWith("/")) {
            return ctaBaseUrl + path;
        }
        return ctaBaseUrl + "/" + path;
    }

    private Buffer loadLogoBuffer(String logoPath) {
        if (logoPath == null || logoPath.isBlank()) {
            return null;
        }
        try {
            Path path = Paths.get(logoPath);
            if (!path.isAbsolute()) {
                path = Paths.get(logoPath).toAbsolutePath();
            }
            if (!Files.exists(path)) {
                log.warn("[EmailService] Logo not found at path: {}", path);
                return null;
            }
            return Buffer.buffer(Files.readAllBytes(path));
        } catch (Exception e) {
            log.warn("[EmailService] Failed to load logo image: {}", e.getMessage());
            return null;
        }
    }
}
