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

@SpringBootTest
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

    @BeforeEach
    void cleanDatabase() {
        repository.deleteAll();
    }

    @Test
    void shouldCreateAndPersistWebhookEndpoint() {
        CreateEndpointRequest request = new CreateEndpointRequest(
                "payment-events",
                "https://example.com/webhooks/payments"
        );

        CreateEndpointResponse created = service.create(request);

        assertThat(created.id()).isNotNull();
        assertThat(created.name()).isEqualTo("payment-events");
        assertThat(created.status()).isEqualTo(EndpointStatus.ACTIVE);
        assertThat(created.version()).isZero();
        assertThat(created.signingSecret()).isNotBlank();

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
                "https://example.com/first"
        );

        service.create(request);

        assertThatThrownBy(() -> service.create(
                new CreateEndpointRequest(
                        "payment-events",
                        "https://example.com/second"
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
                        "https://example.com/original"
                )
        );

        EndpointResponse updated = service.update(
                created.id(),
                new UpdateEndpointRequest(
                        null,
                        "https://example.com/updated",
                        EndpointStatus.DISABLED
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
                        "https://example.com/original"
                )
        );

        EndpointResponse firstUpdate = service.update(
                created.id(),
                new UpdateEndpointRequest(
                        null,
                        null,
                        EndpointStatus.DISABLED
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
                        EndpointStatus.ACTIVE
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
    void shouldNeverExposeSigningSecretInReadResponse() {
        CreateEndpointResponse created = service.create(
                new CreateEndpointRequest(
                        "payment-events",
                        "https://example.com/webhooks"
                )
        );

        EndpointResponse response = service.getById(created.id());

        assertThat(response.id()).isEqualTo(created.id());

        assertThat(EndpointResponse.class.getRecordComponents())
                .extracting(component -> component.getName())
                .doesNotContain("signingSecret");
    }
}