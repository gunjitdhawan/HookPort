package io.hookport.delivery;

import io.hookport.endpoint.EndpointRateBucket;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class EndpointRateBucketTest {

    @Test
    void refillsOneTokenAfterOneSecond() {
        Instant start = Instant.parse("2026-01-01T00:00:00Z");

        EndpointRateBucket bucket = EndpointRateBucket.full(
                UUID.randomUUID(), 2, 1, start
        );

        assertThat(bucket.trySpendOne(start)).isTrue();
        assertThat(bucket.trySpendOne(start)).isTrue();
        assertThat(bucket.trySpendOne(start)).isFalse();

        assertThat(bucket.trySpendOne(start.plusSeconds(1)))
                .isTrue();
        assertThat(bucket.trySpendOne(start.plusSeconds(1)))
                .isFalse();
    }
}