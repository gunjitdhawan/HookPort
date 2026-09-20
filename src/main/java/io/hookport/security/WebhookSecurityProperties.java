package io.hookport.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "hookport.security")
public class WebhookSecurityProperties {

    private boolean allowPrivateTargets;

    public boolean isAllowPrivateTargets() {
        return allowPrivateTargets;
    }

    public void setAllowPrivateTargets(
            boolean allowPrivateTargets
    ) {
        this.allowPrivateTargets = allowPrivateTargets;
    }
}