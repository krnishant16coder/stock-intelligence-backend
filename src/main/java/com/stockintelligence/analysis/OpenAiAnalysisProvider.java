package com.stockintelligence.analysis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.stockintelligence.common.AppProperties;
import com.stockintelligence.common.ExternalProviderException;
import com.stockintelligence.common.TransientProviderException;
import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * OpenAI-compatible chat-completions provider (works with OpenAI, OpenRouter,
 * Azure OpenAI proxy, local gateways, etc. via base-url/model settings).
 * Requests structured JSON output and validates it before returning.
 */
@Component
public class OpenAiAnalysisProvider implements AIAnalysisProvider {

    private static final Logger log = LoggerFactory.getLogger(OpenAiAnalysisProvider.class);

    static final String SYSTEM_PROMPT = """
            You are a stock research assistant. Analyze the given Indian equity using ONLY the provided
            market metrics, fundamentals and news summaries. These signals are analytical observations,
            NOT financial advice and NOT guaranteed predictions.
            Rules:
            - Explain reasoning in 'summary' and list concrete 'keyReasons'.
            - Explicitly acknowledge missing or uncertain data and lower 'confidence' accordingly.
            - Set 'criticalAlert' true only for genuinely material risk (fraud, regulatory action,
              severe fundamental deterioration, extreme price collapse with negative news).
            - 'signal' must be one of: BUY_MORE, HOLD, REVIEW, HIGH_RISK, INSUFFICIENT_DATA.
            - 'riskLevel' must be one of: LOW, MEDIUM, HIGH, CRITICAL.
            - Respond with a single JSON object and no other text, with exactly these fields:
              signal, riskLevel, priceTrend, fundamentalTrend, newsImpact, summary,
              keyReasons (array of strings), confidence (0-1 number), criticalAlert (boolean).
            """;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final AppProperties properties;

    public OpenAiAnalysisProvider(RestClient.Builder builder, ObjectMapper objectMapper,
                                  AppProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(15));
        factory.setReadTimeout(Duration.ofSeconds(Math.max(30, properties.getAi().getTimeoutSeconds())));
        this.restClient = builder.baseUrl(properties.getAi().getBaseUrl()).requestFactory(factory).build();
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Override
    public String providerName() {
        return "openai-compatible";
    }

    @Override
    @Retryable(retryFor = TransientProviderException.class, maxAttempts = 2,
            backoff = @Backoff(delay = 3000, multiplier = 2))
    public StockAnalysisResult analyze(StockAnalysisInput input) {
        if (properties.getAi().getApiKey() == null || properties.getAi().getApiKey().isBlank()) {
            throw new ExternalProviderException("AI API key is not configured (app.ai.api-key)");
        }
        try {
            ObjectNode payload = objectMapper.createObjectNode();
            payload.put("model", properties.getAi().getModel());
            payload.put("temperature", 0.2);
            ObjectNode format = payload.putObject("response_format");
            format.put("type", "json_object");
            ArrayNode messages = payload.putArray("messages");
            messages.addObject().put("role", "system").put("content", SYSTEM_PROMPT);
            messages.addObject().put("role", "user").put("content", renderUserPrompt(input));

            String body = restClient.post().uri("/chat/completions")
                    .header("Authorization", "Bearer " + properties.getAi().getApiKey())
                    .body(payload.toString()).retrieve().body(String.class);
            JsonNode root = objectMapper.readTree(body);
            if (root.has("error")) {
                throw new ExternalProviderException("AI provider: " + root.path("error").path("message").asText());
            }
            String content = root.path("choices").path(0).path("message").path("content").asText(null);
            if (content == null || content.isBlank()) {
                throw new ExternalProviderException("AI provider returned empty content");
            }
            StockAnalysisResult parsed = objectMapper.readValue(content, StockAnalysisResult.class);
            StockAnalysisResult result = parsed.normalized();
            log.info("AI analysis for {}: signal={} risk={} confidence={}",
                    input.symbol(), result.signal(), result.riskLevel(), result.confidence());
            return result;
        } catch (ExternalProviderException | IllegalArgumentException e) {
            throw new ExternalProviderException("AI analysis failed: " + e.getMessage(), e);
        } catch (ResourceAccessException e) {
            throw new TransientProviderException("AI request failed: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new ExternalProviderException("AI analysis failed: " + e.getMessage(), e);
        }
    }

    private static String renderUserPrompt(StockAnalysisInput in) {
        String news = (in.newsSummaries() == null || in.newsSummaries().isEmpty())
                ? "No recent company news available."
                : in.newsSummaries().stream().limit(12).map(s -> "- " + s).collect(Collectors.joining("\n"));
        RuleMetrics m = in.ruleMetrics();
        return """
                Stock: %s (%s, %s) | Period: %s
                Latest price: %s | Daily: %s%% | Weekly: %s%% | Monthly: %s%% | Volume vs avg: %s
                Flags: sharpDailyMove=%s weeklyDecline=%s monthlyDecline=%s unusualVolume=%s dataPoints=%d insufficientData=%s
                Fundamentals: %s
                Previous signal: %s
                Recent news:
                %s
                """.formatted(in.symbol(), in.companyName(), in.exchange(), in.periodLabel(),
                fmt(m == null ? null : m.latestPrice()), pct(m == null ? null : m.dailyChangePct()),
                pct(m == null ? null : m.weeklyChangePct()), pct(m == null ? null : m.monthlyChangePct()),
                m == null || m.volumeRatio() == null ? "n/a" : String.format("%.2fx", m.volumeRatio()),
                m != null && m.sharpDailyMove(), m != null && m.weeklyDecline(),
                m != null && m.monthlyDecline(), m != null && m.unusualVolume(),
                m == null ? 0 : m.dataPoints(), m == null || m.insufficientData(),
                in.fundamentals() == null ? "unavailable" : in.fundamentals().toString(),
                in.previousSignal() == null ? "none" : in.previousSignal(), news);
    }

    private static String fmt(Object v) {
        return v == null ? "n/a" : v.toString();
    }

    private static String pct(Double v) {
        return v == null ? "n/a" : String.format("%.2f", v);
    }

    /** Exposed for tests. */
    List<String> allowedSignals() {
        return List.of("BUY_MORE", "HOLD", "REVIEW", "HIGH_RISK", "INSUFFICIENT_DATA");
    }
}
