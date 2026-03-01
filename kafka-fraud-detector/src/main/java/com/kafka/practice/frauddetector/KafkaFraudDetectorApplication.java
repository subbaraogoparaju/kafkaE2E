package com.kafka.practice.frauddetector;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the Kafka Fraud Detector service (port 8083).
 *
 * <p>This standalone microservice runs a Kafka Streams topology that reads
 * {@code order-events}, tracks consecutive repeated statuses per order, and
 * writes {@code FraudAlert} records to {@code order-fraud-alerts} whenever
 * the same status appears 3 or more times in a row for the same {@code orderId}.
 *
 * <p>Active components on startup:
 * <ul>
 *   <li>{@link com.kafka.practice.frauddetector.config.KafkaStreamsConfig}
 *       — Kafka Streams runtime configuration</li>
 *   <li>{@link com.kafka.practice.frauddetector.streams.FraudDetectionStream}
 *       — stream topology (source → stateful processor → sink)</li>
 * </ul>
 */
@SpringBootApplication
public class KafkaFraudDetectorApplication {

    public static void main(String[] args) {
        SpringApplication.run(KafkaFraudDetectorApplication.class, args);
    }
}