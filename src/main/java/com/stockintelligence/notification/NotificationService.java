package com.stockintelligence.notification;

import com.stockintelligence.alert.Alert;
import com.stockintelligence.common.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Email notifications (V1 channel). Every attempt — sent, failed or skipped —
 * is recorded in {@code notification_logs}.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final JavaMailSender mailSender;
    private final NotificationLogRepository repository;
    private final AppProperties properties;

    public NotificationService(JavaMailSender mailSender, NotificationLogRepository repository,
                               AppProperties properties) {
        this.mailSender = mailSender;
        this.repository = repository;
        this.properties = properties;
    }

    @Transactional
    public void sendAlertEmail(Alert alert) {
        String subject = "[%s] %s — %s".formatted(alert.getSeverity(), alert.getStock().getSymbol(),
                alert.getAlertType());
        send(alert, subject, """
                Stock: %s (%s, %s)
                Severity: %s
                Type: %s
                Time: %s

                %s
                """.formatted(alert.getStock().getSymbol(), alert.getStock().getCompanyName(),
                alert.getStock().getExchange(), alert.getSeverity(), alert.getAlertType(),
                alert.getCreatedAt(), alert.getMessage()));
    }

    @Transactional
    public void sendReportEmail(String watchlistName, Long reportId, String summary) {
        send(null, "Stock Intelligence report — " + watchlistName + " (#" + reportId + ")",
                "Watchlist: %s\nReport: #%d\n\n%s\n\nView: GET /api/reports/%d\n".formatted(
                        watchlistName, reportId, summary, reportId));
    }

    private void send(Alert alert, String subject, String body) {
        NotificationLog entry = new NotificationLog();
        entry.setAlert(alert);
        entry.setChannel(NotificationChannel.EMAIL);
        entry.setSubject(subject);
        String recipient = properties.getNotifications().getDefaultRecipient();
        entry.setRecipient(recipient);

        if (!properties.getNotifications().isEnabled() || recipient == null || recipient.isBlank()) {
            entry.setStatus(NotificationStatus.SKIPPED);
            entry.setErrorMessage("Email disabled or no recipient configured");
            repository.save(entry);
            log.info("Notification skipped (no recipient/disabled): {}", subject);
            return;
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(properties.getNotifications().getFrom());
            message.setTo(recipient);
            message.setSubject(subject);
            message.setText(body);
            mailSender.send(message);
            entry.setStatus(NotificationStatus.SENT);
            repository.save(entry);
            log.info("Notification sent to {}: {}", recipient, subject);
        } catch (Exception e) {
            entry.setStatus(NotificationStatus.FAILED);
            entry.setErrorMessage(e.getMessage());
            repository.save(entry);
            log.warn("Notification failed: {}", e.getMessage());
        }
    }
}
