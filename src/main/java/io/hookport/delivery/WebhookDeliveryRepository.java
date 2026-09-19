package io.hookport.delivery;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WebhookDeliveryRepository
        extends JpaRepository<WebhookDelivery, UUID> {

    Optional<WebhookDelivery> findByEventIdAndEndpointId(UUID eventId, UUID endpointId);

    @Query(
            value = """
                SELECT *
                FROM webhook_deliveries
                WHERE status IN ('PENDING', 'RETRY_SCHEDULED')
                  AND (
                      next_attempt_at IS NULL
                      OR next_attempt_at <= :now
                  )
                ORDER BY next_attempt_at ASC, created_at ASC
                FOR UPDATE SKIP LOCKED
                LIMIT :batchSize
                """,
            nativeQuery = true
    )
    List<WebhookDelivery> findDueForUpdate(
            @Param("now") Instant now,
            @Param("batchSize") int batchSize
    );
}