package com.kyra.notification;

import com.kyra.common.id.Ids;
import com.kyra.notification.api.NotificationApi;
import com.kyra.notification.api.NotificationType;
import com.kyra.notification.domain.RecordingEmailSender;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class NotificationServiceTest {

    @Inject
    NotificationApi notifications;

    @Inject
    RecordingEmailSender emailSender;

    private long countTo(String email) {
        return emailSender.sent().stream().filter(e -> e.to().equals(email)).count();
    }

    @Test
    void rendersTemplateAndSendsEmail() {
        String email = "verify-" + Ids.newUlid() + "@kyra.test";
        notifications.notifyEmail(email, NotificationType.EMAIL_VERIFICATION,
                Map.of("token", "abc123"), "dedup-" + Ids.newUlid());

        var sent = emailSender.sent().stream().filter(e -> e.to().equals(email)).findFirst().orElseThrow();
        assertTrue(sent.subject().contains("Verify"));
        assertTrue(sent.body().contains("abc123"), "token substituted into body");
    }

    @Test
    void deliveryIsIdempotentByDedupKey() {
        String email = "dup-" + Ids.newUlid() + "@kyra.test";
        String dedup = "dedup-" + Ids.newUlid();
        notifications.notifyEmail(email, NotificationType.DEPOSIT_CREDITED, Map.of("amount", "10 USDT"), dedup);
        notifications.notifyEmail(email, NotificationType.DEPOSIT_CREDITED, Map.of("amount", "10 USDT"), dedup);
        assertEquals(1, countTo(email), "same dedup key sends once");
    }

    @Test
    void missingTemplateVariableFailsAndSendsNothing() {
        String email = "bad-" + Ids.newUlid() + "@kyra.test";
        // EMAIL_VERIFICATION needs 'token' — omitting it must fail before sending
        assertThrows(IllegalArgumentException.class, () -> notifications.notifyEmail(
                email, NotificationType.EMAIL_VERIFICATION, Map.of(), "dedup-" + Ids.newUlid()));
        assertEquals(0, countTo(email), "nothing sent when rendering fails");
    }

    @Test
    void withdrawalTemplateSubstitutesAllVariables() {
        String email = "wd-" + Ids.newUlid() + "@kyra.test";
        notifications.notifyEmail(email, NotificationType.WITHDRAWAL_COMPLETED,
                Map.of("amount", "0.5 BTC", "txid", "0xabc"), "dedup-" + Ids.newUlid());
        var sent = emailSender.sent().stream().filter(e -> e.to().equals(email)).findFirst().orElseThrow();
        assertTrue(sent.body().contains("0.5 BTC") && sent.body().contains("0xabc"));
    }
}
