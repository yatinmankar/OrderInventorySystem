package com.example.common.aop;

import java.lang.reflect.Method;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/**
 * Around advice for {@link TraceOrder}: resolves the order id (from an argument that exposes
 * an orderId()/getOrderId() accessor, e.g. any saga command/event record, or otherwise from the
 * return value once the id is known), pushes it into MDC under "orderId" for the call, and logs
 * entry/exit/failure. Nested @TraceOrder calls on the same order id restore the outer value
 * instead of clearing it.
 */
@Aspect
public class OrderTraceAspect {

    private static final Logger log = LoggerFactory.getLogger(OrderTraceAspect.class);
    private static final String MDC_KEY = "orderId";

    @Around("@annotation(com.example.common.aop.TraceOrder)")
    public Object trace(ProceedingJoinPoint pjp) throws Throwable {
        String signature = pjp.getSignature().toShortString();
        String orderId = extractOrderId(pjp.getArgs());
        String previous = MDC.get(MDC_KEY);

        if (orderId != null) {
            MDC.put(MDC_KEY, orderId);
            log.info(">> {}", signature);
        }
        long start = System.nanoTime();
        try {
            Object result = pjp.proceed();
            if (orderId == null) {
                String fromResult = extractFromResult(result);
                if (fromResult != null) {
                    MDC.put(MDC_KEY, fromResult);
                }
            }
            log.info("<< {} ({} ms)", signature, elapsedMs(start));
            return result;
        } catch (Throwable ex) {
            log.warn("xx {} failed after {} ms: {}", signature, elapsedMs(start), ex.toString());
            throw ex;
        } finally {
            if (previous != null) {
                MDC.put(MDC_KEY, previous);
            } else {
                MDC.remove(MDC_KEY);
            }
        }
    }

    private long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }

    private String extractOrderId(Object[] args) {
        for (Object arg : args) {
            String id = extractOrderId(arg);
            if (id != null) return id;
        }
        return null;
    }

    // Only used on the return value: a method returning a bare order id (e.g. order creation)
    // is a deliberate, narrow case, unlike arguments where a stray String could be anything.
    private String extractFromResult(Object result) {
        if (result instanceof String s) return s;
        return extractOrderId(result);
    }

    private String extractOrderId(Object obj) {
        if (obj == null) return null;
        Method accessor = findAccessor(obj.getClass(), "orderId");
        if (accessor == null) accessor = findAccessor(obj.getClass(), "getOrderId");
        if (accessor == null) return null;
        try {
            Object value = accessor.invoke(obj);
            return value != null ? value.toString() : null;
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    private Method findAccessor(Class<?> type, String name) {
        try {
            return type.getMethod(name);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }
}
