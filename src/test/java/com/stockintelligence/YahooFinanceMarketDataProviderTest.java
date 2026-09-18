package com.stockintelligence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockintelligence.marketdata.HistoricalPrice;
import com.stockintelligence.marketdata.YahooFinanceMarketDataProvider;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class YahooFinanceMarketDataProviderTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void mapsNseBseAndUsSymbols() {
        assertThat(YahooFinanceMarketDataProvider.toVendorSymbol("RELIANCE", "NSE"))
                .isEqualTo("RELIANCE.NS");
        assertThat(YahooFinanceMarketDataProvider.toVendorSymbol("reliance", "nse_eq"))
                .isEqualTo("RELIANCE.NS");
        assertThat(YahooFinanceMarketDataProvider.toVendorSymbol("RELIANCE", "BSE"))
                .isEqualTo("RELIANCE.BO");
        assertThat(YahooFinanceMarketDataProvider.toVendorSymbol("IBM", "NYSE"))
                .isEqualTo("IBM");
        assertThat(YahooFinanceMarketDataProvider.toVendorSymbol("RELIANCE.BSE", "NSE"))
                .isEqualTo("RELIANCE.BSE");
    }

    @Test
    void parsesChartPayloadSkippingNullBars() throws Exception {
        String json = """
                {"chart":{"result":[{"timestamp":[1748835900,1748922300,1749008700],
                "indicators":{"quote":[{"open":[100.0,101.0,null],"high":[102.0,103.0,null],
                "low":[99.0,100.0,null],"close":[101.5,102.5,null],"volume":[1000,2000,null]}],
                "adjclose":[{"adjclose":[101.5,102.5,null]}]}}],"error":null}}""";
        List<HistoricalPrice> bars = YahooFinanceMarketDataProvider.parseChart(
                mapper.readTree(json), LocalDate.of(2020, 1, 1), LocalDate.of(2030, 1, 1));
        assertThat(bars).hasSize(2);
        assertThat(bars.get(0).close()).isEqualByComparingTo("101.5");
        assertThat(bars.get(1).volume()).isEqualTo(2000L);
    }

    @Test
    void parsesEmptyChartToEmptyList() throws Exception {
        List<HistoricalPrice> bars = YahooFinanceMarketDataProvider.parseChart(
                mapper.readTree("{\"chart\":{\"result\":[],\"error\":null}}"),
                null, null);
        assertThat(bars).isEmpty();
    }
}
