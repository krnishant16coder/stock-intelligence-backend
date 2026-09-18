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
import org.springframework.web.client.RestClientResponseException;

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
            - 'priceTrend': derive from the metrics (e.g. UPTREND, DOWNTREND, STABLE, VOLATILE,
              VOLATILE/DECLINING). NEVER return UNKNOWN when dataPoints > 0.
            - 'fundamentalTrend': Alpha Vantage returns no OVERVIEW for Indian (NSE/BSE) stocks,
              so Fundamentals will often say 'unavailable'. In that case infer from price stability
              and news (STABLE, WEAKENING, IMPROVING) and state the inference in 'summary'.
              Only use UNKNOWN when there is no price history AND no news at all.
            - 'newsImpact': when news summaries are provided you MUST return one of
              POSITIVE, NEGATIVE, MIXED or NEUTRAL based on their tone. NEVER return UNKNOWN
              when news was provided. Only use NO_NEWS/UNKNOWN when the news list is empty.
            - Only use signal INSUFFICIENT_DATA when dataPoints == 0 AND no news coverage exists.
              If price history exists, choose BUY_MORE, HOLD, REVIEW or HIGH_RISK.
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
    @Retryable(retryFor = TransientProviderException.class, maxAttempts = 4,
            backoff = @Backoff(delay = 8000, multiplier = 2))
    public StockAnalysisResult analyze(StockAnalysisInput input) {
        if (properties.getAi().getApiKey() == null || properties.getAi().getApiKey().isBlank()) {
            throw new ExternalProviderException("AI API key is not configured (app.ai.api-key)");
        }
        try {
            ObjectNode payload = objectMapper.createObjectNode();
            payload.put("model", properties.getAi().getModel());
            payload.put("temperature", 0.2);
            payload.put("max_tokens", Math.max(200, properties.getAi().getMaxTokens()));
            ObjectNode format = payload.putObject("response_format");
            format.put("type", "json_object");
            ArrayNode messages = payload.putArray("messages");
            messages.addObject().put("role", "system").put("content", SYSTEM_PROMPT);
            messages.addObject().put("role", "user").put("content", renderUserPrompt(input,
                    Math.max(1, properties.getAi().getMaxNewsForAi())));

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
        } catch (RestClientResponseException e) {
            // 429 TPM bursts are transient: back off and retry (Groq sends
            // "try again in Ns" + Retry-After; @Retryable handles the wait).
            if (isRateLimited(e)) {
                String retryAfter = null;
                try {
                    retryAfter = e.getResponseHeaders() != null
                            ? e.getResponseHeaders().getFirst("Retry-After") : null;
                } catch (Exception ignored) {}
                log.warn("AI rate-limited (429{}), will retry: {}",
                        retryAfter != null ? ", retry-after=" + retryAfter + "s" : "",
                        firstChars(e.getResponseBodyAsString(), 300));
                throw new TransientProviderException(
                        "AI rate-limited (429), retrying: " + firstChars(e.getResponseBodyAsString(), 300), e);
            }
            throw new ExternalProviderException("AI analysis failed: " + e.getStatusCode().value()
                    + " " + firstChars(e.getResponseBodyAsString(), 300), e);
        } catch (Exception e) {
            throw new ExternalProviderException("AI analysis failed: " + e.getMessage(), e);
        }
    }

    private static String renderUserPrompt(StockAnalysisInput in) {
        return renderUserPrompt(in, 6);
    }

    /** Token-guarded prompt: caps news items and truncates each entry. */
    static String renderUserPrompt(StockAnalysisInput in, int maxNews) {
        String news = (in.newsSummaries() == null || in.newsSummaries().isEmpty())
                ? "No recent company news available."
                : in.newsSummaries().stream().limit(Math.max(1, maxNews))
                        .map(s -> "- " + truncate(s, 200)).collect(Collectors.joining("\n"));
        RuleMetrics m = in.ruleMetrics();
        String fundamentals = in.fundamentals() == null
                ? "unavailable (NSE/BSE: infer from price+news)"
                : truncate(in.fundamentals().toString(), 300);
        return """
                Stock: %s (%s, %s) | Period: %s
                Latest price: %s | Daily: %s%% | Weekly: %s%% | Monthly: %s%% | Volume vs avg: %s
                Flags: sharpDailyMove=%s weeklyDecline=%s monthlyDecline=%s unusualVolume=%s dataPoints=%d insufficientData=%s
                Fundamentals: %s
                Previous signal: %s
                Recent news:
                %s
                Reminder: dataPoints > 0 means priceTrend must NOT be UNKNOWN. News listed means newsImpact must NOT be UNKNOWN. Fundamentals unavailable means infer, not UNKNOWN.
                """.formatted(in.symbol(), in.companyName(), in.exchange(), in.periodLabel(),
                fmt(m == null ? null : m.latestPrice()), pct(m == null ? null : m.dailyChangePct()),
                pct(m == null ? null : m.weeklyChangePct()), pct(m == null ? null : m.monthlyChangePct()),
                m == null || m.volumeRatio() == null ? "n/a" : String.format("%.2fx", m.volumeRatio()),
                m != null && m.sharpDailyMove(), m != null && m.weeklyDecline(),
                m != null && m.monthlyDecline(), m != null && m.unusualVolume(),
                m == null ? 0 : m.dataPoints(), m == null || m.insufficientData(),
                fundamentals,
                in.previousSignal() == null ? "none" : in.previousSignal(), news);
    }

    private static String fmt(Object v) {
        return v == null ? "n/a" : v.toString();
    }

    private static String pct(Double v) {
        return v == null ? "n/a" : String.format("%.2f", v);
    }

    /** 429s are transient TPM bursts; everything else 4xx/5xx fails fast. */
    static boolean isRateLimited(RestClientResponseException e) {
        return e != null && e.getStatusCode().value() == 429;
    }

    static String truncate(String s, int max) {        if (s == null) {
            return "";
        }
        String t = s.strip();
        return t.length() <= max ? t : t.substring(0, max) + "…";
    }

    private static String firstChars(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }

    /** Exposed for tests. */
    List<String> allowedSignals() {
        return List.of("BUY_MORE", "HOLD", "REVIEW", "HIGH_RISK", "INSUFFICIENT_DATA");
    }
}
