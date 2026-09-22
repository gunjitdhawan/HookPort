package io.hookport;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(properties = {
		"hookport.delivery.scheduling-enabled=false",
		"hookport.outbox.enabled=false"
})
@Testcontainers
class HookPortApplicationTests {

	@Container
	@ServiceConnection
	static final PostgreSQLContainer postgres =
			new PostgreSQLContainer("postgres:17-alpine");

	@Test
	void contextLoads() {
	}
}