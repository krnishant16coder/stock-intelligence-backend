package com.stockintelligence.notification;

import com.stockintelligence.report.ReportResponse;
import com.stockintelligence.report.ReportService;
import com.stockintelligence.report.StockAnalysisResponse;
import com.stockintelligence.report.TriggerType;
import com.stockintelligence.analysis.StockAnalysisResult;
import com.stockintelligence.stock.Stock;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MailTemplateTest {

    private static ReportResponse report(long id, String watchlist) {
        StockAnalysisResponse a = new StockAnalysisResponse(1L, 3L, "TCS",
                "Tata <Consultancy> Services", "HOLD", "MEDIUM", "DOWNTREND",
                "WEAKENING", "NEGATIVE",
                "TCS shows a modest daily gain but continues a broader downtrend.",
                List.of("Monthly down 12%"), 0.6, false);
        Instant now = Instant.now();
        return new ReportResponse(id, 1L, watchlist, now, now, now,
                "Watchlist '" + watchlist + "': 1 analyzed.", 1, TriggerType.EOD, List.of(a));
    }

    @Test
    void digestIsHumanReadableHtmlWithFullSummary() {
        MailTemplate.MailBodies bodies =
                MailTemplate.digest("2026-09-30", List.of(report(71L, "TCS")), "https://api.example.com");

        assertThat(bodies.html()).contains("<html>", "Tata &lt;Consultancy&gt; Services");
        assertThat(bodies.html()).contains("Downtrend", "Weakening", "Negative");
        assertThat(bodies.html()).contains("https://api.example.com/api/reports/71");
        assertThat(bodies.html()).contains("broader downtrend");
        assertThat(bodies.html()).doesNotContain("price=", "fund=", "GET /api");
        assertThat(bodies.plain()).contains("TCS", "broader downtrend");
        assertThat(bodies.plain()).doesNotContain("price=", "GET /api");
    }

    @Test
    void digestFallsBackToRelativeLinkWithoutBaseUrl() {
        MailTemplate.MailBodies bodies =
                MailTemplate.digest("2026-09-30", List.of(report(71L, "TCS")), "");

        assertThat(bodies.html()).contains("/api/reports/71");
        assertThat(bodies.plain()).contains("/api/reports/71");
    }

    @Test
    void reportMailRendersRowsWithReasons() {        Stock stock = new Stock("INFY", "Infosys", "NSE");
        StockAnalysisResult result = new StockAnalysisResult("REVIEW", "MEDIUM", "VOLATILE",
                "UNKNOWN", "MIXED", "Mixed signals, review warranted.",
                List.of("Volume is low"), 0.6, false).normalized();
        var rows = List.of(new ReportService.AnalysisRow(stock, result, "{}"));

        MailTemplate.MailBodies bodies =
                MailTemplate.report("Tech", 9L, "1 analyzed.", rows, "https://api.example.com");

        assertThat(bodies.html()).contains("INFY", "Review", "Volume is low");
        assertThat(bodies.html()).contains("https://api.example.com/api/reports/9");
        assertThat(bodies.plain()).contains("Signal: Review | Risk: Medium");
    }

    @Test
    void roundupListsMediumAlerts() throws Exception {
        var ctor = com.stockintelligence.alert.Alert.class.getDeclaredConstructor();
        ctor.setAccessible(true);
        com.stockintelligence.alert.Alert alert = ctor.newInstance();
        alert.setStock(new Stock("TCS", "Tata Consultancy Services", "NSE"));
        alert.setAlertType(com.stockintelligence.alert.AlertType.NEGATIVE_EARNINGS);
        alert.setSeverity(com.stockintelligence.alert.Severity.MEDIUM);
        alert.setMessage("NEGATIVE_EARNINGS: earnings miss (Ndtv)");

        MailTemplate.MailBodies bodies =
                MailTemplate.roundup("2026-09-30", List.of(alert));

        assertThat(bodies.html()).contains("TCS", "Medium risk", "earnings miss");
        assertThat(bodies.html()).contains("16:00 market-close digest");
        assertThat(bodies.plain()).contains("TCS", "earnings miss");
    }
}
