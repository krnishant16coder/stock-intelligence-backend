package com.stockintelligence.alert;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AlertRuleController.class)
class AlertRuleControllerTest {

    @Autowired
    MockMvc mvc;

    @MockBean
    AlertRuleService service;

    private static AlertRuleResponse response(Long id, double thresholdPct, PriceDirection direction) {
        return new AlertRuleResponse(id, 3L, "TCS", thresholdPct, direction,
                Severity.HIGH, 24, true, null, Instant.now());
    }

    @Test
    void listsRules() throws Exception {
        when(service.list(null)).thenReturn(List.of(response(1L, 3.0, PriceDirection.BOTH)));
        mvc.perform(get("/api/alert-rules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].symbol").value("TCS"));
    }

    @Test
    void createsRule() throws Exception {
        when(service.create(any())).thenReturn(response(2L, 2.0, PriceDirection.UP));
        mvc.perform(post("/api/alert-rules").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"stockId\":3,\"thresholdPct\":2.0,\"direction\":\"UP\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.thresholdPct").value(2.0));
    }

    @Test
    void rejectsInvalidThreshold() throws Exception {
        mvc.perform(post("/api/alert-rules").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"stockId\":3,\"thresholdPct\":500}"))
                .andExpect(status().isBadRequest());
    }
}
