package de.eseidinger.variabilityengineering.persistence;

import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/** Application-owned 180-day retention, independent of platform backup expiry. */
@ApplicationScoped
public class EnergyRetention {
    private static final Logger LOG = Logger.getLogger(EnergyRetention.class);
    @Inject DataSource dataSource;
    @ConfigProperty(name = "energy.retention.enabled", defaultValue = "true") boolean enabled;

    public record Purged(int runs, int auditEvents) { }

    @Scheduled(every = "1h", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void clean() {
        if (!enabled) return;
        try {
            Purged result = purgeExpired(Instant.now());
            if (result.runs() > 0 || result.auditEvents() > 0) {
                LOG.infof("event=energy_retention_completed runs=%d audit_events=%d", result.runs(), result.auditEvents());
            }
        } catch (RuntimeException exception) {
            LOG.errorf("event=energy_retention_failed error_type=%s", exception.getClass().getName());
        }
    }

    public Purged purgeExpired(Instant now) {
        Timestamp cutoff = Timestamp.from(now.minus(180, ChronoUnit.DAYS));
        try (var connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                int runs;
                try (var statement = connection.prepareStatement("""
                        WITH expired AS (
                            SELECT run_id FROM energy_optimization_run
                            WHERE status IN ('SUCCEEDED', 'INFEASIBLE', 'FAILED') AND completed_at <= ?
                            ORDER BY completed_at, run_id FOR UPDATE SKIP LOCKED LIMIT 500
                        ) DELETE FROM energy_optimization_run WHERE run_id IN (SELECT run_id FROM expired)
                        """)) {
                    statement.setTimestamp(1, cutoff);
                    runs = statement.executeUpdate();
                }
                int events;
                try (var statement = connection.prepareStatement("""
                        WITH expired AS (
                            SELECT event_id FROM energy_security_audit WHERE occurred_at <= ?
                            ORDER BY occurred_at, event_id FOR UPDATE SKIP LOCKED LIMIT 5000
                        ) DELETE FROM energy_security_audit WHERE event_id IN (SELECT event_id FROM expired)
                        """)) {
                    statement.setTimestamp(1, cutoff);
                    events = statement.executeUpdate();
                }
                connection.commit();
                return new Purged(runs, events);
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("energy retention failed", exception);
        }
    }

    public void audit(String principal, String runId, String action, boolean allowed) {
        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement("""
                     INSERT INTO energy_security_audit (event_id, principal_id, run_id, action, outcome)
                     VALUES (?, ?, ?, ?, ?)
                     """)) {
            statement.setObject(1, UUID.randomUUID());
            statement.setString(2, principal);
            statement.setString(3, runId);
            statement.setString(4, action);
            statement.setString(5, allowed ? "ALLOWED" : "DENIED");
            statement.executeUpdate();
        } catch (SQLException exception) {
            LOG.errorf("event=energy_audit_failed error_type=%s", exception.getClass().getName());
            throw new IllegalStateException("energy security audit unavailable", exception);
        }
    }
}
