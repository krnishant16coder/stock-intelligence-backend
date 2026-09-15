package com.stockintelligence.common;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Central typed configuration bound from {@code app.*} properties.
 * All secrets must be supplied via environment variables (see .env.example).
 */
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    /** Active market-data provider key: {@code alphavantage} (default) or {@code twelvedata}. */
    private String marketDataProvider = "alphavantage";
    /** Active news provider key: {@code newsapi} (default) or {@code gnews}. */
    private String newsProvider = "newsapi";
    /** Active AI provider key: {@code openai} (default, OpenAI-compatible endpoint). */
    private String aiProvider = "openai";

    private final AlphaVantage alphaVantage = new AlphaVantage();
    private final TwelveData twelveData = new TwelveData();
    private final NewsApi newsApi = new NewsApi();
    private final GNews gnews = new GNews();
    private final Ai ai = new Ai();
    private final Analysis analysis = new Analysis();
    private final Scheduling scheduling = new Scheduling();
    private final Notifications notifications = new Notifications();

    public String getMarketDataProvider() { return marketDataProvider; }
    public void setMarketDataProvider(String marketDataProvider) { this.marketDataProvider = marketDataProvider; }
    public String getNewsProvider() { return newsProvider; }
    public void setNewsProvider(String newsProvider) { this.newsProvider = newsProvider; }
    public String getAiProvider() { return aiProvider; }
    public void setAiProvider(String aiProvider) { this.aiProvider = aiProvider; }
    public AlphaVantage getAlphaVantage() { return alphaVantage; }
    public TwelveData getTwelveData() { return twelveData; }
    public NewsApi getNewsApi() { return newsApi; }
    public GNews getGnews() { return gnews; }
    public Ai getAi() { return ai; }
    public Analysis getAnalysis() { return analysis; }
    public Scheduling getScheduling() { return scheduling; }
    public Notifications getNotifications() { return notifications; }

    public static class AlphaVantage {
        private String apiKey = "";
        private String baseUrl = "https://www.alphavantage.co";
        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    }

    public static class TwelveData {
        private String apiKey = "";
        private String baseUrl = "https://api.twelvedata.com";
        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    }

    public static class NewsApi {
        private String apiKey = "";
        private String baseUrl = "https://newsapi.org";
        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    }

    public static class GNews {
        private String apiKey = "";
        private String baseUrl = "https://gnews.io";
        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    }

    public static class Ai {
        private String apiKey = "";
        private String baseUrl = "https://api.openai.com/v1";
        private String model = "gpt-4o-mini";
        private int timeoutSeconds = 120;
        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }
        public int getTimeoutSeconds() { return timeoutSeconds; }
        public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
    }

    /** Deterministic rule thresholds for metrics + alert detection. */
    public static class Analysis {
        private double dailyMovePct = 5.0;
        private double weeklyDeclinePct = 10.0;
        private double monthlyDeclinePct = 15.0;
        private double volumeSurgeMultiple = 3.0;
        private int volumeAverageDays = 20;
        private int historyDays = 90;
        private int newsDays = 14;
        private int newsMaxArticles = 20;

        public double getDailyMovePct() { return dailyMovePct; }
        public void setDailyMovePct(double v) { this.dailyMovePct = v; }
        public double getWeeklyDeclinePct() { return weeklyDeclinePct; }
        public void setWeeklyDeclinePct(double v) { this.weeklyDeclinePct = v; }
        public double getMonthlyDeclinePct() { return monthlyDeclinePct; }
        public void setMonthlyDeclinePct(double v) { this.monthlyDeclinePct = v; }
        public double getVolumeSurgeMultiple() { return volumeSurgeMultiple; }
        public void setVolumeSurgeMultiple(double v) { this.volumeSurgeMultiple = v; }
        public int getVolumeAverageDays() { return volumeAverageDays; }
        public void setVolumeAverageDays(int v) { this.volumeAverageDays = v; }
        public int getHistoryDays() { return historyDays; }
        public void setHistoryDays(int v) { this.historyDays = v; }
        public int getNewsDays() { return newsDays; }
        public void setNewsDays(int v) { this.newsDays = v; }
        public int getNewsMaxArticles() { return newsMaxArticles; }
        public void setNewsMaxArticles(int v) { this.newsMaxArticles = v; }
    }

    public static class Scheduling {
        /** Cron for checking due report schedules (default: every hour). */
        private String reportCheckCron = "0 0 * * * *";
        /** Cron for independent critical-alert monitoring. */
        private String alertMonitorCron = "0 0 */4 * * *";
        private boolean enabled = true;

        public String getReportCheckCron() { return reportCheckCron; }
        public void setReportCheckCron(String v) { this.reportCheckCron = v; }
        public String getAlertMonitorCron() { return alertMonitorCron; }
        public void setAlertMonitorCron(String v) { this.alertMonitorCron = v; }
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean v) { this.enabled = v; }
    }

    public static class Notifications {
        private boolean enabled = true;
        private String defaultRecipient = "";
        private String from = "stock-intelligence@localhost";
        private List<String> reportSeverities = new ArrayList<>(List.of("HIGH", "CRITICAL"));

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean v) { this.enabled = v; }
        public String getDefaultRecipient() { return defaultRecipient; }
        public void setDefaultRecipient(String v) { this.defaultRecipient = v; }
        public String getFrom() { return from; }
        public void setFrom(String v) { this.from = v; }
        public List<String> getReportSeverities() { return reportSeverities; }
        public void setReportSeverities(List<String> v) { this.reportSeverities = v; }
    }
}
