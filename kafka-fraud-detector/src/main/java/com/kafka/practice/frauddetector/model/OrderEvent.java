package com.kafka.practice.frauddetector.model;

import java.time.LocalDateTime;

/**
 * Consumer-side view of an order domain event from the {@code order-events} topic.
 *
 * <p>Only the fields needed by the fraud-detection logic are used here
 * ({@code orderId} and {@code status}); the rest are retained so the full
 * JSON payload deserializes cleanly without {@code FAIL_ON_UNKNOWN_PROPERTIES}.
 */
public class OrderEvent {

    private String orderId;
    private String product;
    private int quantity;
    private double price;
    private String status;
    private String message;
    private LocalDateTime eventTime;

    public OrderEvent() {}

    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }

    public String getProduct() { return product; }
    public void setProduct(String product) { this.product = product; }

    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }

    public double getPrice() { return price; }
    public void setPrice(double price) { this.price = price; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public LocalDateTime getEventTime() { return eventTime; }
    public void setEventTime(LocalDateTime eventTime) { this.eventTime = eventTime; }

    @Override
    public String toString() {
        return "OrderEvent{orderId='" + orderId + "', status='" + status + "', eventTime=" + eventTime + "}";
    }
}