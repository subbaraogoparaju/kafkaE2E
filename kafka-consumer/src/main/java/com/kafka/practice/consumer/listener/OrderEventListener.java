package com.kafka.practice.consumer.listener;

import com.kafka.practice.consumer.model.OrderEvent;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
/**
 * Simple Kafka listener for {@code order-events} messages.
 *
 * <p><strong>Note:</strong> This class has been superseded by
 * {@link com.kafka.practice.consumer.streams.OrderEventStream}, which uses the
 * Kafka Streams API and additionally pushes events to WebSocket clients. This
 * listener is kept for reference but is <em>not registered as a Spring bean</em>
 * and therefore does not run at startup.
 *
 * <p>When active, it consumes every message on the configured topic, logs
 * partition/offset metadata, and delegates status-specific handling to
 * {@link #processOrderEvent(OrderEvent)}.
 */
// Superseded by OrderEventStream (Kafka Streams topology)
public class OrderEventListener {

    private static final Logger log = LoggerFactory.getLogger(OrderEventListener.class);

    /**
     * Kafka listener method that receives each {@link OrderEvent} record.
     *
     * <p>Binds the consumer record's partition number and offset from Kafka
     * headers so they can be included in structured log output.
     *
     * @param record    the full Kafka record containing key and deserialized value
     * @param partition the partition from which the record was consumed
     * @param offset    the offset of the record within its partition
     */
    @KafkaListener(
            topics = "${kafka.topic.order-events}",
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void handleOrderEvent(
            ConsumerRecord<String, OrderEvent> record,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset) {

        OrderEvent event = record.value();
        log.info("Received order event | partition={} offset={} key={} | {}",
                partition, offset, record.key(), event);

        processOrderEvent(event);
    }

    /**
     * Dispatches status-specific log messages for an {@link OrderEvent}.
     *
     * <p>Logs at {@code INFO} for normal lifecycle transitions
     * ({@code CREATED}, {@code CONFIRMED}, {@code SHIPPED}, {@code DELIVERED})
     * and at {@code WARN} for {@code CANCELLED} or unrecognised statuses.
     *
     * @param event the deserialized order event to process
     */
    private void processOrderEvent(OrderEvent event) {
        switch (event.getStatus()) {
            case "CREATED"   -> log.info("Processing new order: {}", event.getOrderId());
            case "CONFIRMED" -> log.info("Order confirmed: {}", event.getOrderId());
            case "SHIPPED"   -> log.info("Order shipped: {}", event.getOrderId());
            case "DELIVERED" -> log.info("Order delivered: {}", event.getOrderId());
            case "CANCELLED" -> log.warn("Order cancelled: {}", event.getOrderId());
            default          -> log.warn("Unknown status '{}' for order: {}", event.getStatus(), event.getOrderId());
        }
    }
}