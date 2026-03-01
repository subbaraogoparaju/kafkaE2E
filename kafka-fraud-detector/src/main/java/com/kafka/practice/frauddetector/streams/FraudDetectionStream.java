package com.kafka.practice.frauddetector.streams;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.kafka.practice.frauddetector.model.FraudAlert;
import com.kafka.practice.frauddetector.model.OrderEvent;
import com.kafka.practice.frauddetector.model.OrderFraudState;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.Produced;
import org.apache.kafka.streams.state.KeyValueStore;
import org.apache.kafka.streams.state.StoreBuilder;
import org.apache.kafka.streams.state.Stores;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.support.serializer.JsonSerde;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * Kafka Streams topology for order-level fraud detection.
 *
 * <h2>Pipeline</h2>
 * <pre>
 *   order-events  (source topic)
 *        │
 *        ▼  Consumed&lt;String, OrderEvent&gt;
 *        │
 *        ▼  selectKey(orderId)
 *        │    re-keys the stream so all events for the same order go to the
 *        │    same stream task → state is locally consistent, no coordination needed
 *        │
 *        ▼  process(FraudDetectionProcessor, "fraud-state-store")
 *        │    reads/writes OrderFraudState (lastStatus + consecutiveCount) per orderId
 *        │    emits FraudAlert when consecutiveCount >= threshold (default 3)
 *        │
 *        ├─ peek  →  WARN log
 *        │
 *        └─ to "order-fraud-alerts"  (sink topic)
 * </pre>
 *
 * <p>The {@code fraud-state-store} is a persistent RocksDB {@link KeyValueStore}.
 * On restart, Kafka Streams replays the internal changelog topic to restore state,
 * so no in-flight fraud patterns are lost across service restarts.
 */
@Component
public class FraudDetectionStream {

    private static final Logger log = LoggerFactory.getLogger(FraudDetectionStream.class);
    private static final String FRAUD_STATE_STORE = "fraud-state-store";

    @Value("${kafka.topic.order-events}")
    private String orderEventsTopic;

    @Value("${kafka.topic.fraud-alerts}")
    private String fraudAlertsTopic;

    @Value("${fraud.detection.consecutive-threshold:3}")
    private int threshold;

    private final SimpMessagingTemplate messagingTemplate;

    public FraudDetectionStream(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * Builds and registers the fraud-detection topology into the shared
     * {@link StreamsBuilder} provided by {@code @EnableKafkaStreams}.
     *
     * @param builder the DSL topology builder injected by Spring Kafka
     */
    @Autowired
    void buildTopology(StreamsBuilder builder) {
        ObjectMapper mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        JsonSerde<OrderEvent>     orderEventSerde  = new JsonSerde<>(OrderEvent.class,     mapper);
        JsonSerde<OrderFraudState> fraudStateSerde = new JsonSerde<>(OrderFraudState.class, mapper);
        JsonSerde<FraudAlert>     fraudAlertSerde  = new JsonSerde<>(FraudAlert.class,     mapper);

        // Persistent RocksDB store keyed by orderId
        StoreBuilder<KeyValueStore<String, OrderFraudState>> storeBuilder =
                Stores.keyValueStoreBuilder(
                        Stores.persistentKeyValueStore(FRAUD_STATE_STORE),
                        Serdes.String(),
                        fraudStateSerde);
        builder.addStateStore(storeBuilder);

        builder.stream(orderEventsTopic, Consumed.with(Serdes.String(), orderEventSerde))
                // Single global key — one shared counter tracks consecutive CANCELLEDs
                // across all orders, regardless of orderId
                .selectKey((k, v) -> "GLOBAL")
                // Stateful fraud detection — emits FraudAlert when threshold is reached
                .<String, FraudAlert>process(
                        () -> new FraudDetectionProcessor(FRAUD_STATE_STORE, threshold),
                        FRAUD_STATE_STORE)
                // Log, push to WebSocket subscribers, then sink to the fraud-alerts topic
                .peek((key, alert) -> {
                    log.warn("[FRAUD DETECTED] {} consecutive CANCELLED events — last orderId={}",
                            alert.getConsecutiveCount(), alert.getOrderId());
                    messagingTemplate.convertAndSend("/topic/fraud-alerts", alert);
                })
                .to(fraudAlertsTopic, Produced.with(Serdes.String(), fraudAlertSerde));
    }
}