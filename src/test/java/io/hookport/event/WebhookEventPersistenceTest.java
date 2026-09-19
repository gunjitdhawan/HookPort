package io.hookport.event;

import io.hookport.delivery.DeliveryStatus;
import io.hookport.delivery.WebhookDelivery;
import io.hookport.delivery.WebhookDeliveryRepository;
import io.hookport.endpoint.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

@SpringBootTest
@Testcontainers
public class WebhookEventPersistenceTest {
    @Autowired
    private WebhookEventRepository eventRepository;

    @Autowired
    private WebhookDeliveryRepository deliveryRepository;

    @Autowired
    private WebhookEndpointService service;

    @Autowired
    private WebhookEndpointRepository repository;

    @Autowired
    private PublishEventService publishEventService;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void cleanDatabase() {
        deliveryRepository.deleteAll();
        eventRepository.deleteAll();
        repository.deleteAll();
    }

    @Test
    void shouldPersistEventAndPendingDelivery() {
        CreateEndpointResponse endpointResponse = service.create(
                new CreateEndpointRequest(
                        "payment-events",
                        "https://example.com/webhooks"
                )
        );

        WebhookEndpoint endpoint = repository
                .findById(endpointResponse.id())
                .orElseThrow();

        ObjectMapper objectMapper = new ObjectMapper();

        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("paymentId", "pay-123");
        payload.put("amount", 4999);

        WebhookEvent event = eventRepository.save(
                WebhookEvent.create(
                        "payment.completed",
                        payload,
                        "payment-pay-123-completed"
                )
        );

        WebhookDelivery delivery = deliveryRepository.saveAndFlush(
                WebhookDelivery.pending(event, endpoint)
        );

        assertThat(event.getId()).isNotNull();
        assertThat(delivery.getId()).isNotNull();
        assertThat(delivery.getStatus())
                .isEqualTo(DeliveryStatus.PENDING);
        assertThat(delivery.getAttemptCount()).isZero();
        assertThat(delivery.getVersion()).isZero();
    }

    @Test
    void shouldCreateEventAndPendingDeliveryAtomically() {
        // Create active endpoint
        // Publish event
        // Assert one event and one PENDING delivery exist
        CreateEndpointResponse endpointResponse = service.create(
                new CreateEndpointRequest(
                        "payment-events",
                        "https://example.com/webhooks"
                )
        );

        WebhookEndpoint endpoint = repository
                .findById(endpointResponse.id())
                .orElseThrow();

        ObjectMapper objectMapper = new ObjectMapper();

        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("paymentId", "pay-123");
        payload.put("amount", 4999);

        WebhookEvent event = eventRepository.save(
                WebhookEvent.create(
                        "payment.completed",
                        payload,
                        "payment-pay-123-completed"
                )
        );

        WebhookDelivery delivery = deliveryRepository.saveAndFlush(
                WebhookDelivery.pending(event, endpoint)
        );

        assertThat(event.getId()).isNotNull();
        assertThat(delivery.getId()).isNotNull();
        assertThat(delivery.getStatus())
                .isEqualTo(DeliveryStatus.PENDING);
        assertThat(delivery.getAttemptCount()).isZero();
        assertThat(delivery.getVersion()).isZero();
    }

    @Test
    void shouldReturnOriginalResultForIdempotentReplay() {
        // Publish twice with the same key and payload
        // Assert IDs match and database counts remain 1
        CreateEndpointResponse endpointResponse = service.create(
                new CreateEndpointRequest(
                        "payment-events",
                        "https://example.com/webhooks"
                )
        );

        WebhookEndpoint endpoint = repository
                .findById(endpointResponse.id())
                .orElseThrow();

        ObjectMapper objectMapper = new ObjectMapper();

        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("paymentId", "pay-123");
        payload.put("amount", 4999);

        WebhookEvent event = eventRepository.save(
                WebhookEvent.create(
                        "payment.completed",
                        payload,
                        "payment-pay-123-completed"
                )
        );

        WebhookDelivery delivery = deliveryRepository.saveAndFlush(
                WebhookDelivery.pending(event, endpoint)
        );

        WebhookEvent event2 = eventRepository.save(
                WebhookEvent.create(
                        "payment.completed",
                        payload,
                        "payment-pay-123-completed"
                )
        );

        WebhookDelivery delivery2 = deliveryRepository.saveAndFlush(
                WebhookDelivery.pending(event, endpoint)
        );

        assertThat(event.getId()).isEqualTo(event2.getId());
    }

    @Test
    void shouldRejectIdempotencyKeyWithDifferentPayload() {
        CreateEndpointResponse endpoint = service.create(
                new CreateEndpointRequest(
                        "payment-events",
                        "https://example.com/webhooks"
                )
        );

        ObjectNode originalPayload = objectMapper.createObjectNode();
        originalPayload.put("paymentId", "pay-123");
        originalPayload.put("amount", 4999);

        PublishEventResponse firstResponse = publishEventService.publish(
                endpoint.id(),
                "payment-pay-123-completed",
                new PublishEventRequest(
                        "payment.completed",
                        originalPayload
                )
        );

        ObjectNode differentPayload = originalPayload.deepCopy();
        differentPayload.put("amount", 5000);

        assertThatThrownBy(() -> publishEventService.publish(
                endpoint.id(),
                "payment-pay-123-completed",
                new PublishEventRequest(
                        "payment.completed",
                        differentPayload
                )
        ))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(exception -> {
                    ResponseStatusException responseException =
                            (ResponseStatusException) exception;

                    assertThat(responseException.getStatusCode())
                            .isEqualTo(HttpStatus.CONFLICT);
                });

        assertThat(eventRepository.count()).isEqualTo(1);
        assertThat(deliveryRepository.count()).isEqualTo(1);

        WebhookEvent persistedEvent = eventRepository
                .findById(firstResponse.eventId())
                .orElseThrow();

        assertThat(persistedEvent.getPayload().get("amount").asInt())
                .isEqualTo(4999);
    }

    @Test
    void shouldRejectPublishingToDisabledEndpoint() {
        // Disable endpoint
        // Attempt publish
        // Assert 409 and no event/delivery was created
        CreateEndpointResponse endpoint = service.create(
                new CreateEndpointRequest(
                        "payment-events",
                        "https://example.com/webhooks"
                )
        );

        UpdateEndpointRequest disableRequest =
                new UpdateEndpointRequest(
                        null,
                        null,
                        EndpointStatus.DISABLED
                );

        EndpointResponse disabledEndpoint = service.update(
                endpoint.id(),
                disableRequest,
                endpoint.version()
        );

        assertThat(disabledEndpoint.status())
                .isEqualTo(EndpointStatus.DISABLED);

        ObjectNode originalPayload = objectMapper.createObjectNode();
        originalPayload.put("paymentId", "pay-123");
        originalPayload.put("amount", 4999);



        assertThatThrownBy(() -> publishEventService.publish(
                endpoint.id(),
                "payment-pay-123-completed",
                new PublishEventRequest(
                        "payment.completed",
                        originalPayload
                )
        ))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(exception -> {
                    ResponseStatusException responseException =
                            (ResponseStatusException) exception;

                    assertThat(responseException.getStatusCode())
                            .isEqualTo(HttpStatus.CONFLICT);
                });

        assertThat(eventRepository.count()).isEqualTo(0);
        assertThat(deliveryRepository.count()).isEqualTo(0);
    }
}
