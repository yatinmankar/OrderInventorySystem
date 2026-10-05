package com.example.common.aop;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a top-flow saga entry point (REST handler, orchestrator listener, downstream command
 * listener) for correlation logging. {@link OrderTraceAspect} pushes the order id into MDC
 * for the duration of the call, so every log line across all four services can be grepped by
 * a single order id.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface TraceOrder {
}
