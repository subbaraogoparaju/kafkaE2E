package com.kafka.practice.consumer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the Kafka Consumer service (port 8082).
 *
 * <p>Bootstraps the Spring Boot application, which activates:
 * <ul>
 *   <li>{@link com.kafka.practice.consumer.config.KafkaStreamsConfig} — Kafka Streams topology</li>
 *   <li>{@link com.kafka.practice.consumer.config.WebSocketConfig} — STOMP/WebSocket broker</li>
 *   <li>{@link com.kafka.practice.consumer.streams.OrderEventStream} — stream processing pipeline</li>
 * </ul>
 */
@SpringBootApplication
public class KafkaConsumerApplication {

    /**
     * Application entry point.
     *
     * @param args command-line arguments passed to the Spring context
     */
    public static void main(String[] args) {
        SpringApplication.run(KafkaConsumerApplication.class, args);
    }
}