package io.hookport.delivery;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface WebhookDeliveryRepository
        extends JpaRepository<WebhookDelivery, UUID> {

    Optional<WebhookDelivery> findByEventIdAndEndpointId(UUID eventId, UUID endpointId);
}