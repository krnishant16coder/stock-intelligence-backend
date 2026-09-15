package com.stockintelligence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockintelligence.analysis.StockAnalysisResult;
import com.stockintelligence.report.AnalysisReport;
import com.stockintelligence.report.AnalysisReportRepository;
import com.stockintelligence.report.ReportResponse;
import com.stockintelligence.report.ReportService;
import com.stockintelligence.report.StockAnalysisRepository;
import com.stockintelligence.report.TriggerType;
import com.stockintelligence.stock.Stock;
import com.stockintelligence.stock.StockRepository;
import com.stockintelligence.watchlist.Watchlist;
import com.stockintelligence.watchlist.WatchlistRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import({ReportService.class, ReportPersistenceTest.TestConfig.class})
class ReportPersistenceTest {

    @TestConfiguration
    static class TestConfig {
        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }

    @Autowired
    StockRepository stocks;
    @Autowired
    WatchlistRepository watchlists;
    @Autowired
    AnalysisReportRepository reports;
    @Autowired
    StockAnalysisRepository analyses;
    @Autowired
    ReportService reportService;
    @Autowired
    ObjectMapper objectMapper;

    @Test
    void savesAndReadsReportWithAnalyses() {
        Stock stock = stocks.save(new Stock("INFY", "Infosys", "NSE"));
        Watchlist watchlist = watchlists.save(new Watchlist("IT"));
        StockAnalysisResult result = new StockAnalysisResult("HOLD", "MEDIUM", "STABLE",
                "STABLE", "NEUTRAL", "Steady quarter", List.of("Stable margins"), 0.7, false);

        ReportResponse saved = reportService.saveReport(watchlist, Instant.now().minusSeconds(3600),
                Instant.now(), "summary", TriggerType.MANUAL,
                List.of(new ReportService.AnalysisRow(stock, result, "{\"dataPoints\":30}")));

        assertThat(saved.id()).isNotNull();
        assertThat(saved.analyses()).hasSize(1);
        assertThat(saved.analyses().get(0).keyReasons()).contains("Stable margins");

        ReportResponse fetched = reportService.getReport(saved.id());
        assertThat(fetched.watchlistName()).isEqualTo("IT");

        assertThat(reportService.previousSignal(stock.getId())).isEqualTo("HOLD");
        assertThat(reportService.latestForStock(stock.getId(), 5)).hasSize(1);
        assertThat(reportService.list(watchlist.getId())).hasSize(1);
    }

    @Test
    void enforcesUniqueStockPerExchange() {
        stocks.save(new Stock("TCS", "Tata Consultancy Services", "NSE"));
        try {
            stocks.saveAndFlush(new Stock("TCS", "Duplicate", "NSE"));
            assertThat(false).as("expected unique violation").isTrue();
        } catch (Exception e) {
            assertThat(e.getMessage()).isNotNull();
        }
    }
}
