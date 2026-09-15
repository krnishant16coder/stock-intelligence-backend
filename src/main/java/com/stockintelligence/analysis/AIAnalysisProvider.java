package com.stockintelligence.analysis;

/**
 * Replaceable AI analysis provider contract. Implementations must return
 * validated, structured results and never fabricate market data.
 */
public interface AIAnalysisProvider {

    StockAnalysisResult analyze(StockAnalysisInput input);

    String providerName();
}
