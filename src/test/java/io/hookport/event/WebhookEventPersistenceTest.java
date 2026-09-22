package io.hookport.event;

import io.hookport.delivery.*;
import io.hookport.endpoint.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.time.Instant;
import java.util.concurrent.*;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

@SpringBootTest(properties = {
        "hookport.delivery.scheduling-enabled=false",
        "hookport.outbox.enabled=false",
        "hookport.security.allow-private-targets=true"
})
@Testcontainers
public class WebhookEventPersistenceTest {
    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:17-alpine");

    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private WebhookEventRepository eventRepository;

    @Autowired
    private DeliveryStateService deliveryStateService;

    @Autowired
    private DeliveryAttemptRepository attemptRepository;

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
        attemptRepository.deleteAll();
        deliveryRepository.deleteAll();
        jdbcTemplate.update("DELETE FROM event_outbox");
        eventRepository.deleteAll();
        repository.deleteAll();
    }

    @Test
    void shouldPersistEventAndPendingDelivery() {
        CreateEndpointResponse endpointResponse = service.create(
                new CreateEndpointRequest(
                        "payment-events",
                        "https://example.com/webhooks",
                        null,
                        null
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
        CreateEndpointResponse endpointResponse = service.create(
                new CreateEndpointRequest(
                        "payment-events",
                        "https://example.com/webhooks",
                        null,
                        null
                )
        );

        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("paymentId", "pay-123");
        payload.put("amount", 4999);

        PublishEventResponse published = publishEventService.publish(
                endpointResponse.id(),
                "payment-pay-123-completed",
                new PublishEventRequest("payment.completed", payload)
        );

        WebhookDelivery delivery = deliveryRepository
                .findById(published.deliveryId()).orElseThrow();
        assertThat(eventRepository.count()).isEqualTo(1);
        assertThat(deliveryRepository.count()).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM event_outbox WHERE event_id = ?",
                Long.class, published.eventId()
        )).isEqualTo(1L);
        assertThat(delivery.getStatus())
                .isEqualTo(DeliveryStatus.PENDING);
        assertThat(delivery.getAttemptCount()).isZero();
    }

    @Test
    void shouldReturnOriginalResultForIdempotentReplay() {
        CreateEndpointResponse endpointResponse = service.create(
                new CreateEndpointRequest(
                        "payment-events",
                        "https://example.com/webhooks",
                        null,
                        null
                )
        );

        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("paymentId", "pay-123");
        payload.put("amount", 4999);

        PublishEventRequest request = new PublishEventRequest(
                "payment.completed", payload
        );
        PublishEventResponse first = publishEventService.publish(
                endpointResponse.id(), "payment-pay-123-completed", request
        );
        PublishEventResponse second = publishEventService.publish(
                endpointResponse.id(), "payment-pay-123-completed", request
        );

        assertThat(second.eventId()).isEqualTo(first.eventId());
        assertThat(second.deliveryId()).isEqualTo(first.deliveryId());
        assertThat(second.replayed()).isTrue();
        assertThat(eventRepository.count()).isEqualTo(1);
        assertThat(deliveryRepository.count()).isEqualTo(1);
    }

    @Test
    void shouldRejectIdempotencyKeyWithDifferentPayload() {
        CreateEndpointResponse endpoint = service.create(
                new CreateEndpointRequest(
                        "payment-events",
                        "https://example.com/webhooks",
                        null,
                        null
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
                        "https://example.com/webhooks",
                        null,
                        null
                )
        );

        UpdateEndpointRequest disableRequest =
                new UpdateEndpointRequest(
                        null,
                        null,
                        EndpointStatus.DISABLED,
                        null,
                        null
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

    @Test
    void shouldRecoverDeliveryStuckInProgress() {
        CreateEndpointResponse endpoint = service.create(
                new CreateEndpointRequest(
                        "recovery-endpoint",
                        "http://localhost:9999/webhook",
                        null,
                        null
                )
        );

        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("message", "recovery test");

        PublishEventResponse published =
                publishEventService.publish(
                        endpoint.id(),
                        "recovery-test-key",
                        new PublishEventRequest(
                                "test.recovery",
                                payload
                        )
                );

        ClaimedDelivery claimed =
                deliveryStateService.claim(
                        published.deliveryId()
                );

        /*
         * A future cutoff makes the recently claimed delivery
         * eligible without making the test sleep.
         */
        int recovered =
                deliveryStateService.recoverStuckDeliveries(
                        Instant.now().plusSeconds(1),
                        10
                );

        assertThat(recovered).isEqualTo(1);

        WebhookDelivery delivery = deliveryRepository
                .findById(published.deliveryId())
                .orElseThrow();

        assertThat(delivery.getStatus())
                .isEqualTo(DeliveryStatus.RETRY_SCHEDULED);

        assertThat(delivery.getNextAttemptAt()).isNotNull();

        DeliveryAttempt attempt = attemptRepository
                .findById(claimed.attemptId())
                .orElseThrow();

        assertThat(attempt.getOutcome())
                .isEqualTo(AttemptOutcome.RETRYABLE_FAILURE);

        assertThat(attempt.getCompletedAt()).isNotNull();
    }

    @Test
    void shouldAllowOnlyOneWorkerToClaimDelivery()
            throws Exception {

        CreateEndpointResponse endpoint = service.create(
                new CreateEndpointRequest(
                        "concurrent-endpoint",
                        "http://localhost:9999/webhook",
                        null,
                        null
                )
        );

        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("message", "concurrency test");

        publishEventService.publish(
                endpoint.id(),
                "concurrent-test-key",
                new PublishEventRequest(
                        "test.concurrent",
                        payload
                )
        );

        ExecutorService executor =
                Executors.newFixedThreadPool(2);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        Callable<Integer> claimTask = () -> {
            ready.countDown();
            start.await();

            return deliveryStateService.claimNextDue().claimed() == null
                    ? 0 : 1;
        };

        try {
            Future<Integer> first =
                    executor.submit(claimTask);

            Future<Integer> second =
                    executor.submit(claimTask);

            ready.await();
            start.countDown();

            int totalClaimed =
                    first.get() + second.get();

            assertThat(totalClaimed).isEqualTo(1);
            assertThat(attemptRepository.count()).isEqualTo(1);

            WebhookDelivery delivery =
                    deliveryRepository.findAll()
                            .getFirst();

            assertThat(delivery.getStatus())
                    .isEqualTo(DeliveryStatus.IN_PROGRESS);

            assertThat(delivery.getAttemptCount())
                    .isEqualTo(1);

        } finally {
            executor.shutdownNow();
        }
    }
}
