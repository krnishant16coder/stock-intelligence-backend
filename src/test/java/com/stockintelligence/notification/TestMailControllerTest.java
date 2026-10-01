package com.stockintelligence.notification;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TestMailController.class)
class TestMailControllerTest {

    @Autowired
    MockMvc mvc;

    @MockBean
    NotificationService notifications;

    @Test
    void sendsTestMailToConfiguredRecipient() throws Exception {
        when(notifications.sendTestEmail()).thenReturn(
                new NotificationService.TestMailResult("krnishant16@gmail.com",
                        "Test mail — Stock Intelligence", true));
        mvc.perform(post("/api/admin/test-mail"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.recipient").value("krnishant16@gmail.com"))
                .andExpect(jsonPath("$.accepted").value(true));
        verify(notifications).sendTestEmail();
    }

    @Test
    void reportsWhenDeliveryNotAttempted() throws Exception {
        when(notifications.sendTestEmail()).thenReturn(
                new NotificationService.TestMailResult("", "Test mail — Stock Intelligence", false));
        mvc.perform(post("/api/admin/test-mail"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.accepted").value(false));
    }

    @Test
    void testMailTemplateMentionsRegularMailTypes() {
        MailTemplate.MailBodies bodies = MailTemplate.testMail("krnishant16@gmail.com");
        assertThat(bodies.plain()).contains("krnishant16@gmail.com");
        assertThat(bodies.html()).contains("EOD market-close digest");
    }
}
