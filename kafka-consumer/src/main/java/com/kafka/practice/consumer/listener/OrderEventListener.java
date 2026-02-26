package com.kafka.practice.consumer.listener;

import com.kafka.practice.consumer.model.OrderEvent;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
// Superseded by OrderEventStream (Kafka Streams topology)
public class OrderEventListener {

    private static final Logger log = LoggerFactory.getLogger(OrderEventListener.class);

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