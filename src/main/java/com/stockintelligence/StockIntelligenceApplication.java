package com.stockintelligence;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import com.stockintelligence.common.AppProperties;

@SpringBootApplication
@EnableScheduling
@EnableRetry
@EnableAsync
@EnableConfigurationProperties(AppProperties.class)
public class StockIntelligenceApplication {

    public static void main(String[] args) {
        SpringApplication.run(StockIntelligenceApplication.class, args);
    }
}
