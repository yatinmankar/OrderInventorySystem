package com.example.common.aop;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers {@link OrderTraceAspect}. Not component-scanned (services only scan their own
 * base package), so each *ServiceApplication imports this explicitly, same as KafkaSupportConfig.
 */
@Configuration
public class TraceAspectConfig {

    @Bean
    public OrderTraceAspect orderTraceAspect() {
        return new OrderTraceAspect();
    }
}
