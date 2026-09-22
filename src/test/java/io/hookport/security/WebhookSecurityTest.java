package io.hookport.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

public class WebhookSecurityTest {

    private final TargetUrlValidator validator =
            new TargetUrlValidator(new WebhookSecurityProperties());

    @Test
    void shouldRejectLoopbackTarget() {
        assertThatThrownBy(() ->
                validator.validate("http://127.0.0.1/admin")
        ).isInstanceOf(UnsafeTargetUrlException.class);
    }

    @Test
    void shouldRejectPrivateTarget() {
        assertThatThrownBy(() ->
                validator.validate("http://10.0.0.5/webhook")
        ).isInstanceOf(UnsafeTargetUrlException.class);
    }

    @Test
    void shouldRejectCloudMetadataTarget() {
        assertThatThrownBy(() ->
                validator.validate(
                        "http://169.254.169.254/latest/meta-data"
                )
        ).isInstanceOf(UnsafeTargetUrlException.class);
    }

    @Test
    void shouldRejectNonHttpScheme() {
        assertThatThrownBy(() ->
                validator.validate("file:///etc/passwd")
        ).isInstanceOf(UnsafeTargetUrlException.class);
    }

    @Test
    void shouldRejectEmbeddedCredentials() {
        assertThatThrownBy(() ->
                validator.validate(
                        "https://username:password@example.com/webhook"
                )
        ).isInstanceOf(UnsafeTargetUrlException.class);
    }
}
