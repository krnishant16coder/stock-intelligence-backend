package com.stockintelligence;

import com.stockintelligence.stock.StockController;
import com.stockintelligence.stock.StockResponse;
import com.stockintelligence.stock.StockService;
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

@WebMvcTest(StockController.class)
class StockControllerTest {

    @Autowired
    MockMvc mvc;

    @MockBean
    StockService stockService;

    @Test
    void listsStocks() throws Exception {
        when(stockService.list()).thenReturn(List.of(
                new StockResponse(1L, "RELIANCE", "Reliance Industries", "NSE", Instant.now(), Instant.now())));
        mvc.perform(get("/api/stocks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].symbol").value("RELIANCE"));
    }

    @Test
    void rejectsInvalidCreate() throws Exception {
        mvc.perform(post("/api/stocks").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"symbol\":\"\",\"companyName\":\"x\",\"exchange\":\"NSE\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createsStock() throws Exception {
        when(stockService.create(any())).thenReturn(
                new StockResponse(2L, "TCS", "Tata Consultancy Services", "NSE", Instant.now(), Instant.now()));
        mvc.perform(post("/api/stocks").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"symbol\":\"TCS\",\"companyName\":\"Tata Consultancy Services\",\"exchange\":\"NSE\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.symbol").value("TCS"));
    }
}
