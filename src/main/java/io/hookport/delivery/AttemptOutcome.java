package io.hookport.delivery;

public enum AttemptOutcome {
    DELIVERED,
    RETRYABLE_FAILURE,
    PERMANENT_FAILURE
}