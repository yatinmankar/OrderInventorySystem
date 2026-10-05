package com.example.order.dlq;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Container factory for the DLT recorder. Deliberately NOT the shared one:
 *  - no JSON message converter: the recorder needs the raw bytes of a possibly-malformed record;
 *  - no dead-lettering of its own (it would DLT the DLT): if the DB write fails it retries
 *    forever, so a DB outage delays recording instead of losing it.
 */
@Configuration
public class DltKafkaConfig {

    @Bean
    public ConcurrentKafkaListenerContainerFactory<Object, Object> dltListenerFactory(
            ConsumerFactory<Object, Object> consumerFactory) {
        ConcurrentKafkaListenerContainerFactory<Object, Object> f = new ConcurrentKafkaListenerContainerFactory<>();
        f.setConsumerFactory(consumerFactory);
        f.setCommonErrorHandler(new DefaultErrorHandler(new FixedBackOff(5000L, FixedBackOff.UNLIMITED_ATTEMPTS)));
        return f;
    }
}
