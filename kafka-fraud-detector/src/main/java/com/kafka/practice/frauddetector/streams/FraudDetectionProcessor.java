package com.kafka.practice.frauddetector.streams;

import com.kafka.practice.frauddetector.model.FraudAlert;
import com.kafka.practice.frauddetector.model.OrderEvent;
import com.kafka.practice.frauddetector.model.OrderFraudState;
import org.apache.kafka.streams.processor.api.ContextualProcessor;
import org.apache.kafka.streams.processor.api.ProcessorContext;
import org.apache.kafka.streams.processor.api.Record;
import org.apache.kafka.streams.state.KeyValueStore;

import java.time.LocalDateTime;

/**
 * Stateful Kafka Streams processor that detects 3 or more consecutive identical
 * statuses for the same {@code orderId} and emits a {@link FraudAlert}.
 *
 * <h2>State-transition logic (per orderId)</h2>
 * <pre>
 *   incoming status == lastStatus  →  consecutiveCount++
 *   incoming status != lastStatus  →  lastStatus = incoming, consecutiveCount = 1
 *   consecutiveCount >= threshold  →  forward FraudAlert downstream
 * </pre>
 *
 * <p>Because the stream is re-keyed by {@code orderId} before this processor
 * (via {@code selectKey}), Kafka Streams guarantees that all events for the same
 * order are handled by the same stream task. The state store is therefore always
 * locally consistent — no cross-partition coordination is needed.
 *
 * <p>An alert is forwarded on every event that meets or exceeds the threshold
 * (count = 3, 4, 5 …). Downstream consumers can filter on
 * {@link FraudAlert#getConsecutiveCount()} == threshold for first-alert-only behaviour.
 */
public class FraudDetectionProcessor
        extends ContextualProcessor<String, OrderEvent, String, FraudAlert> {

    private final String storeName;
    private final int threshold;
    private KeyValueStore<String, OrderFraudState> store;

    FraudDetectionProcessor(String storeName, int threshold) {
        this.storeName = storeName;
        this.threshold = threshold;
    }

    @Override
    public void init(ProcessorContext<String, FraudAlert> context) {
        super.init(context);
        this.store = context.getStateStore(storeName);
    }

    /**
     * Processes one {@link OrderEvent} record keyed by the global sentinel key.
     *
     * <p>Only {@code CANCELLED} events increment the counter; any other status resets
     * it to zero. A {@link FraudAlert} is forwarded when the counter reaches the
     * threshold, carrying the triggering {@code orderId} for traceability.
     *
     * @param record the incoming record — key is the global sentinel, value is the event
     */
    @Override
    public void process(Record<String, OrderEvent> record) {
        String currentStatus = record.value().getStatus();

        OrderFraudState state = store.get(record.key());
        if (state == null) {
            state = new OrderFraudState("CANCELLED", 0);
        }

        if ("CANCELLED".equals(currentStatus)) {
            state.setConsecutiveCount(state.getConsecutiveCount() + 1);
        } else {
            state.setConsecutiveCount(0);
        }

        store.put(record.key(), state);

        if (state.getConsecutiveCount() >= threshold) {
            FraudAlert alert = new FraudAlert(
                    record.value().getOrderId(), "CANCELLED", state.getConsecutiveCount(), LocalDateTime.now());
            context().forward(record.withValue(alert));
        }
    }
}