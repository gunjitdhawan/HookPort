package io.hookport.outbox;

import io.hookport.endpoint.CreateEndpointRequest;
import io.hookport.endpoint.WebhookEndpointService;
import io.hookport.event.PublishEventRequest;
import io.hookport.event.PublishEventService;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "hookport.delivery.scheduling-enabled=false",
        "hookport.outbox.enabled=false",
        "hookport.security.allow-private-targets=true"
})
@Testcontainers
class EventOutboxRelayIntegrationTest {

    private static final String TOPIC =
            "hookport.events.accepted";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:17-alpine");

    @Container
    @ServiceConnection
    static final KafkaContainer kafka =
            new KafkaContainer("apache/kafka:4.1.1");

    @Autowired
    WebhookEndpointService endpointService;

    @Autowired
    PublishEventService publishEventService;

    @Autowired
    EventOutboxRelay relay;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void publishesAcceptedEventAndMarksOutboxRow()
            throws Exception {
        var endpoint = endpointService.create(
                new CreateEndpointRequest(
                        "outbox-test",
                        "http://127.0.0.1:9999/webhook",
                        null,
                        null
                )
        );

        var payload = objectMapper.createObjectNode();
        payload.put("orderId", "order-123");

        var published = publishEventService.publish(
                endpoint.id(),
                "outbox-order-123",
                new PublishEventRequest(
                        "order.created",
                        payload
                )
        );

        assertThat(jdbcTemplate.queryForObject(
                """
                SELECT published_at IS NULL
                FROM event_outbox
                WHERE event_id = ?
                """,
                Boolean.class,
                published.eventId()
        )).isTrue();

        assertThat(relay.publishOne()).isTrue();

        ConsumerRecord<String, String> message =
                readAcceptedEvent();

        assertThat(message).isNotNull();
        assertThat(message.key())
                .isEqualTo(published.eventId().toString());

        var body = objectMapper.readTree(message.value());
        assertThat(body.path("eventId").asText())
                .isEqualTo(published.eventId().toString());
        assertThat(body.path("endpointId").asText())
                .isEqualTo(endpoint.id().toString());
        assertThat(body.path("deliveryId").asText())
                .isEqualTo(published.deliveryId().toString());
        assertThat(body.path("eventType").asText())
                .isEqualTo("order.created");

        assertThat(jdbcTemplate.queryForObject(
                """
                SELECT published_at IS NOT NULL
                FROM event_outbox
                WHERE event_id = ?
                """,
                Boolean.class,
                published.eventId()
        )).isTrue();

        assertThat(relay.publishOne()).isFalse();
    }

    private ConsumerRecord<String, String> readAcceptedEvent() {
        Map<String, Object> config = new HashMap<>();
        config.put(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
                kafka.getBootstrapServers()
        );
        config.put(
                ConsumerConfig.GROUP_ID_CONFIG,
                "outbox-test-" + UUID.randomUUID()
        );
        config.put(
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,
                "earliest"
        );
        config.put(
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
                StringDeserializer.class
        );
        config.put(
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                StringDeserializer.class
        );

        try (KafkaConsumer<String, String> consumer =
                     new KafkaConsumer<>(config)) {
            consumer.subscribe(List.of(TOPIC));

            long deadline =
                    System.nanoTime()
                            + Duration.ofSeconds(15).toNanos();

            while (System.nanoTime() < deadline) {
                for (ConsumerRecord<String, String> record
                        : consumer.poll(Duration.ofMillis(500))) {
                    if (TOPIC.equals(record.topic())) {
                        return record;
                    }
                }
            }
        }

        return null;
    }
}