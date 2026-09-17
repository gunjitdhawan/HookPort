package io.hookport.endpoint;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface WebhookEndpointRepository
        extends JpaRepository<WebhookEndpoint, UUID> {

    boolean existsByName(String name);
    boolean existsByNameAndIdNot(String name, UUID id);
}