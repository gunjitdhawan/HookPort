package io.hookport.delivery;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class WebhookSignerTest {

    private final WebhookSigner signer =
            new WebhookSigner();

    @Test
    void shouldProduceDeterministicSignature() {
        String secret = encodedSecret(
                "test-signing-secret"
        );

        byte[] body = """
                {"event":"payment.completed"}
                """.strip().getBytes(StandardCharsets.UTF_8);

        String first = signer.sign(
                secret,
                1_789_794_000L,
                body
        );

        String second = signer.sign(
                secret,
                1_789_794_000L,
                body
        );

        assertThat(first).isEqualTo(second);
        assertThat(first).matches("v1=[0-9a-f]{64}");
    }

    @Test
    void shouldChangeSignatureWhenBodyChanges() {
        String secret = encodedSecret(
                "test-signing-secret"
        );

        long timestamp = 1_789_794_000L;

        String first = signer.sign(
                secret,
                timestamp,
                "{\"amount\":4999}"
                        .getBytes(StandardCharsets.UTF_8)
        );

        String second = signer.sign(
                secret,
                timestamp,
                "{\"amount\":5000}"
                        .getBytes(StandardCharsets.UTF_8)
        );

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void shouldChangeSignatureWhenTimestampChanges() {
        String secret = encodedSecret(
                "test-signing-secret"
        );

        byte[] body = "{\"amount\":4999}"
                .getBytes(StandardCharsets.UTF_8);

        String first = signer.sign(
                secret,
                1_789_794_000L,
                body
        );

        String second = signer.sign(
                secret,
                1_789_794_001L,
                body
        );

        assertThat(first).isNotEqualTo(second);
    }

    private String encodedSecret(String value) {
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(
                        value.getBytes(StandardCharsets.UTF_8)
                );
    }
}