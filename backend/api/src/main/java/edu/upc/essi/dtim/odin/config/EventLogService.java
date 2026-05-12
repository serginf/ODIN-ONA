package edu.upc.essi.dtim.odin.config;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class EventLogService {

    private final JdbcTemplate jdbc;

    public EventLogService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void append(String tenantId, String actor, String eventType, String payloadJson) {
        jdbc.update(
            "INSERT INTO events (tenant_id, actor, event_type, payload_json, created_at) VALUES (?, ?, ?, ?::jsonb, NOW())",
            tenantId, actor, eventType, payloadJson
        );
    }
}
