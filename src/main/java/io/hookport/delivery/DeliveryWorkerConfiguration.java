package io.hookport.delivery;

import io.hookport.security.WebhookSecurityProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@EnableConfigurationProperties({DeliveryProperties.class, WebhookSecurityProperties.class})
public class DeliveryWorkerConfiguration {
}