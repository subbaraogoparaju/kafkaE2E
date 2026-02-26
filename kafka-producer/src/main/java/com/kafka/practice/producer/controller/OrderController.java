package com.kafka.practice.producer.controller;

import com.kafka.practice.producer.model.OrderEvent;
import com.kafka.practice.producer.service.OrderEventProducer;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderEventProducer producer;

    public OrderController(OrderEventProducer producer) {
        this.producer = producer;
    }

    @PostMapping
    public ResponseEntity<String> placeOrder(@RequestBody OrderEvent event) {
        if (event.getOrderId() == null || event.getOrderId().isBlank()) {
            event.setOrderId(UUID.randomUUID().toString());
        }
        producer.sendOrderEvent(event);
        return ResponseEntity.accepted().body("Order event queued: " + event.getOrderId());
    }

    @PostMapping("/sample")
    public ResponseEntity<String> placeSampleOrder() {
        OrderEvent event = new OrderEvent(
                UUID.randomUUID().toString(),
                "Laptop",
                1,
                999.99,
                "CREATED"
        );
        producer.sendOrderEvent(event);
        return ResponseEntity.accepted().body("Sample order event queued: " + event.getOrderId());
    }
}