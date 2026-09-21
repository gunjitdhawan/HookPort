package io.hookport.outbox;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;

@Repository
public class EventOutboxWriter {

    private static final String TOPIC = "hookport.events.accepted";

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public EventOutboxWriter(
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public void write(EventAcceptedMessage message) {
        String json = objectMapper.writeValueAsString(message);

        jdbcTemplate.update(
                """
                INSERT INTO event_outbox (
                    event_id,
                    topic,
                    payload
                )
                VALUES (?, ?, CAST(? AS jsonb))
                """,
                message.eventId(),
                TOPIC,
                json
        );
    }
}