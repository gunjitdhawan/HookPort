package io.hookport.delivery;

public enum DeliveryStatus {
    PENDING,
    IN_PROGRESS,
    DELIVERED,
    RETRY_SCHEDULED,
    EXHAUSTED
}