package com.kafka.practice.frauddetector.model;

/**
 * Per-order state persisted in the Kafka Streams RocksDB state store.
 *
 * <p>Tracks the most recently observed {@link OrderEvent} status and the number
 * of consecutive times that status has appeared without interruption. When
 * {@code consecutiveCount} reaches the configured threshold, a
 * {@link FraudAlert} is emitted.
 *
 * <p>Jackson no-arg constructor is required for the {@link JsonSerde} used as
 * the state store's value serde.
 */
public class OrderFraudState {

    /** Most recently seen status for this order (e.g. {@code "CANCELLED"}). */
    private String lastStatus;

    /** How many consecutive events have carried {@code lastStatus}. Resets to 1 on any status change. */
    private int consecutiveCount;

    public OrderFraudState() {}

    public OrderFraudState(String lastStatus, int consecutiveCount) {
        this.lastStatus = lastStatus;
        this.consecutiveCount = consecutiveCount;
    }

    public String getLastStatus() { return lastStatus; }
    public void setLastStatus(String lastStatus) { this.lastStatus = lastStatus; }

    public int getConsecutiveCount() { return consecutiveCount; }
    public void setConsecutiveCount(int consecutiveCount) { this.consecutiveCount = consecutiveCount; }

    @Override
    public String toString() {
        return "OrderFraudState{lastStatus='" + lastStatus + "', consecutiveCount=" + consecutiveCount + "}";
    }
}