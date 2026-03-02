package com.kafka.practice.consumer.config;

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
 * <p>Handles two transient failure modes that would otherwise drive Kafka Streams
 * into the terminal ERROR state:
 *
 * <ul>
 *   <li>{@link MissingSourceTopicException} — source topic deleted/recreated during rebalance
 *       (KIP-662). REPLACE_THREAD retries the rebalance until the topic reappears.
 *   <li>{@link DisconnectException} as cause — broker restarted mid-commit; DNS resolution
 *       failed transiently during {@code commitSync()}. REPLACE_THREAD lets the new thread
 *       reconnect once the broker is back.
 * </ul>
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