package io.hookport.endpoint;

import io.hookport.delivery.DeliveryStatus;
import io.hookport.delivery.WebhookDelivery;
import io.hookport.delivery.WebhookDeliveryRepository;
import io.hookport.event.WebhookEvent;
import io.hookport.event.WebhookEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

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
}
