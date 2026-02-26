package com.kafka.practice.producer.model;

import java.time.LocalDateTime;

public class OrderEvent {

    private String orderId;
    private String product;
    private int quantity;
    private double price;
    private String status;
    private LocalDateTime eventTime;

    public OrderEvent() {
    }

    public OrderEvent(String orderId, String product, int quantity, double price, String status) {
        this.orderId = orderId;
        this.product = product;
        this.quantity = quantity;
        this.price = price;
        this.status = status;
        this.eventTime = LocalDateTime.now();
    }

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

    public LocalDateTime getEventTime() { return eventTime; }
    public void setEventTime(LocalDateTime eventTime) { this.eventTime = eventTime; }

    @Override
    public String toString() {
        return "OrderEvent{orderId='" + orderId + "', product='" + product +
               "', quantity=" + quantity + ", price=" + price +
               ", status='" + status + "', eventTime=" + eventTime + "}";
    }
}