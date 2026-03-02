package com.kafka.practice.frauddetector.config;

import org.apache.kafka.common.errors.DisconnectException;
import org.apache.kafka.streams.errors.MissingSourceTopicException;
import org.apache.kafka.streams.errors.StreamsUncaughtExceptionHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.StreamsBuilderFactoryBeanConfigurer;

/**
 * Registers a {@link StreamsUncaughtExceptionHandler} on the Kafka Streams client.
 *
 * <p>When the source topic is deleted/recreated while the app is running, Kafka Streams
 * throws {@link MissingSourceTopicException} during rebalance and — with no handler —
 * transitions to the terminal ERROR state requiring a manual restart (KIP-662 / KIP-696).
 *
 * <p>Returning {@code REPLACE_THREAD} instructs Kafka Streams to discard the dead stream
 * thread, start a replacement, and retry the rebalance. The client stays in
 * RUNNING/REBALANCING and recovers automatically once the topic is available again.
 */
@Configuration
public class KafkaStreamsExceptionHandlerConfig {

    private static final Logger log = LoggerFactory.getLogger(KafkaStreamsExceptionHandlerConfig.class);

    @Bean
    public StreamsBuilderFactoryBeanConfigurer streamsExceptionHandler() {
        return factoryBean -> factoryBean.setStreamsUncaughtExceptionHandler(exception -> {
            if (exception instanceof MissingSourceTopicException) {
                log.warn("Source topic missing during rebalance — replacing stream thread to retry: {}",
                        exception.getMessage());
                return StreamsUncaughtExceptionHandler.StreamThreadExceptionResponse.REPLACE_THREAD;
            }
            if (exception instanceof DisconnectException
                    || exception.getCause() instanceof DisconnectException) {
                log.warn("Broker disconnect during commit (transient) — replacing stream thread to retry: {}",
                        exception.getMessage());
                return StreamsUncaughtExceptionHandler.StreamThreadExceptionResponse.REPLACE_THREAD;
            }
            log.error("Unrecoverable Kafka Streams exception — shutting down client", exception);
            return StreamsUncaughtExceptionHandler.StreamThreadExceptionResponse.SHUTDOWN_CLIENT;
        });
    }
}