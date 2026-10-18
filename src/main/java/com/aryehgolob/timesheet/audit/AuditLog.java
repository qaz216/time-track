package com.aryehgolob.timesheet.audit;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.aryehgolob.timesheet.identity.Actor;

import tools.jackson.databind.json.JsonMapper;

/**
 * Append-only record of every change. MANDATORY propagation means it can only
 * run inside the caller's transaction: the change and its audit row commit
 * together or not at all. Nothing ever updates or deletes from audit_event.
 */
@Component
public class AuditLog {

    private final JdbcTemplate jdbc;
    private final JsonMapper json;

    AuditLog(JdbcTemplate jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(Actor actor, String entityType, Object entityId, String action, Object before, Object after) {
        jdbc.update("""
                insert into audit_event (actor_id, entity_type, entity_id, action, before, after)
                values (?, ?, ?, ?, ?::jsonb, ?::jsonb)
                """,
                actor.userId(), entityType, String.valueOf(entityId), action, toJson(before), toJson(after));
    }

    private String toJson(Object snapshot) {
        return snapshot == null ? null : json.writeValueAsString(snapshot);
    }
}
