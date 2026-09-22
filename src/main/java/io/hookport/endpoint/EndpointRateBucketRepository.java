package io.hookport.endpoint;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface EndpointRateBucketRepository
        extends JpaRepository<EndpointRateBucket, UUID> {

    // Worker path: do not wait behind another worker.
    @Query(value = """
        SELECT *
        FROM endpoint_rate_buckets
        WHERE endpoint_id = :endpointId
        FOR UPDATE SKIP LOCKED
        """, nativeQuery = true)
    Optional<EndpointRateBucket> findForClaim(
            @Param("endpointId") UUID endpointId
    );

    // Rare API configuration update: wait for any active claim.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT b FROM EndpointRateBucket b
        WHERE b.endpointId = :endpointId
        """)
    Optional<EndpointRateBucket> findForSettingsUpdate(
            @Param("endpointId") UUID endpointId
    );
}