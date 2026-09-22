package io.hookport.event;

import com.sun.net.httpserver.HttpServer;
import io.hookport.delivery.AttemptOutcome;
import io.hookport.delivery.DeliveryAttempt;
import io.hookport.delivery.DeliveryAttemptRepository;
import io.hookport.delivery.DeliveryStatus;
import io.hookport.delivery.DeliveryWorker;
import io.hookport.delivery.WebhookDeliveryRepository;
import io.hookport.delivery.WebhookSigner;
import io.hookport.endpoint.CreateEndpointRequest;
import io.hookport.endpoint.CreateEndpointResponse;
import io.hookport.endpoint.WebhookEndpointRepository;
import io.hookport.endpoint.WebhookEndpointService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;

import java.net.InetSocketAddress;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "hookport.delivery.scheduling-enabled=false",
        "hookport.outbox.enabled=false",
        "hookport.security.allow-private-targets=true"
})
@Testcontainers
class WebhookDeliveryEndToEndTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:17-alpine");

    @Autowired
    WebhookEndpointService endpointService;

    @Autowired
    PublishEventService publishEventService;

    @Autowired
    DeliveryWorker worker;

    @Autowired
    WebhookSigner signer;

    @Autowired
    WebhookDeliveryRepository deliveryRepository;

    @Autowired
    DeliveryAttemptRepository attemptRepository;

    @Autowired
    WebhookEventRepository eventRepository;

    @Autowired
    WebhookEndpointRepository endpointRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    ObjectMapper objectMapper;

    private HttpServer receiver;

    private final AtomicInteger receiverStatus =
            new AtomicInteger(204);

    private final ArrayBlockingQueue<ReceivedWebhook> received =
            new ArrayBlockingQueue<>(10);

    record ReceivedWebhook(
            String eventId,
            String eventType,
            String timestamp,
            String signature,
            byte[] body
    ) {
    }

    record Published(
            CreateEndpointResponse endpoint,
            PublishEventResponse event
    ) {
    }

    @BeforeEach
    void setUp() throws Exception {
        attemptRepository.deleteAll();
        deliveryRepository.deleteAll();
        jdbcTemplate.update("DELETE FROM event_outbox");
        eventRepository.deleteAll();
        endpointRepository.deleteAll();

        received.clear();
        receiverStatus.set(204);

        receiver = HttpServer.create(
                new InetSocketAddress("127.0.0.1", 0),
                0
        );

        receiver.createContext("/webhook", exchange -> {
            byte[] body =
                    exchange.getRequestBody().readAllBytes();

            received.add(new ReceivedWebhook(
                    exchange.getRequestHeaders()
                            .getFirst("X-HookPort-Event-Id"),
                    exchange.getRequestHeaders()
                            .getFirst("X-HookPort-Event-Type"),
                    exchange.getRequestHeaders()
                            .getFirst("X-HookPort-Timestamp"),
                    exchange.getRequestHeaders()
                            .getFirst("X-HookPort-Signature"),
                    body
            ));

            exchange.sendResponseHeaders(
                    receiverStatus.get(),
                    -1
            );
            exchange.close();
        });

        receiver.start();
    }

    @AfterEach
    void stopReceiver() {
        if (receiver != null) {
            receiver.stop(0);
        }
    }

    private Published publish(
            String endpointName,
            String idempotencyKey,
            String orderId
    ) {
        String targetUrl = "http://127.0.0.1:"
                + receiver.getAddress().getPort()
                + "/webhook";

        CreateEndpointResponse endpoint =
                endpointService.create(
                        new CreateEndpointRequest(
                                endpointName,
                                targetUrl,
                                null,
                                null
                        )
                );

        var payload = objectMapper.createObjectNode();
        payload.put("orderId", orderId);

        PublishEventResponse event =
                publishEventService.publish(
                        endpoint.id(),
                        idempotencyKey,
                        new PublishEventRequest(
                                "order.created",
                                payload
                        )
                );

        return new Published(endpoint, event);
    }

    private DeliveryAttempt onlyAttempt(
            PublishEventResponse published
    ) {
        var attempts = attemptRepository.findByDelivery_Id(
                published.deliveryId(),
                PageRequest.of(0, 10)
        );

        assertThat(attempts.getTotalElements()).isEqualTo(1);
        return attempts.getContent().getFirst();
    }

    @Test
    void publishesAndDeliversSignedWebhook() throws Exception {
        Published published = publish(
                "successful-endpoint",
                "success-order-123",
                "order-123"
        );

        assertThat(worker.runOnce()).isEqualTo(1);

        ReceivedWebhook webhook =
                received.poll(5, TimeUnit.SECONDS);

        assertThat(webhook).isNotNull();
        assertThat(webhook.eventId())
                .isEqualTo(
                        published.event().eventId().toString()
                );
        assertThat(webhook.eventType())
                .isEqualTo("order.created");

        assertThat(
                objectMapper.readTree(webhook.body())
                        .path("data")
                        .path("orderId")
                        .asText()
        ).isEqualTo("order-123");

        long timestamp =
                Long.parseLong(webhook.timestamp());

        assertThat(webhook.signature()).isEqualTo(
                signer.sign(
                        published.endpoint().signingSecret(),
                        timestamp,
                        webhook.body()
                )
        );

        var delivery = deliveryRepository
                .findById(published.event().deliveryId())
                .orElseThrow();

        assertThat(delivery.getStatus())
                .isEqualTo(DeliveryStatus.DELIVERED);
        assertThat(delivery.getAttemptCount())
                .isEqualTo(1);

        DeliveryAttempt attempt =
                onlyAttempt(published.event());

        assertThat(attempt.getOutcome())
                .isEqualTo(AttemptOutcome.DELIVERED);
        assertThat(attempt.getHttpStatus())
                .isEqualTo(204);
        assertThat(attempt.getCompletedAt())
                .isNotNull();

        // Outbox relay is disabled, so the committed row is
        // still waiting to be published to Kafka.
        assertThat(jdbcTemplate.queryForObject(
                """
                SELECT count(*)
                FROM event_outbox
                WHERE event_id = ?
                  AND published_at IS NULL
                """,
                Long.class,
                published.event().eventId()
        )).isEqualTo(1L);
    }

    @Test
    void schedulesRetryAfterServerError() {
        receiverStatus.set(503);

        Published published = publish(
                "retry-endpoint",
                "retry-order-123",
                "retry-123"
        );

        assertThat(worker.runOnce()).isEqualTo(1);

        var delivery = deliveryRepository
                .findById(published.event().deliveryId())
                .orElseThrow();

        assertThat(delivery.getStatus())
                .isEqualTo(DeliveryStatus.RETRY_SCHEDULED);
        assertThat(delivery.getAttemptCount())
                .isEqualTo(1);
        assertThat(delivery.getNextAttemptAt())
                .isNotNull();

        DeliveryAttempt attempt =
                onlyAttempt(published.event());

        assertThat(attempt.getOutcome())
                .isEqualTo(AttemptOutcome.RETRYABLE_FAILURE);
        assertThat(attempt.getHttpStatus())
                .isEqualTo(503);
    }

    @Test
    void stopsAfterPermanentClientError() {
        receiverStatus.set(400);

        Published published = publish(
                "failed-endpoint",
                "failed-order-123",
                "failed-123"
        );

        assertThat(worker.runOnce()).isEqualTo(1);

        var delivery = deliveryRepository
                .findById(published.event().deliveryId())
                .orElseThrow();

        assertThat(delivery.getStatus())
                .isEqualTo(DeliveryStatus.FAILED);
        assertThat(delivery.getAttemptCount())
                .isEqualTo(1);
        assertThat(delivery.getNextAttemptAt())
                .isNull();

        DeliveryAttempt attempt =
                onlyAttempt(published.event());

        assertThat(attempt.getOutcome())
                .isEqualTo(AttemptOutcome.PERMANENT_FAILURE);
        assertThat(attempt.getHttpStatus())
                .isEqualTo(400);
    }

    @Test
    void defersEmptyBucketAndDeliversForAnotherEndpoint()
            throws Exception {
        Published limited = publish(
                "limited-endpoint",
                "limited-order-123",
                "limited-123"
        );

        Published available = publish(
                "available-endpoint",
                "available-order-123",
                "available-123"
        );

        // Make the limited delivery the oldest due work.
        jdbcTemplate.update("""
        UPDATE webhook_deliveries
        SET next_attempt_at = now() - interval '1 minute'
        WHERE id = ?
        """, limited.event().deliveryId());

        // Set up a deterministically empty bucket. A future refill
        // timestamp prevents incidental refill during this test.
        jdbcTemplate.update("""
        UPDATE endpoint_rate_buckets
        SET tokens = 0,
            refilled_at = now() + interval '1 hour'
        WHERE endpoint_id = ?
        """, limited.endpoint().id());

        assertThat(worker.runOnce()).isEqualTo(1);

        ReceivedWebhook webhook =
                received.poll(5, TimeUnit.SECONDS);

        assertThat(webhook).isNotNull();
        assertThat(webhook.eventId()).isEqualTo(
                available.event().eventId().toString()
        );
        assertThat(received).isEmpty();

        var deferred = deliveryRepository
                .findById(limited.event().deliveryId())
                .orElseThrow();

        assertThat(deferred.getStatus())
                .isEqualTo(DeliveryStatus.PENDING);
        assertThat(deferred.getAttemptCount()).isZero();
        assertThat(deferred.getNextAttemptAt()).isNotNull();

        var deferredAttempts = attemptRepository.findByDelivery_Id(
                limited.event().deliveryId(),
                PageRequest.of(0, 10)
        );
        assertThat(deferredAttempts.getTotalElements()).isZero();

        var delivered = deliveryRepository
                .findById(available.event().deliveryId())
                .orElseThrow();

        assertThat(delivered.getStatus())
                .isEqualTo(DeliveryStatus.DELIVERED);
        assertThat(delivered.getAttemptCount()).isEqualTo(1);
    }
}