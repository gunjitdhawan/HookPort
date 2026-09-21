package io.hookport.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Service
public class EventOutboxRelay {

    private static final Logger log =
            LoggerFactory.getLogger(EventOutboxRelay.class);

    private final JdbcTemplate jdbcTemplate;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public EventOutboxRelay(
            JdbcTemplate jdbcTemplate,
            KafkaTemplate<String, String> kafkaTemplate
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Transactional
    public boolean publishOne() {
        List<PendingOutboxMessage> rows = jdbcTemplate.query(
                """
                SELECT event_id, topic, payload::text AS payload
                FROM event_outbox
                WHERE published_at IS NULL
                ORDER BY created_at, event_id
                FOR UPDATE SKIP LOCKED
                LIMIT 1
                """,
                (rs, rowNum) -> new PendingOutboxMessage(
                        rs.getObject("event_id", java.util.UUID.class),
                        rs.getString("topic"),
                        rs.getString("payload")
                )
        );

        if (rows.isEmpty()) {
            return false;
        }

        PendingOutboxMessage message = rows.getFirst();

        try {
            kafkaTemplate.send(
                    message.topic(),
                    message.eventId().toString(),
                    message.payload()
            ).get(10, TimeUnit.SECONDS);

        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                    "Interrupted while publishing outbox event "
                            + message.eventId(),
                    exception
            );

        } catch (ExecutionException | TimeoutException exception) {
            throw new IllegalStateException(
                    "Kafka did not acknowledge outbox event "
                            + message.eventId(),
                    exception
            );
        }

        int updated = jdbcTemplate.update(
                """
                UPDATE event_outbox
                SET published_at = now()
                WHERE event_id = ?
                  AND published_at IS NULL
                """,
                message.eventId()
        );

        if (updated != 1) {
            throw new IllegalStateException(
                    "Expected to mark one outbox row published: "
                            + message.eventId()
            );
        }

        log.info(
                "Published outbox event: eventId={}, topic={}",
                message.eventId(),
                message.topic()
        );

        return true;
    }
}