package com.stockintelligence.notification;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Manual mail delivery check. Unlike analysis-triggered mails (sent only when
 * HIGH/CRITICAL risk is found), this endpoint always sends a sample mail to
 * the configured recipient so delivery can be verified on demand.
 */
@RestController
@RequestMapping("/api/admin")
public class TestMailController {

    private final NotificationService notifications;

    public TestMailController(NotificationService notifications) {
        this.notifications = notifications;
    }

    @PostMapping("/test-mail")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public TestMailResponse sendTestMail() {
        NotificationService.TestMailResult result = notifications.sendTestEmail();
        return new TestMailResponse(result.recipient(), result.subject(), result.accepted());
    }

    public record TestMailResponse(String recipient, String subject, boolean accepted) {}
}
