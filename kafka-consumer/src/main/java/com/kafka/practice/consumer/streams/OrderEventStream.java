package com.kafka.practice.consumer.streams;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.kafka.practice.consumer.model.OrderEvent;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.Branched;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.Named;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.support.serializer.JsonSerde;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
public class OrderEventStream {

    private static final Logger log = LoggerFactory.getLogger(OrderEventStream.class);

    @Value("${kafka.topic.order-events}")
    private String topic;

    private final SimpMessagingTemplate messagingTemplate;

    public OrderEventStream(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @Autowired
    void buildTopology(StreamsBuilder builder) {
        ObjectMapper mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        JsonSerde<OrderEvent> orderEventSerde = new JsonSerde<>(OrderEvent.class, mapper);

        KStream<String, OrderEvent> stream = builder.stream(
                topic, Consumed.with(Serdes.String(), orderEventSerde));

        stream.peek((key, event) -> {
            log.info("Received order event | key={} | {}", key, event);
            messagingTemplate.convertAndSend("/topic/orders", event);
        });

        stream.split(Named.as("status-"))
                .branch((k, e) -> "CREATED".equals(e.getStatus()),
                        Branched.withConsumer(s -> s.peek((k, e) ->
                                log.info("Processing new order: {}", e.getOrderId()))))
                .branch((k, e) -> "CONFIRMED".equals(e.getStatus()),
                        Branched.withConsumer(s -> s.peek((k, e) ->
                                log.info("Order confirmed: {}", e.getOrderId()))))
                .branch((k, e) -> "SHIPPED".equals(e.getStatus()),
                        Branched.withConsumer(s -> s.peek((k, e) ->
                                log.info("Order shipped: {}", e.getOrderId()))))
                .branch((k, e) -> "DELIVERED".equals(e.getStatus()),
                        Branched.withConsumer(s -> s.peek((k, e) ->
                                log.info("Order delivered: {}", e.getOrderId()))))
                .branch((k, e) -> "CANCELLED".equals(e.getStatus()),
                        Branched.withConsumer(s -> s.peek((k, e) ->
                                log.warn("Order cancelled: {}", e.getOrderId()))))
                .defaultBranch(
                        Branched.withConsumer(s -> s.peek((k, e) ->
                                log.warn("Unknown status '{}' for order: {}", e.getStatus(), e.getOrderId()))));
    }
}