package com.stockintelligence.analysis;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;

import static org.assertj.core.api.Assertions.assertThat;

class OpenAiPromptGuardTest {

    private static StockAnalysisInput input(List<String> news) {
        RuleMetrics m = new RuleMetrics(BigDecimal.valueOf(2500), 0.4, 1.2, 3.0, 0.9,
                false, false, false, false, 60, false);
        return new StockAnalysisInput("ITC", "ITC Limited", "NSE", null, m, null, news, null, "MANUAL");
    }

    @Test
    void capsNewsItemsAndTruncatesLongEntries() {
        List<String> news = IntStream.range(0, 12)
                .mapToObj(i -> "Headline " + i + " " + "x".repeat(500))
                .toList();
        String prompt = OpenAiAnalysisProvider.renderUserPrompt(input(news), 6);
        long bullets = prompt.lines().filter(l -> l.startsWith("- ")).count();
        assertThat(bullets).isEqualTo(6);
        assertThat(prompt).doesNotContain("x".repeat(OpenAiAnalysisProvider.NEWS_CHARS + 1));
        assertThat(prompt.length()).isLessThan(4000);
    }

    @Test
    void truncateKeepsShortStrings() {
        assertThat(OpenAiAnalysisProvider.truncate("  hello  ", 200)).isEqualTo("hello");
        assertThat(OpenAiAnalysisProvider.truncate("a".repeat(300), 200)).hasSize(201);
    }

    @Test
    void rateLimitDetectionCovers429Only() {
        HttpClientErrorException tooMany = HttpClientErrorException.create(
                HttpStatus.TOO_MANY_REQUESTS, "Too Many Requests",
                HttpHeaders.EMPTY, new byte[0], StandardCharsets.UTF_8);
        HttpClientErrorException badRequest = HttpClientErrorException.create(
                HttpStatus.BAD_REQUEST, "Bad Request",
                HttpHeaders.EMPTY, new byte[0], StandardCharsets.UTF_8);
        assertThat(OpenAiAnalysisProvider.isRateLimited(tooMany)).isTrue();
        assertThat(OpenAiAnalysisProvider.isRateLimited(badRequest)).isFalse();
        assertThat(OpenAiAnalysisProvider.isRateLimited(null)).isFalse();
        // getResponseHeaders() must exist on this type (regression: getHeaders() does not).
        assertThat(tooMany.getResponseHeaders()).isNotNull();
    }

    @Test
    void jsonModeDisabledForGptOssModels() {
        assertThat(OpenAiAnalysisProvider.shouldUseJsonMode("openai/gpt-oss-120b", true)).isFalse();
        assertThat(OpenAiAnalysisProvider.shouldUseJsonMode("openai/gpt-oss-20b", true)).isFalse();
        assertThat(OpenAiAnalysisProvider.shouldUseJsonMode("llama-3.1-8b-instant", true)).isTrue();
        assertThat(OpenAiAnalysisProvider.shouldUseJsonMode("gpt-4o-mini", true)).isTrue();
        assertThat(OpenAiAnalysisProvider.shouldUseJsonMode("gpt-4o-mini", false)).isFalse();
        assertThat(OpenAiAnalysisProvider.shouldUseJsonMode(null, true)).isTrue();
    }

    @Test
    void extractJsonHandlesFencesAndChatter() {
        assertThat(OpenAiAnalysisProvider.extractJson("```json\n{\"a\":1}\n```"))
                .isEqualTo("{\"a\":1}");
        assertThat(OpenAiAnalysisProvider.extractJson("Sure! Here you go: {\"a\":1} hope that helps"))
                .isEqualTo("{\"a\":1}");
        assertThat(OpenAiAnalysisProvider.extractJson("{\"a\":1}")).isEqualTo("{\"a\":1}");
        assertThat(OpenAiAnalysisProvider.extractJson("no braces here")).isNull();
        assertThat(OpenAiAnalysisProvider.extractJson(null)).isNull();
    }

    @Test
    void reducedContextStaysSmall() {
        List<String> news = IntStream.range(0, 12)
                .mapToObj(i -> "Headline " + i + " " + "x".repeat(500))
                .toList();
        String prompt = OpenAiAnalysisProvider.renderUserPrompt(input(news), 3);
        long bullets = prompt.lines().filter(l -> l.startsWith("- ")).count();
        assertThat(bullets).isEqualTo(3);
        assertThat(prompt.length()).isLessThan(2500);
    }

    @Test
    void reasoningEffortCappedForGptOssOnly() {
        assertThat(OpenAiAnalysisProvider.reasoningEffort("openai/gpt-oss-120b")).isEqualTo("low");
        assertThat(OpenAiAnalysisProvider.reasoningEffort("openai/gpt-oss-20b")).isEqualTo("low");
        assertThat(OpenAiAnalysisProvider.reasoningEffort("llama-3.1-8b-instant")).isNull();
        assertThat(OpenAiAnalysisProvider.reasoningEffort("gpt-4o-mini")).isNull();
        assertThat(OpenAiAnalysisProvider.reasoningEffort(null)).isNull();
    }
}
