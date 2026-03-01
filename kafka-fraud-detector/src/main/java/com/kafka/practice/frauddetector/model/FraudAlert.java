package com.kafka.practice.frauddetector.model;

import java.time.LocalDateTime;

/**
 * Fraud alert published to the {@code order-fraud-alerts} topic when an order's
 * status repeats consecutively at or beyond the detection threshold (default: 3).
 *
 * <p>Consumers of {@code order-fraud-alerts} can distinguish "first alert"
 * ({@code consecutiveCount == threshold}) from "escalating" alerts
 * ({@code consecutiveCount > threshold}) and act accordingly.
 */
public class FraudAlert {

    /** The order ID for which the fraud pattern was detected. */
    private String orderId;

    /** The repeated status value that triggered the alert (e.g. {@code "CANCELLED"}). */
    private String status;

    /** Number of consecutive occurrences at the time of emission (>= threshold). */
    private int consecutiveCount;

    /** Wall-clock timestamp on the fraud-detector node when the alert was created. */
    private LocalDateTime detectedAt;

    public FraudAlert() {}

    public FraudAlert(String orderId, String status, int consecutiveCount, LocalDateTime detectedAt) {
        this.orderId = orderId;
        this.status = status;
        this.consecutiveCount = consecutiveCount;
        this.detectedAt = detectedAt;
    }

    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public int getConsecutiveCount() { return consecutiveCount; }
    public void setConsecutiveCount(int consecutiveCount) { this.consecutiveCount = consecutiveCount; }

    public LocalDateTime getDetectedAt() { return detectedAt; }
    public void setDetectedAt(LocalDateTime detectedAt) { this.detectedAt = detectedAt; }

    @Override
    public String toString() {
        return "FraudAlert{orderId='" + orderId + "', status='" + status +
               "', consecutiveCount=" + consecutiveCount + ", detectedAt=" + detectedAt + "}";
    }
}