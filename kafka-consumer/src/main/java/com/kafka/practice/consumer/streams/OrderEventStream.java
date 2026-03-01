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

/**
 * Kafka Streams topology that consumes {@link OrderEvent} records from the
 * {@code order-events} topic, broadcasts them to WebSocket subscribers, and
 * routes each event to a status-specific log branch.
 *
 * <h2>Pipeline overview</h2>
 * <pre>
 *   order-events topic
 *        │
 *        ▼
 *   KStream&lt;String, OrderEvent&gt;
 *        │
 *        ├─ peek → log + push to WebSocket /topic/orders
 *        │
 *        └─ split by status
 *               ├─ CREATED   → log INFO
 *               ├─ CONFIRMED → log INFO
 *               ├─ SHIPPED   → log INFO
 *               ├─ DELIVERED → log INFO
 *               ├─ CANCELLED → log WARN
 *               └─ default   → log WARN (unknown status)
 * </pre>
 *
 * <p>The {@link com.fasterxml.jackson.databind.ObjectMapper} is configured with
 * {@link JavaTimeModule} so that {@link java.time.LocalDateTime} fields in
 * {@link OrderEvent} are serialized/deserialized as ISO-8601 strings rather than
 * numeric timestamps.
 */
@Component
public class OrderEventStream {

    private static final Logger log = LoggerFactory.getLogger(OrderEventStream.class);

    /** Kafka topic name resolved from {@code kafka.topic.order-events} property. */
    @Value("${kafka.topic.order-events}")
    private String topic;

    /** STOMP template used to forward events to WebSocket subscribers. */
    private final SimpMessagingTemplate messagingTemplate;

    /**
     * @param messagingTemplate Spring's STOMP messaging template, injected by the container
     */
    public OrderEventStream(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * Builds and registers the Kafka Streams processing topology.
     *
     * <p>Called by Spring during context refresh via {@link Autowired} on the
     * {@link StreamsBuilder} bean provided by {@code @EnableKafkaStreams}.
     *
     * <p>Two independent traversals of the same stream are set up:
     * <ol>
     *   <li>A {@code peek} that logs every incoming record and forwards it to
     *       the WebSocket destination {@code /topic/orders}.</li>
     *   <li>A {@code split} that fans the stream out into named sub-streams,
     *       one per order lifecycle status, each logging at the appropriate level.</li>
     * </ol>
     *
     * @param builder the {@link StreamsBuilder} used to construct the DSL topology
     */
    @Autowired
    void buildTopology(StreamsBuilder builder) {
        ObjectMapper mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        JsonSerde<OrderEvent> orderEventSerde = new JsonSerde<>(OrderEvent.class, mapper);

        KStream<String, OrderEvent> stream = builder.stream(
                topic, Consumed.with(Serdes.String(), orderEventSerde));

        // Forward every event to WebSocket subscribers and log receipt
        stream.peek((key, event) -> {
            log.info("Received order event | key={} | {}", key, event);
            messagingTemplate.convertAndSend("/topic/orders", event);
        });

        // Route to status-specific branches for targeted logging
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