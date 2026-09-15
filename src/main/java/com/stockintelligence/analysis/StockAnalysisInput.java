package com.stockintelligence.analysis;

import com.stockintelligence.marketdata.FundamentalData;
import com.stockintelligence.marketdata.MarketQuote;
import java.util.List;

/** Normalized input assembled for the LLM. */
public record StockAnalysisInput(String symbol, String companyName, String exchange,
                                 MarketQuote latestQuote, RuleMetrics ruleMetrics,
                                 FundamentalData fundamentals, List<String> newsSummaries,
                                 String previousSignal, String periodLabel) {}
