package io.hookport.delivery;

public record WebhookSendResult(
        AttemptOutcome outcome,
        Integer httpStatus,
        String errorMessage,
        long durationMs
) {
    public static WebhookSendResult fromHttpStatus(
            int status,
            long durationMs
    ) {
        if (status >= 200 && status < 300) {
            return new WebhookSendResult(
                    AttemptOutcome.DELIVERED,
                    status,
                    null,
                    durationMs
            );
        }

        if (status == 408 ||
                status == 425 ||
                status == 429 ||
                status >= 500) {
            return new WebhookSendResult(
                    AttemptOutcome.RETRYABLE_FAILURE,
                    status,
                    "Receiver returned HTTP " + status,
                    durationMs
            );
        }

        return new WebhookSendResult(
                AttemptOutcome.PERMANENT_FAILURE,
                status,
                "Receiver returned HTTP " + status,
                durationMs
        );
    }

    public static WebhookSendResult networkFailure(
            Exception exception,
            long durationMs
    ) {
        return new WebhookSendResult(
                AttemptOutcome.RETRYABLE_FAILURE,
                null,
                exception.getMessage(),
                durationMs
        );
    }

    public static WebhookSendResult abandoned(
            long durationMs
    ) {
        return new WebhookSendResult(
                AttemptOutcome.RETRYABLE_FAILURE,
                null,
                "Worker stopped before completing the delivery attempt",
                durationMs
        );
    }
}