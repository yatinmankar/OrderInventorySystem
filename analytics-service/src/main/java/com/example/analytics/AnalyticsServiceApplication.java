package com.example.analytics;

import com.example.common.aop.TraceAspectConfig;
import com.example.common.config.KafkaSupportConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

import java.util.TimeZone;

@SpringBootApplication
@Import({KafkaSupportConfig.class, TraceAspectConfig.class})
public class AnalyticsServiceApplication {
    public static void main(String[] args) {
        // JVM on this host resolves the default zone to the legacy "Asia/Calcutta" alias,
        // which Postgres doesn't recognize as a valid TimeZone startup parameter.
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        SpringApplication.run(AnalyticsServiceApplication.class, args);
    }
}
