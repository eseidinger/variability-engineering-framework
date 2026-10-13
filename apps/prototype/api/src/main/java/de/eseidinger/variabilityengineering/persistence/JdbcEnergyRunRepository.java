package de.eseidinger.variabilityengineering.persistence;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** PostgreSQL is the source of truth for ownership, idempotency, work claims, and immutable results. */
@ApplicationScoped
public class JdbcEnergyRunRepository {
    @Inject DataSource dataSource;

    public boolean isReady() {
        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement("""
                     SELECT 1 FROM energy_optimization_run LIMIT 1
                     """)) {
            statement.executeQuery().close();
            return true;
        } catch (SQLException error) {
            return false;
        }
    }

    public record Run(String runId, String principalId, String requestJson, String datasetId,
                      String version, String manifestSha256, String status, Instant createdAt,
                      Instant startedAt, Instant completedAt, String resultJson, String errorJson,
                      UUID claimId) { }
    public record Claimed(Run run, UUID claimId) { }
    public record Page(List<String> items, int total) { }
    public record IdempotentRun(Run run, String requestHash) { }

    public Optional<IdempotentRun> findByIdempotency(String principalId, String key) {
        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement("""
                     SELECT * FROM energy_optimization_run
                     WHERE principal_id = ? AND idempotency_key = ?
                     """)) {
            statement.setString(1, principalId);
            statement.setString(2, key);
            try (var rows = statement.executeQuery()) {
                return rows.next() ? Optional.of(new IdempotentRun(read(rows),
                        rows.getString("request_sha256"))) : Optional.empty();
            }
        } catch (SQLException error) {
            throw new IllegalStateException("energy idempotency lookup failed", error);
        }
    }

    public Run reserve(String principalId, String key, String hash, String requestJson,
                       String datasetId, String version, String manifestSha256) {
        String runId = "run-" + UUID.randomUUID();
        try (var connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                try (var statement = connection.prepareStatement("""
                        INSERT INTO energy_optimization_run
                            (run_id, principal_id, idempotency_key, request_sha256, request_json,
                             dataset_id, publication_version, manifest_sha256)
                        VALUES (?, ?, ?, ?, ?::jsonb, ?, ?, ?) ON CONFLICT DO NOTHING
                        """)) {
                    statement.setString(1, runId);
                    statement.setString(2, principalId);
                    statement.setString(3, key);
                    statement.setString(4, hash);
                    statement.setString(5, requestJson);
                    statement.setString(6, datasetId);
                    statement.setString(7, version);
                    statement.setString(8, manifestSha256);
                    statement.executeUpdate();
                }
                Run run;
                try (var statement = connection.prepareStatement("""
                        SELECT *, request_sha256 FROM energy_optimization_run
                        WHERE principal_id = ? AND idempotency_key = ?
                        """)) {
                    statement.setString(1, principalId);
                    statement.setString(2, key);
                    try (var rows = statement.executeQuery()) {
                        if (!rows.next()) throw new IllegalStateException("idempotency reservation was not found");
                        if (!hash.equals(rows.getString("request_sha256"))) {
                            throw new IdempotencyConflict();
                        }
                        run = read(rows);
                    }
                }
                connection.commit();
                return run;
            } catch (RuntimeException | SQLException error) {
                connection.rollback();
                throw error;
            }
        } catch (SQLException error) {
            throw new IllegalStateException("energy run reservation failed", error);
        }
    }

    public Optional<Run> findOwned(String runId, String principalId) {
        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement("""
                     SELECT * FROM energy_optimization_run WHERE run_id = ? AND principal_id = ?
                     """)) {
            statement.setString(1, runId);
            statement.setString(2, principalId);
            try (var rows = statement.executeQuery()) {
                return rows.next() ? Optional.of(read(rows)) : Optional.empty();
            }
        } catch (SQLException error) {
            throw new IllegalStateException("energy run lookup failed", error);
        }
    }

    public Optional<Claimed> claimNext() {
        try (var connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                String runId;
                try (var statement = connection.prepareStatement("""
                        SELECT run_id FROM energy_optimization_run WHERE status = 'QUEUED'
                        ORDER BY created_at, run_id FOR UPDATE SKIP LOCKED LIMIT 1
                        """)) {
                    try (var rows = statement.executeQuery()) {
                        if (!rows.next()) {
                            connection.commit();
                            return Optional.empty();
                        }
                        runId = rows.getString(1);
                    }
                }
                UUID claimId = UUID.randomUUID();
                try (var statement = connection.prepareStatement("""
                        UPDATE energy_optimization_run SET status = 'RUNNING', started_at = CURRENT_TIMESTAMP,
                            claim_id = ? WHERE run_id = ? RETURNING *
                        """)) {
                    statement.setObject(1, claimId);
                    statement.setString(2, runId);
                    try (var rows = statement.executeQuery()) {
                        rows.next();
                        var claimed = new Claimed(read(rows), claimId);
                        connection.commit();
                        return Optional.of(claimed);
                    }
                }
            } catch (RuntimeException | SQLException error) {
                connection.rollback();
                throw error;
            }
        } catch (SQLException error) {
            throw new IllegalStateException("energy queue claim failed", error);
        }
    }

    public void complete(Claimed claimed, String status, String resultJson, String errorJson,
                         List<String> candidateJson) {
        try (var connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                int updated;
                try (var statement = connection.prepareStatement("""
                        UPDATE energy_optimization_run SET status = ?, completed_at = CURRENT_TIMESTAMP,
                            result_json = ?::jsonb, error_json = ?::jsonb, claim_id = NULL
                        WHERE run_id = ? AND claim_id = ? AND status = 'RUNNING'
                        """)) {
                    statement.setString(1, status);
                    statement.setString(2, resultJson);
                    statement.setString(3, errorJson);
                    statement.setString(4, claimed.run().runId());
                    statement.setObject(5, claimed.claimId());
                    updated = statement.executeUpdate();
                }
                if (updated != 1) {
                    connection.rollback();
                    return;
                }
                try (var statement = connection.prepareStatement("""
                        INSERT INTO energy_optimization_candidate (run_id, position, candidate_json)
                        VALUES (?, ?, ?::jsonb)
                        """)) {
                    for (int i = 0; i < candidateJson.size(); i++) {
                        statement.setString(1, claimed.run().runId());
                        statement.setInt(2, i);
                        statement.setString(3, candidateJson.get(i));
                        statement.addBatch();
                    }
                    statement.executeBatch();
                }
                connection.commit();
            } catch (RuntimeException | SQLException error) {
                connection.rollback();
                throw error;
            }
        } catch (SQLException error) {
            throw new IllegalStateException("energy run completion failed", error);
        }
    }

    public Page candidates(String runId, int page, int pageSize) {
        try (var connection = dataSource.getConnection()) {
            int total;
            try (var statement = connection.prepareStatement("""
                    SELECT count(*) FROM energy_optimization_candidate WHERE run_id = ?
                    """)) {
                statement.setString(1, runId);
                try (var rows = statement.executeQuery()) {
                    rows.next();
                    total = rows.getInt(1);
                }
            }
            var items = new ArrayList<String>();
            try (var statement = connection.prepareStatement("""
                    SELECT candidate_json::text FROM energy_optimization_candidate
                    WHERE run_id = ? ORDER BY position LIMIT ? OFFSET ?
                    """)) {
                statement.setString(1, runId);
                statement.setInt(2, pageSize);
                statement.setInt(3, Math.toIntExact(Math.min((long) page * pageSize, Integer.MAX_VALUE)));
                try (var rows = statement.executeQuery()) {
                    while (rows.next()) items.add(rows.getString(1));
                }
            }
            return new Page(List.copyOf(items), total);
        } catch (SQLException error) {
            throw new IllegalStateException("energy candidates lookup failed", error);
        }
    }

    public void requeueInterrupted() {
        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement("""
                     UPDATE energy_optimization_run SET status = 'QUEUED', started_at = NULL,
                         claim_id = NULL WHERE status = 'RUNNING'
                     """)) {
            statement.executeUpdate();
        } catch (SQLException error) {
            throw new IllegalStateException("energy queue recovery failed", error);
        }
    }

    private static Run read(java.sql.ResultSet rows) throws SQLException {
        var started = rows.getTimestamp("started_at");
        var completed = rows.getTimestamp("completed_at");
        return new Run(rows.getString("run_id"), rows.getString("principal_id").trim(),
                rows.getString("request_json"), rows.getString("dataset_id"),
                rows.getString("publication_version"), rows.getString("manifest_sha256"),
                rows.getString("status"), rows.getTimestamp("created_at").toInstant(),
                started == null ? null : started.toInstant(), completed == null ? null : completed.toInstant(),
                rows.getString("result_json"), rows.getString("error_json"),
                (UUID) rows.getObject("claim_id"));
    }

    public static final class IdempotencyConflict extends RuntimeException { }
}
