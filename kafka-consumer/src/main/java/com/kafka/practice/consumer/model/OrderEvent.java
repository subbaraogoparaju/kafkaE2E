package com.kafka.practice.consumer.model;

import java.time.LocalDateTime;

/**
 * Represents an order domain event consumed from the {@code order-events} Kafka topic.
 *
 * <p>This is the consumer-side mirror of the producer's {@code OrderEvent}. Jackson
 * deserializes JSON payloads into this POJO via {@link org.springframework.kafka.support.serializer.JsonSerde}.
 *
 * <p>Supported {@code status} values: {@code CREATED}, {@code CONFIRMED},
 * {@code SHIPPED}, {@code DELIVERED}, {@code CANCELLED}.
 */
public class OrderEvent {

    /** Unique identifier for the order (e.g. UUID string). */
    private String orderId;

    /** Name or SKU of the ordered product. */
    private String product;

    /** Number of units ordered. */
    private int quantity;

    /** Unit price of the product at the time of the event. */
    private double price;

    /** Lifecycle status of the order (e.g. {@code CREATED}, {@code SHIPPED}). */
    private String status;

    /** Optional human-readable message accompanying the event. */
    private String message;

    /** Timestamp when the event was created on the producer side. */
    private LocalDateTime eventTime;

    /** No-arg constructor required by Jackson for deserialization. */
    public OrderEvent() {
    }

    /** @return the unique order identifier */
    public String getOrderId() { return orderId; }
    /** @param orderId the unique order identifier */
    public void setOrderId(String orderId) { this.orderId = orderId; }

    /** @return the product name or SKU */
    public String getProduct() { return product; }
    /** @param product the product name or SKU */
    public void setProduct(String product) { this.product = product; }

    /** @return the number of units ordered */
    public int getQuantity() { return quantity; }
    /** @param quantity the number of units ordered */
    public void setQuantity(int quantity) { this.quantity = quantity; }

    /** @return the unit price of the product */
    public double getPrice() { return price; }
    /** @param price the unit price of the product */
    public void setPrice(double price) { this.price = price; }

    /** @return the order lifecycle status */
    public String getStatus() { return status; }
    /** @param status the order lifecycle status */
    public void setStatus(String status) { this.status = status; }

    /** @return the optional event message */
    public String getMessage() { return message; }
    /** @param message the optional event message */
    public void setMessage(String message) { this.message = message; }

    /** @return the timestamp when the event was created */
    public LocalDateTime getEventTime() { return eventTime; }
    /** @param eventTime the timestamp when the event was created */
    public void setEventTime(LocalDateTime eventTime) { this.eventTime = eventTime; }

    @Override
    public String toString() {
        return "OrderEvent{orderId='" + orderId + "', product='" + product +
               "', quantity=" + quantity + ", price=" + price +
               ", status='" + status + "', message='" + message + "', eventTime=" + eventTime + "}";
    }
}