package com.example.common.config;

import com.example.common.Topics;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.converter.ByteArrayJsonMessageConverter;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.util.backoff.ExponentialBackOff;

import java.util.HashMap;
import java.util.Map;

/**
 * Shared messaging wiring imported by every service.
 *
 * On the wire everything is plain JSON (no Spring type headers), so services stay decoupled
 * from each other's class names. Consumers use {@link ByteArrayJsonMessageConverter}, which
 * infers the target type from each @KafkaListener method's parameter — this lets a single
 * listener factory handle many message types across different topics.
 */
@Configuration
@EnableScheduling
public class KafkaSupportConfig {

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    // Most of these services don't depend on spring-web, so Boot's JacksonAutoConfiguration
    // (which needs Jackson2ObjectMapperBuilder from spring-web) never registers an ObjectMapper
    // bean. Provide one directly for the outbox JSON (de)serialization below. Services that DO
    // have spring-web (e.g. analytics-service) also get this as their MVC ObjectMapper, so it
    // needs JavaTimeModule too, or any Instant-bearing response DTO fails to serialize.
    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    /** JSON template for producing typed commands/events. */
    @Bean
    public ProducerFactory<String, Object> jsonProducerFactory() {
        Map<String, Object> p = new HashMap<>();
        p.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        p.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        p.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        p.put(JsonSerializer.ADD_TYPE_INFO_HEADERS, false);
        p.put(ProducerConfig.ACKS_CONFIG, "all");
        p.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        return new DefaultKafkaProducerFactory<>(p);
    }

    @Bean
    public KafkaTemplate<String, Object> jsonKafkaTemplate(ProducerFactory<String, Object> jsonProducerFactory) {
        return new KafkaTemplate<>(jsonProducerFactory);
    }

    /** Raw-bytes template used only by the dead-letter recoverer (republishes the failed record verbatim). */
    @Bean
    public ProducerFactory<byte[], byte[]> bytesProducerFactory() {
        Map<String, Object> p = new HashMap<>();
        p.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        p.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class);
        p.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class);
        p.put(ProducerConfig.ACKS_CONFIG, "all");
        return new DefaultKafkaProducerFactory<>(p);
    }

    @Bean
    public KafkaTemplate<byte[], byte[]> bytesKafkaTemplate(ProducerFactory<byte[], byte[]> bytesProducerFactory) {
        return new KafkaTemplate<>(bytesProducerFactory);
    }

    /** Boot wires this RecordMessageConverter into the auto-configured listener factory. */
    @Bean
    public ByteArrayJsonMessageConverter jsonMessageConverter(ObjectMapper mapper) {
        return new ByteArrayJsonMessageConverter(mapper);
    }

    /**
     * Consumer retry policy: bounded exponential backoff, then route the poison record to
     * "<topic>.DLT". Boot picks this up as the container's CommonErrorHandler automatically.
     */
    @Bean
    public DefaultErrorHandler errorHandler(KafkaTemplate<byte[], byte[]> bytesKafkaTemplate) {
        // Auto-created "<topic>.DLT" topics default to 1 partition; returning null lets the
        // producer's partitioner choose, instead of reusing the (possibly out-of-range) source partition.
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(bytesKafkaTemplate,
                (consumerRecord, exception) -> null);
        ExponentialBackOff backOff = new ExponentialBackOff();
        backOff.setMaxAttempts(3);
        backOff.setInitialInterval(500L);
        backOff.setMultiplier(2.0);
        backOff.setMaxInterval(4000L);
        return new DefaultErrorHandler(recoverer, backOff);
    }

    // ---- Topic definitions (KafkaAdmin creates any that don't yet exist) ----
    @Bean public NewTopic tOrderEvents()      { return TopicBuilder.name(Topics.ORDER_EVENTS).partitions(3).replicas(1).build(); }
    @Bean public NewTopic tInvReserveCmd()    { return TopicBuilder.name(Topics.INVENTORY_RESERVE_CMD).partitions(3).replicas(1).build(); }
    @Bean public NewTopic tInvReserveReply()  { return TopicBuilder.name(Topics.INVENTORY_RESERVE_REPLY).partitions(3).replicas(1).build(); }
    @Bean public NewTopic tInvReleaseCmd()    { return TopicBuilder.name(Topics.INVENTORY_RELEASE_CMD).partitions(3).replicas(1).build(); }
    @Bean public NewTopic tInvReleaseReply()  { return TopicBuilder.name(Topics.INVENTORY_RELEASE_REPLY).partitions(3).replicas(1).build(); }
    @Bean public NewTopic tPayProcessCmd()   { return TopicBuilder.name(Topics.PAYMENT_PROCESS_CMD).partitions(3).replicas(1).build(); }
    @Bean public NewTopic tPayProcessReply()  { return TopicBuilder.name(Topics.PAYMENT_PROCESS_REPLY).partitions(3).replicas(1).build(); }
    @Bean public NewTopic tPayRefundCmd()     { return TopicBuilder.name(Topics.PAYMENT_REFUND_CMD).partitions(3).replicas(1).build(); }
    @Bean public NewTopic tNotificationCmd()  { return TopicBuilder.name(Topics.NOTIFICATION_CMD).partitions(3).replicas(1).build(); }
}
