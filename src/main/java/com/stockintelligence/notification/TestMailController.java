package com.stockintelligence.notification;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Manual mail delivery checks. Unlike analysis-triggered mails (sent only when
 * HIGH/CRITICAL risk is found), these endpoints always send so delivery can
 * be verified on demand:
 * <ul>
 *   <li>{@code POST /api/admin/test-mail} — static sample mail, no market data.</li>
 *   <li>{@code POST /api/admin/eod-digest} — real EOD market-close digest
 *   (analyzes every active watchlist and mails the combined portfolio report).</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/admin")
public class TestMailController {

    private final NotificationService notifications;
    private final com.stockintelligence.schedule.ScheduledTasks scheduledTasks;

    public TestMailController(NotificationService notifications,
                              com.stockintelligence.schedule.ScheduledTasks scheduledTasks) {
        this.notifications = notifications;
        this.scheduledTasks = scheduledTasks;
    }

    @PostMapping("/test-mail")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public TestMailResponse sendTestMail() {
        NotificationService.TestMailResult result = notifications.sendTestEmail();
        return new TestMailResponse(result.recipient(), result.subject(), result.accepted());
    }

    public record TestMailResponse(String recipient, String subject, boolean accepted) {}

    /** Manual trigger for the real EOD digest (same logic as 16:00 IST cron). */
    @PostMapping("/eod-digest")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public EodDigestResponse sendEodDigest() {
        scheduledTasks.sendEodDigest();
        return new EodDigestResponse(true, "EOD digest triggered — check inbox + notification_logs");
    }

    public record EodDigestResponse(boolean triggered, String message) {}
}
