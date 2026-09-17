package io.hookport.endpoint;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EndpointEtagTest {

    @Test
    void shouldCreateStrongEtagFromVersion() {
        assertThat(EndpointEtag.fromVersion(4))
                .isEqualTo("\"4\"");
    }

    @Test
    void shouldParseStrongEtag() {
        assertThat(EndpointEtag.parseRequired("\"4\""))
                .isEqualTo(4);
    }

    @Test
    void shouldRejectMissingEtag() {
        assertThatThrownBy(() -> EndpointEtag.parseRequired(null))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(exception -> {
                    ResponseStatusException responseException =
                            (ResponseStatusException) exception;

                    assertThat(responseException.getStatusCode())
                            .isEqualTo(HttpStatus.PRECONDITION_REQUIRED);
                });
    }

    @Test
    void shouldRejectWeakEtag() {
        assertThatThrownBy(
                () -> EndpointEtag.parseRequired("W/\"4\"")
        ).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void shouldRejectUnquotedVersion() {
        assertThatThrownBy(
                () -> EndpointEtag.parseRequired("4")
        ).isInstanceOf(ResponseStatusException.class);
    }
}