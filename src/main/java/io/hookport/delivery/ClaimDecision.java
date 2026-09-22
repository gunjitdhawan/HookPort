package io.hookport.delivery;

public record ClaimDecision(
        ClaimedDelivery claimed,
        boolean foundDueDelivery
) {
    public static ClaimDecision claimed(ClaimedDelivery delivery) {
        return new ClaimDecision(delivery, true);
    }

    public static ClaimDecision deferred() {
        return new ClaimDecision(null, true);
    }

    public static ClaimDecision empty() {
        return new ClaimDecision(null, false);
    }
}