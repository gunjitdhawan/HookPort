package io.hookport.delivery;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "hookport.delivery")
public class DeliveryProperties {

    private boolean schedulingEnabled = true;
    private long pollIntervalMs = 1000;
    private int batchSize = 20;
    private int maxAttempts = 5;
    private long baseDelaySeconds = 30;
    private long maxDelaySeconds = 3600;
    private long stuckTimeoutSeconds = 30;
    private int recoveryBatchSize = 20;

    public boolean isSchedulingEnabled() {
        return schedulingEnabled;
    }

    public void setSchedulingEnabled(boolean schedulingEnabled) {
        this.schedulingEnabled = schedulingEnabled;
    }

    public long getPollIntervalMs() {
        return pollIntervalMs;
    }

    public void setPollIntervalMs(long pollIntervalMs) {
        this.pollIntervalMs = pollIntervalMs;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public long getBaseDelaySeconds() {
        return baseDelaySeconds;
    }

    public void setBaseDelaySeconds(long baseDelaySeconds) {
        this.baseDelaySeconds = baseDelaySeconds;
    }

    public long getMaxDelaySeconds() {
        return maxDelaySeconds;
    }

    public void setMaxDelaySeconds(long maxDelaySeconds) {
        this.maxDelaySeconds = maxDelaySeconds;
    }

    public long getStuckTimeoutSeconds() {
        return stuckTimeoutSeconds;
    }

    public int getRecoveryBatchSize() {
        return recoveryBatchSize;
    }

    public void prop() {

    }
}