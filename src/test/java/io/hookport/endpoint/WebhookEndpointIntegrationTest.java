package io.hookport.endpoint;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "hookport.delivery.scheduling-enabled=false",
        "hookport.outbox.enabled=false"
})
@Testcontainers
class WebhookEndpointIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:17-alpine");

    @Autowired
    private WebhookEndpointService service;

    @Autowired
    private WebhookEndpointRepository repository;

    @Autowired
    private EndpointRateBucketRepository bucketRepository;

    @BeforeEach
    void cleanDatabase() {
        repository.deleteAll();
    }

    @Test
    void shouldCreateAndPersistWebhookEndpoint() {
        CreateEndpointRequest request = new CreateEndpointRequest(
                "payment-events",
                "https://example.com/webhooks/payments",
                null,
                null
        );

        CreateEndpointResponse created = service.create(request);

        assertThat(created.id()).isNotNull();
        assertThat(created.name()).isEqualTo("payment-events");
        assertThat(created.status()).isEqualTo(EndpointStatus.ACTIVE);
        assertThat(created.version()).isZero();
        assertThat(created.signingSecret()).isNotBlank();
        assertThat(created.bucketCapacity()).isEqualTo(5);
        assertThat(created.refillPerSecond()).isEqualTo(5);
        EndpointRateBucket bucket = bucketRepository.findById(created.id()).orElseThrow();
        assertThat(bucket.getCapacity()).isEqualTo(5);
        assertThat(bucket.getRefillPerSecond()).isEqualTo(5);

        WebhookEndpoint persisted = repository
                .findById(created.id())
                .orElseThrow();

        assertThat(persisted.getTargetUrl())
                .isEqualTo("https://example.com/webhooks/payments");

        assertThat(persisted.getStatus())
                .isEqualTo(EndpointStatus.ACTIVE);
    }

    @Test
    void shouldRejectDuplicateEndpointName() {
        CreateEndpointRequest request = new CreateEndpointRequest(
                "payment-events",
                "https://example.com/first",
                null,
                null
        );

        service.create(request);

        assertThatThrownBy(() -> service.create(
                new CreateEndpointRequest(
                        "payment-events",
                        "https://example.com/second",
                        null,
                        null
                )
        ))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(exception -> {
                    ResponseStatusException responseException =
                            (ResponseStatusException) exception;

                    assertThat(responseException.getStatusCode())
                            .isEqualTo(HttpStatus.CONFLICT);
                });

        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void shouldUpdateEndpointAndIncrementVersion() {
        CreateEndpointResponse created = service.create(
                new CreateEndpointRequest(
                        "payment-events",
                        "https://example.com/original",
                        null,
                        null
                )
        );

        EndpointResponse updated = service.update(
                created.id(),
                new UpdateEndpointRequest(
                        null,
                        "https://example.com/updated",
                        EndpointStatus.DISABLED,
                        null,
                        null
                ),
                created.version()
        );

        assertThat(updated.targetUrl())
                .isEqualTo("https://example.com/updated");

        assertThat(updated.status())
                .isEqualTo(EndpointStatus.DISABLED);

        assertThat(updated.version())
                .isEqualTo(created.version() + 1);
    }

    @Test
    void shouldRejectUpdateUsingStaleVersion() {
        CreateEndpointResponse created = service.create(
                new CreateEndpointRequest(
                        "payment-events",
                        "https://example.com/original",
                        null,
                        null
                )
        );

        EndpointResponse firstUpdate = service.update(
                created.id(),
                new UpdateEndpointRequest(
                        null,
                        null,
                        EndpointStatus.DISABLED,
                        null,
                        null
                ),
                created.version()
        );

        assertThat(firstUpdate.version())
                .isEqualTo(created.version() + 1);

        assertThatThrownBy(() -> service.update(
                created.id(),
                new UpdateEndpointRequest(
                        null,
                        null,
                        EndpointStatus.ACTIVE,
                        null,
                        null
                ),
                created.version()
        ))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(exception -> {
                    ResponseStatusException responseException =
                            (ResponseStatusException) exception;

                    assertThat(responseException.getStatusCode())
                            .isEqualTo(HttpStatus.PRECONDITION_FAILED);
                });
    }

    @Test
    void shouldUpdateRateSettingsAndVersion() {
        CreateEndpointResponse created = service.create(
                new CreateEndpointRequest(
                        "rate-events",
                        "https://example.com/webhooks",
                        2,
                        1
                )
        );

        EndpointResponse updated = service.update(
                created.id(),
                new UpdateEndpointRequest(null, null, null, 3, 2),
                created.version()
        );

        assertThat(updated.bucketCapacity()).isEqualTo(3);
        assertThat(updated.refillPerSecond()).isEqualTo(2);
        assertThat(updated.version()).isEqualTo(created.version() + 1);
        assertThat(service.getById(created.id()).bucketCapacity())
                .isEqualTo(3);
        EndpointRateBucket bucket = bucketRepository
                .findById(created.id()).orElseThrow();
        assertThat(bucket.getCapacity()).isEqualTo(3);
        assertThat(bucket.getRefillPerSecond()).isEqualTo(2);
    }

    @Test
    void shouldNeverExposeSigningSecretInReadResponse() {
        CreateEndpointResponse created = service.create(
                new CreateEndpointRequest(
                        "payment-events",
                        "https://example.com/webhooks",
                        null,
                        null
                )
        );

        EndpointResponse response = service.getById(created.id());

        assertThat(response.id()).isEqualTo(created.id());

        assertThat(EndpointResponse.class.getRecordComponents())
                .extracting(component -> component.getName())
                .doesNotContain("signingSecret");
    }
}
