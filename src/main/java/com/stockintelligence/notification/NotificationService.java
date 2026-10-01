package com.stockintelligence.notification;

import com.stockintelligence.alert.Alert;
import com.stockintelligence.common.AppProperties;
import com.stockintelligence.report.ReportResponse;
import com.stockintelligence.report.ReportService;
import jakarta.mail.internet.MimeMessage;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Email notifications (V1 channel). Every attempt — sent, failed or skipped —
 * is recorded in {@code notification_logs}. Mails go out as multipart/alternative
 * (plain text + HTML) so every client renders a human-readable version.
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
        MailTemplate.MailBodies bodies = MailTemplate.alert(alert);
        String subject = "[%s] %s — %s".formatted(alert.getSeverity(), alert.getStock().getSymbol(),
                alert.getAlertType());
        send(alert, subject, bodies.plain(), bodies.html());
    }

    @Transactional
    public void sendReportEmail(String watchlistName, Long reportId, String summary,
                                List<ReportService.AnalysisRow> rows) {
        MailTemplate.MailBodies bodies = MailTemplate.report(watchlistName, reportId, summary, rows,
                properties.getBaseUrl());
        send(null, "Stock Intelligence report — " + watchlistName + " (#" + reportId + ")",
                bodies.plain(), bodies.html());
    }

    /**
     * Midday roundup of today's MEDIUM alerts (HIGH/CRITICAL already mailed instantly).
     * Sends one combined mail; stays silent (no row, just a log line) when there is nothing.
     */
    @Transactional
    public void sendMediumRoundupEmail(String dateLabel, List<Alert> alerts) {
        MailTemplate.MailBodies bodies = MailTemplate.roundup(dateLabel, alerts);
        send(null, "Midday roundup — %s (%d medium signals)".formatted(dateLabel, alerts.size()),
                bodies.plain(), bodies.html());
    }

    /**
     * Daily market-close digest (whole portfolio). Unlike severity-gated report mails,
     * this always sends — it bypasses the HIGH/CRITICAL severity gate by design.
     * Still respects the enabled flag + recipient (logs SKIPPED when off).
     */
    @Transactional
    public void sendEodDigestEmail(String dateLabel, List<ReportResponse> reports) {
        int stocks = reports.stream().mapToInt(ReportResponse::stocksAnalyzed).sum();
        MailTemplate.MailBodies bodies = MailTemplate.digest(dateLabel, reports, properties.getBaseUrl());
        send(null, "EOD digest — %s (%d watchlists, %d stocks)".formatted(dateLabel, reports.size(), stocks),
                bodies.plain(), bodies.html());
    }

    private void send(Alert alert, String subject, String plainBody, String htmlBody) {
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
            MimeMessage mime = mailSender.createMimeMessage();
            MimeMessageHelper message = new MimeMessageHelper(mime, true, "UTF-8");
            message.setFrom(properties.getNotifications().getFrom());
            message.setTo(recipient);
            message.setSubject(subject);
            message.setText(plainBody, htmlBody);
            mailSender.send(mime);
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
