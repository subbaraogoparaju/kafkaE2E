package com.kafka.practice.consumer.config;

import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafkaStreams;
import org.springframework.kafka.annotation.KafkaStreamsDefaultConfiguration;
import org.springframework.kafka.config.KafkaStreamsConfiguration;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.streams.errors.LogAndContinueExceptionHandler;

import java.util.HashMap;
import java.util.Map;

/**
 * Configures the Kafka Streams runtime for the consumer service.
 *
 * <p>Produces the {@link KafkaStreamsConfiguration} bean that Spring Kafka
 * uses as the default Streams config. Key settings:
 * <ul>
 *   <li><b>application.id</b>: {@code order-streams-app} — uniquely identifies
 *       this Streams application in the Kafka cluster and is used as the
 *       internal consumer group ID.</li>
 *   <li><b>default key serde</b>: {@link Serdes#String()} — message keys are
 *       plain strings (order IDs).</li>
 *   <li><b>idempotence</b>: disabled — simplifies local development; enable in
 *       production for exactly-once semantics.</li>
 *   <li><b>deserialization error handler</b>:
 *       {@link LogAndContinueExceptionHandler} — logs corrupt records and
 *       continues processing instead of crashing the stream.</li>
 * </ul>
 */
@Configuration
@EnableKafkaStreams
public class KafkaStreamsConfig {

    /**
     * Creates the default {@link KafkaStreamsConfiguration} bean consumed by
     * {@link org.springframework.kafka.annotation.EnableKafkaStreams}.
     *
     * @param bootstrapServers Kafka broker address(es) injected from
     *                         {@code spring.kafka.bootstrap-servers}
     * @return fully configured {@link KafkaStreamsConfiguration}
     */
    @Bean(name = KafkaStreamsDefaultConfiguration.DEFAULT_STREAMS_CONFIG_BEAN_NAME)
    public KafkaStreamsConfiguration streamsConfig(
            @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers) {
        Map<String, Object> props = new HashMap<>();
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, "order-streams-app");
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass().getName());
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, false);
        props.put(StreamsConfig.DEFAULT_DESERIALIZATION_EXCEPTION_HANDLER_CLASS_CONFIG,
                LogAndContinueExceptionHandler.class);
        return new KafkaStreamsConfiguration(props);
    }
}
