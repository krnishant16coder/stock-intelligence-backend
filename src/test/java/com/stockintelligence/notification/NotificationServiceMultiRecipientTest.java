package com.stockintelligence.notification;

import com.stockintelligence.common.AppProperties;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.util.List;
import java.util.Properties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceMultiRecipientTest {

    @Mock
    JavaMailSender mailSender;

    @Mock
    NotificationLogRepository repository;

    AppProperties properties;
    NotificationService service;

    @BeforeEach
    void setUp() {
        properties = new AppProperties();
        properties.getNotifications().setEnabled(true);
        properties.getNotifications().setFrom("stock-intelligence@localhost");
        service = new NotificationService(mailSender, repository, properties);
    }

    private void stubMimeMessage() {
        when(mailSender.createMimeMessage())
                .thenAnswer(inv -> new MimeMessage(Session.getInstance(new Properties())));
    }

    private List<String> sentTo() throws Exception {
        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());
        return List.of(captor.getValue().getAllRecipients()).stream()
                .map(Object::toString)
                .toList();
    }

    @Test
    void singleAddressBehavesAsBefore() throws Exception {
        stubMimeMessage();
        properties.getNotifications().setDefaultRecipient("a@gmail.com");

        service.sendTestEmail();

        assertThat(sentTo()).containsExactly("a@gmail.com");
        ArgumentCaptor<NotificationLog> log = ArgumentCaptor.forClass(NotificationLog.class);
        verify(repository).save(log.capture());
        assertThat(log.getValue().getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(log.getValue().getRecipient()).isEqualTo("a@gmail.com");
    }

    @Test
    void twoCommaSeparatedAddressesBothMailed() throws Exception {
        stubMimeMessage();
        properties.getNotifications()
                .setDefaultRecipient("gulshanshane108@gmail.com, krnishant16@gmail.com");

        NotificationService.TestMailResult result = service.sendTestEmail();

        assertThat(sentTo()).containsExactlyInAnyOrder(
                "gulshanshane108@gmail.com", "krnishant16@gmail.com");
        assertThat(result.accepted()).isTrue();
        ArgumentCaptor<NotificationLog> log = ArgumentCaptor.forClass(NotificationLog.class);
        verify(repository).save(log.capture());
        assertThat(log.getValue().getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(log.getValue().getRecipient())
                .isEqualTo("gulshanshane108@gmail.com,krnishant16@gmail.com");
    }

    @Test
    void blankEntriesAreIgnored() {
        assertThat(NotificationService.resolveRecipients(" a@gmail.com ,,  ,b@gmail.com "))
                .containsExactly("a@gmail.com", "b@gmail.com");
        assertThat(NotificationService.resolveRecipients("   ")).isEmpty();
        assertThat(NotificationService.resolveRecipients(null)).isEmpty();
    }

    @Test
    void blankConfigIsSkippedWithoutSmtpAttempt() {
        properties.getNotifications().setDefaultRecipient("  ,  ");

        NotificationService.TestMailResult result = service.sendTestEmail();

        assertThat(result.accepted()).isFalse();
        verify(mailSender, never()).send((MimeMessage) any());
        ArgumentCaptor<NotificationLog> log = ArgumentCaptor.forClass(NotificationLog.class);
        verify(repository).save(log.capture());
        assertThat(log.getValue().getStatus()).isEqualTo(NotificationStatus.SKIPPED);
    }

    @Test
    void disabledNotificationsAreSkipped() {
        properties.getNotifications().setEnabled(false);
        properties.getNotifications().setDefaultRecipient("a@gmail.com,b@gmail.com");

        NotificationService.TestMailResult result = service.sendTestEmail();

        assertThat(result.accepted()).isFalse();
        verify(mailSender, never()).send((MimeMessage) any());
    }

    @Test
    void smtpFailureIsReportedAsNotAccepted() throws Exception {
        stubMimeMessage();
        properties.getNotifications().setDefaultRecipient("a@gmail.com");
        doThrow(new RuntimeException("535 Authentication failed"))
                .when(mailSender).send((MimeMessage) any());

        NotificationService.TestMailResult result = service.sendTestEmail();

        assertThat(result.accepted()).isFalse();
        ArgumentCaptor<NotificationLog> log = ArgumentCaptor.forClass(NotificationLog.class);
        verify(repository).save(log.capture());
        assertThat(log.getValue().getStatus()).isEqualTo(NotificationStatus.FAILED);
    }

    @Test
    void fromCarriesDisplayName() throws Exception {
        stubMimeMessage();
        properties.getNotifications().setDefaultRecipient("a@gmail.com");

        service.sendTestEmail();

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());
        assertThat(((jakarta.mail.internet.InternetAddress) captor.getValue().getFrom()[0])
                .getPersonal()).isEqualTo("Stock Intelligence");
    }
}
