package com.stockintelligence.alert;

import com.stockintelligence.analysis.AIAnalysisProvider;
import com.stockintelligence.analysis.RuleMetrics;
import com.stockintelligence.analysis.StockAnalysisInput;
import com.stockintelligence.analysis.StockAnalysisResult;
import com.stockintelligence.common.AppProperties;
import com.stockintelligence.news.NewsArticle;
import com.stockintelligence.stock.Stock;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Deterministic rule detection + best-effort AI impact assessment + dedup.
 * Only genuinely significant events become alerts.
 */
@Service
public class RiskAlertService {

    private static final Logger log = LoggerFactory.getLogger(RiskAlertService.class);
    private static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");

    private final AlertRepository repository;
    private final AIAnalysisProvider aiProvider;
    private final AppProperties properties;

    public RiskAlertService(AlertRepository repository, AIAnalysisProvider aiProvider, AppProperties properties) {
        this.repository = repository;
        this.aiProvider = aiProvider;
        this.properties = properties;
    }

    public record AlertCandidate(AlertType type, Severity severity, String message, String dedupKey) {}

    /**
     * Evaluate rules for one stock and persist new (deduplicated) alerts.
     *
     * @param aiContext optional AI result from the scheduled analysis; when present it is
     *                  used to confirm news-driven candidates instead of an extra LLM call
     */
    @Transactional
    public List<Alert> evaluateAndCreate(Stock stock, RuleMetrics metrics, List<NewsArticle> news,
                                         StockAnalysisResult aiContext, StockAnalysisInput aiInput) {
        List<AlertCandidate> candidates = detect(stock, metrics, news);
        List<Alert> created = new ArrayList<>();
        for (AlertCandidate c : candidates) {
            if (c.dedupKey() != null && repository.existsByDedupKey(c.dedupKey())) {
                continue;
            }
            Severity severity = c.severity();
            if (isNewsDriven(c.type())) {
                severity = assessNewsImpact(c, aiContext, aiInput);
                if (severity == null) {
                    continue; // AI judged it immaterial
                }
            }
            Alert alert = new Alert();
            alert.setStock(stock);
            alert.setAlertType(c.type());
            alert.setSeverity(severity);
            alert.setMessage(c.message());
            alert.setDedupKey(c.dedupKey());
            created.add(repository.save(alert));
            log.info("Alert created: {} {} {}", stock.getSymbol(), c.type(), severity);
        }
        return created;
    }

    List<AlertCandidate> detect(Stock stock, RuleMetrics m, List<NewsArticle> news) {
        List<AlertCandidate> out = new ArrayList<>();
        String bucket = LocalDate.now(ZONE).toString();
        if (m != null && !m.insufficientData()) {
            if (m.dailyChangePct() != null && Math.abs(m.dailyChangePct()) >= properties.getAnalysis().getDailyMovePct()) {
                boolean up = m.dailyChangePct() > 0;
                double extreme = properties.getAnalysis().getDailyMovePct() * 2;
                Severity sev = Math.abs(m.dailyChangePct()) >= extreme ? Severity.HIGH : Severity.MEDIUM;
                out.add(new AlertCandidate(up ? AlertType.PRICE_SPIKE : AlertType.PRICE_DROP, sev,
                        "%s moved %s%.2f%% today to %s.".formatted(stock.getSymbol(),
                                up ? "+" : "", m.dailyChangePct(), m.latestPrice()),
                        dedup(stock, up ? "SPIKE" : "DROP", bucket)));
            }
            if (m.weeklyDecline()) {
                out.add(new AlertCandidate(AlertType.WEEKLY_DECLINE, Severity.HIGH,
                        "%s declined %.2f%% over the last week.".formatted(stock.getSymbol(), m.weeklyChangePct()),
                        dedup(stock, "WDECLINE", bucket)));
            }
            if (m.monthlyDecline()) {
                out.add(new AlertCandidate(AlertType.MONTHLY_DECLINE, Severity.HIGH,
                        "%s declined %.2f%% over the last month.".formatted(stock.getSymbol(), m.monthlyChangePct()),
                        dedup(stock, "MDECLINE", bucket)));
            }
            if (m.unusualVolume()) {
                out.add(new AlertCandidate(AlertType.VOLUME_SURGE, Severity.LOW,
                        "%s traded at %.1fx its average volume.".formatted(stock.getSymbol(), m.volumeRatio()),
                        dedup(stock, "VOL", bucket)));
            }
        }
        if (news != null) {
            for (NewsArticle article : news) {
                matchNewsCategory(article).ifPresent(match -> out.add(new AlertCandidate(match.type(),
                        match.defaultSeverity(),
                        "%s: %s (%s)".formatted(match.type(), article.getTitle(), article.getSource()),
                        dedup(stock, "NEWS-" + match.type() + "-" + urlHash(article.getUrl()), bucket))));
            }
        }
        return out;
    }

    private record NewsMatch(AlertType type, Severity defaultSeverity) {}

    private static final List<NewsRule> NEWS_RULES = List.of(
            new NewsRule(AlertType.FRAUD_GOVERNANCE, Severity.CRITICAL,
                    "fraud|scam|insider trad|market manipulat|forensic audit|auditor resign|governance|siphon|embezzle"),
            new NewsRule(AlertType.REGULATORY_RISK, Severity.HIGH,
                    "sebi|rbi|regulator|fine|penalt|ban|probe|investigat|notice|show-cause|lawsuit|court|nclt|settlement order|raid"),
            new NewsRule(AlertType.NEGATIVE_EARNINGS, Severity.MEDIUM,
                    "profit warn|net loss|loss widens|miss.*estimat|downgrade|earnings miss|revenue fall|margin compress"),
            new NewsRule(AlertType.DEBT_RISK, Severity.HIGH,
                    "default|bankrupt|insolv|npa|debt restruct|credit watch|rating cut|liquidity cris|loan recall"),
            new NewsRule(AlertType.MANAGEMENT_CHANGE, Severity.MEDIUM,
                    "ceo resign|cfo resign|managing director.*resign|chairman.*resign|board.*resign|ceo.*quit|leadership change"),
            new NewsRule(AlertType.FUNDAMENTAL_DETERIORATION, Severity.MEDIUM,
                    "plant clos|layoff|job cut|strike|shutdown|contract cancel|order cancel|dividend cut|dividend skip"));

    private record NewsRule(AlertType type, Severity defaultSeverity, Pattern pattern) {
        NewsRule(AlertType type, Severity defaultSeverity, String regex) {
            this(type, defaultSeverity, Pattern.compile(regex, Pattern.CASE_INSENSITIVE));
        }
    }

    private java.util.Optional<NewsMatch> matchNewsCategory(NewsArticle article) {
        String text = ((article.getTitle() == null ? "" : article.getTitle()) + " "
                + (article.getSummary() == null ? "" : article.getSummary()));
        for (NewsRule rule : NEWS_RULES) {
            if (rule.pattern().matcher(text).find()) {
                return java.util.Optional.of(new NewsMatch(rule.type(), rule.defaultSeverity()));
            }
        }
        return java.util.Optional.empty();
    }

    private boolean isNewsDriven(AlertType type) {
        return switch (type) {
            case NEGATIVE_EARNINGS, REGULATORY_RISK, FRAUD_GOVERNANCE, DEBT_RISK, MANAGEMENT_CHANGE,
                    FUNDAMENTAL_DETERIORATION -> true;
            default -> false;
        };
    }

    /**
     * Confirm a news-driven candidate with AI when possible. Falls back to the
     * keyword default severity when the LLM is unavailable.
     */
    Severity assessNewsImpact(AlertCandidate candidate, StockAnalysisResult aiContext, StockAnalysisInput aiInput) {
        if (aiContext != null) {
            if (Boolean.TRUE.equals(aiContext.criticalAlert())) {
                return Severity.CRITICAL;
            }
            return switch (aiContext.riskLevel()) {
                case "CRITICAL" -> Severity.CRITICAL;
                case "HIGH" -> Severity.HIGH;
                case "MEDIUM" -> candidate.severity();
                default -> candidate.severity();
            };
        }
        if (aiInput != null) {
            try {
                StockAnalysisResult r = aiProvider.analyze(aiInput).normalized();
                if (Boolean.TRUE.equals(r.criticalAlert())) {
                    return Severity.CRITICAL;
                }
                if ("LOW".equals(r.riskLevel()) && candidate.severity().ordinal() <= Severity.MEDIUM.ordinal()) {
                    log.info("AI judged news candidate immaterial, dropping: {}", candidate.message());
                    return null;
                }
                return candidate.severity();
            } catch (Exception e) {
                log.warn("AI impact assessment failed, using keyword default: {}", e.getMessage());
            }
        }
        return candidate.severity();
    }

    private static String dedup(Stock stock, String kind, String bucket) {
        return stock.getId() + "|" + kind + "|" + bucket;
    }

    private static String urlHash(String url) {
        if (url == null) {
            return "nourl";
        }
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(url.getBytes(StandardCharsets.UTF_8))).substring(0, 16);
        } catch (Exception e) {
            return String.valueOf(url.hashCode());
        }
    }
}
