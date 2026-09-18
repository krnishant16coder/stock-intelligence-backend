package com.stockintelligence.news;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockintelligence.common.AppProperties;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

class NewsDataIoNewsProviderTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private NewsDataIoNewsProvider provider() {
        return new NewsDataIoNewsProvider(RestClient.builder(), mapper, new AppProperties());
    }

    @Test
    void providerNameIsNewsdata() {
        assertThat(provider().providerName()).isEqualTo("newsdata");
    }

    @Test
    void buildQueryUsesCompanyNameAndTruncatesTo100Chars() {
        assertThat(NewsDataIoNewsProvider.buildQuery("RELIANCE", "Reliance Industries"))
                .isEqualTo("Reliance Industries");
        assertThat(NewsDataIoNewsProvider.buildQuery("HDFCBANK", "  "))
                .isEqualTo("HDFCBANK stock");
        String longName = "A".repeat(150);
        assertThat(NewsDataIoNewsProvider.buildQuery("X", longName)).hasSize(100);
    }

    @Test
    void parsePubDateHandlesNewsdataFormatAndIso() {
        assertThat(NewsDataIoNewsProvider.parsePubDate("2026-09-11 05:32:04"))
                .isNotNull()
                .isEqualTo(java.time.Instant.parse("2026-09-11T05:32:04Z"));
        assertThat(NewsDataIoNewsProvider.parsePubDate("2026-09-11T05:32:04Z"))
                .isEqualTo(java.time.Instant.parse("2026-09-11T05:32:04Z"));
        assertThat(NewsDataIoNewsProvider.parsePubDate(null)).isNull();
        assertThat(NewsDataIoNewsProvider.parsePubDate("not-a-date")).isNull();
    }

    @Test
    void extractErrorMessageReadsResultsObject() throws Exception {
        JsonNode err = mapper.readTree("{\"status\":\"error\",\"results\":{\"message\":\"Rate limit exceeded\",\"code\":\"RateLimitExceeded\"}}");
        assertThat(NewsDataIoNewsProvider.extractErrorMessage(err)).contains("Rate limit");
        JsonNode unknown = mapper.readTree("{\"status\":\"error\"}");
        assertThat(NewsDataIoNewsProvider.extractErrorMessage(unknown)).isEqualTo("unknown error");
    }

    @Test
    void mapArticlesNormalizesFiltersAndCaps() throws Exception {
        JsonNode results = mapper.readTree("""
                [
                  {"title":"Reliance profit rises","description":"Q1 beat","link":"https://ex.com/1",
                   "source_name":"Economic Times","source_id":"economictimes","pubDate":"2026-09-11 05:32:04"},
                  {"title":"Old news","description":"d","link":"https://ex.com/old",
                   "source_name":"Mint","source_id":"mint","pubDate":"2026-01-01 00:00:00"},
                  {"title":"No link","description":"d","link":"","source_name":"Mint","pubDate":"2026-09-11 05:32:04"},
                  {"title":"Fallback source","description":null,"link":"https://ex.com/4",
                   "source_name":null,"source_id":"moneycontrol","pubDate":"2026-09-10 10:00:00"}
                ]
                """);
        LocalDateTime from = LocalDateTime.of(2026, 9, 1, 0, 0);
        LocalDateTime to = LocalDateTime.of(2026, 9, 15, 0, 0);

        List<NewsArticle> out = provider().mapArticles(results, from, to, 20);

        assertThat(out).hasSize(2);
        assertThat(out.get(0).getTitle()).isEqualTo("Reliance profit rises");
        assertThat(out.get(0).getUrl()).isEqualTo("https://ex.com/1");
        assertThat(out.get(0).getSource()).isEqualTo("Economic Times");
        assertThat(out.get(0).getDataSource()).isEqualTo("newsdata");
        assertThat(out.get(1).getSource()).isEqualTo("moneycontrol");
    }

    @Test
    void mapArticlesRespectsMax() throws Exception {
        JsonNode results = mapper.readTree("""
                [
                  {"title":"A","link":"https://ex.com/a","source_name":"S","pubDate":"2026-09-11 05:32:04"},
                  {"title":"B","link":"https://ex.com/b","source_name":"S","pubDate":"2026-09-11 05:32:04"}
                ]
                """);
        LocalDateTime from = LocalDateTime.of(2026, 9, 1, 0, 0);
        LocalDateTime to = LocalDateTime.of(2026, 9, 15, 0, 0);
        assertThat(provider().mapArticles(results, from, to, 1)).hasSize(1);
    }
}
