package com.stockintelligence.report;

import com.stockintelligence.stock.Stock;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "stock_analysis")
public class StockAnalysis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "report_id", nullable = false)
    private AnalysisReport report;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "stock_id", nullable = false)
    private Stock stock;

    @Column(length = 32)
    private String signal;

    @Column(name = "risk_level", length = 16)
    private String riskLevel;

    @Column(name = "price_trend", length = 64)
    private String priceTrend;

    @Column(name = "fundamental_trend", length = 64)
    private String fundamentalTrend;

    @Column(name = "news_impact", length = 64)
    private String newsImpact;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @Column(name = "key_reasons", columnDefinition = "TEXT")
    private String keyReasons;

    private Double confidence;

    @Column(name = "critical_alert")
    private boolean criticalAlert;

    @Column(name = "rule_metrics", columnDefinition = "TEXT")
    private String ruleMetricsJson;

    protected StockAnalysis() {}

    public Long getId() { return id; }
    public AnalysisReport getReport() { return report; }
    public void setReport(AnalysisReport report) { this.report = report; }
    public Stock getStock() { return stock; }
    public void setStock(Stock stock) { this.stock = stock; }
    public String getSignal() { return signal; }
    public void setSignal(String signal) { this.signal = signal; }
    public String getRiskLevel() { return riskLevel; }
    public void setRiskLevel(String riskLevel) { this.riskLevel = riskLevel; }
    public String getPriceTrend() { return priceTrend; }
    public void setPriceTrend(String priceTrend) { this.priceTrend = priceTrend; }
    public String getFundamentalTrend() { return fundamentalTrend; }
    public void setFundamentalTrend(String fundamentalTrend) { this.fundamentalTrend = fundamentalTrend; }
    public String getNewsImpact() { return newsImpact; }
    public void setNewsImpact(String newsImpact) { this.newsImpact = newsImpact; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public String getKeyReasons() { return keyReasons; }
    public void setKeyReasons(String keyReasons) { this.keyReasons = keyReasons; }
    public Double getConfidence() { return confidence; }
    public void setConfidence(Double confidence) { this.confidence = confidence; }
    public boolean isCriticalAlert() { return criticalAlert; }
    public void setCriticalAlert(boolean criticalAlert) { this.criticalAlert = criticalAlert; }
    public String getRuleMetricsJson() { return ruleMetricsJson; }
    public void setRuleMetricsJson(String ruleMetricsJson) { this.ruleMetricsJson = ruleMetricsJson; }
}
