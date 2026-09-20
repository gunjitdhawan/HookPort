package io.hookport.delivery;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DeliveryAttemptRepository
        extends JpaRepository<DeliveryAttempt, UUID> {

    List<DeliveryAttempt> findAllByDeliveryIdOrderByAttemptNumber(
            UUID deliveryId
    );

    Optional<DeliveryAttempt>
    findFirstByDeliveryIdAndCompletedAtIsNullOrderByAttemptNumberDesc(
            UUID deliveryId
    );

    Page<DeliveryAttempt> findByDelivery_Id(
            UUID deliveryId,
            Pageable pageable
    );
}