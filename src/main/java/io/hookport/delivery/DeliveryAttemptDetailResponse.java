package io.hookport.delivery;

import java.time.Instant;
import java.util.UUID;

public record DeliveryAttemptDetailResponse(
        UUID id,
        int attemptNumber,
        AttemptOutcome outcome,
        Integer httpStatus,
        Long durationMs,
        String errorMessage,
        Instant startedAt,
        Instant completedAt
) {
    public static DeliveryAttemptDetailResponse from(
            DeliveryAttempt attempt
    ) {
        return new DeliveryAttemptDetailResponse(
                attempt.getId(),
                attempt.getAttemptNumber(),
                attempt.getOutcome(),
                attempt.getHttpStatus(),
                attempt.getDurationMs(),
                attempt.getErrorMessage(),
                attempt.getStartedAt(),
                attempt.getCompletedAt()
        );
    }
}