package com.stockintelligence;

import com.stockintelligence.analysis.StockAnalysisResult;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StockAnalysisResultTest {

    @Test
    void normalizesValidResult() {
        StockAnalysisResult r = new StockAnalysisResult("hold", "high", "Up", "", "Positive",
                "Good outlook", List.of("Reason 1"), 0.8, null).normalized();
        assertThat(r.signal()).isEqualTo("HOLD");
        assertThat(r.riskLevel()).isEqualTo("HIGH");
        assertThat(r.fundamentalTrend()).isEqualTo("UNKNOWN");
        assertThat(r.criticalAlert()).isFalse();
    }

    @Test
    void rejectsUnknownSignal() {
        StockAnalysisResult r = new StockAnalysisResult("STRONG_BUY", "LOW", null, null, null,
                "x", List.of(), 0.5, false);
        assertThatThrownBy(r::normalized).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void clampsConfidence() {
        StockAnalysisResult r = new StockAnalysisResult("HOLD", "LOW", null, null, null,
                "x", List.of(), 42.0, false).normalized();
        assertThat(r.confidence()).isEqualTo(1.0);
    }
}
