package com.foxya.coin.notification.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationMessageLocalizerTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void resolve_formatsAmountLikeFieldsToSixDecimalsForMessages() throws Exception {
        String resolved = NotificationMessageLocalizer.resolve(
            "notifications.depositCompleted.message",
            Locale.KOREAN,
            OBJECT_MAPPER.readTree("""
                {
                  "amount": "12.123456789",
                  "currencyCode": "TRX"
                }
                """),
            "fallback"
        );

        assertThat(resolved).isEqualTo("12.123456 TRX 입금이 완료되었습니다.");
    }

    @Test
    void resolve_formatsFromToAndFeeFieldsWithoutMutatingNonAmountValues() throws Exception {
        String resolved = NotificationMessageLocalizer.resolve(
            "notifications.swapCompleted.message",
            Locale.KOREAN,
            OBJECT_MAPPER.readTree("""
                {
                  "amount": "1.23456789",
                  "currencyCode": "ETH",
                  "fromAmount": "1.23456789",
                  "toAmount": "345.6789123",
                  "fee": "0.0009001",
                  "txHash": "abc123"
                }
                """),
            "fallback"
        );

        assertThat(resolved).isEqualTo("1.234567 ETH 스왑이 완료되었습니다.");
        assertThat(NotificationMessageLocalizer.resolve(
            "notifications.depositCompleted.message",
            Locale.KOREAN,
            OBJECT_MAPPER.readTree("""
                {
                  "amount": "10.5000000",
                  "currencyCode": "KORI",
                  "txHash": "abc123"
                }
                """),
            "fallback"
        )).isEqualTo("10.5 KORI 입금이 완료되었습니다.");
    }
}
