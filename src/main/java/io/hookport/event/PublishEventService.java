package io.hookport.event;

import io.hookport.delivery.WebhookDelivery;
import io.hookport.delivery.WebhookDeliveryRepository;
import io.hookport.endpoint.EndpointStatus;
import io.hookport.endpoint.WebhookEndpoint;
import io.hookport.endpoint.WebhookEndpointRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Service
public class PublishEventService {

    private final WebhookEndpointRepository endpointRepository;
    private final WebhookEventRepository eventRepository;
    private final WebhookDeliveryRepository deliveryRepository;

    public PublishEventService(
            WebhookEndpointRepository endpointRepository,
            WebhookEventRepository eventRepository,
            WebhookDeliveryRepository deliveryRepository
    ) {
        this.endpointRepository = endpointRepository;
        this.eventRepository = eventRepository;
        this.deliveryRepository = deliveryRepository;
    }

    @Transactional
    public PublishEventResponse publish(
            UUID endpointId,
            String idempotencyKey,
            PublishEventRequest request
    ) {
        String normalizedKey =
                validateIdempotencyKey(idempotencyKey);

        /*
         * Check idempotency before creating anything.
         */
        var existingEvent =
                eventRepository.findByIdempotencyKey(normalizedKey);

        if (existingEvent.isPresent()) {
            return handleExistingEvent(
                    existingEvent.get(),
                    endpointId,
                    request
            );
        }

        WebhookEndpoint endpoint = endpointRepository
                .findById(endpointId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Webhook endpoint not found"
                ));

        if (endpoint.getStatus() != EndpointStatus.ACTIVE) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Events cannot be published to a disabled endpoint"
            );
        }

        WebhookEvent event = eventRepository.save(
                WebhookEvent.create(
                        request.eventType().trim(),
                        request.payload(),
                        normalizedKey
                )
        );

        WebhookDelivery delivery = deliveryRepository.save(
                WebhookDelivery.pending(event, endpoint)
        );

        /*
         * Execute the inserts before creating the response.
         */
        deliveryRepository.flush();

        return toResponse(event, delivery, false);
    }

    private PublishEventResponse handleExistingEvent(
            WebhookEvent event,
            UUID endpointId,
            PublishEventRequest request
    ) {
        boolean sameEventType = event.getEventType()
                .equals(request.eventType().trim());

        boolean samePayload = event.getPayload()
                .equals(request.payload());

        if (!sameEventType || !samePayload) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Idempotency key was already used with different event data"
            );
        }

        WebhookDelivery delivery = deliveryRepository
                .findByEventIdAndEndpointId(
                        event.getId(),
                        endpointId
                )
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Idempotency key was already used for another endpoint"
                ));

        return toResponse(event, delivery, true);
    }

    private String validateIdempotencyKey(String value) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Idempotency-Key header is required"
            );
        }

        String normalized = value.trim();

        if (normalized.length() > 255) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Idempotency-Key must not exceed 255 characters"
            );
        }

        return normalized;
    }

    private PublishEventResponse toResponse(
            WebhookEvent event,
            WebhookDelivery delivery,
            boolean replayed
    ) {
        return new PublishEventResponse(
                event.getId(),
                delivery.getId(),
                event.getEventType(),
                delivery.getStatus(),
                event.getCreatedAt(),
                replayed
        );
    }
}