CREATE INDEX energy_run_retention ON energy_optimization_run (completed_at, run_id)
    WHERE status IN ('SUCCEEDED', 'INFEASIBLE', 'FAILED');

-- No payloads, prompts, credentials, or FK that would extend a run's lifetime.
CREATE TABLE energy_security_audit (
    event_id UUID PRIMARY KEY,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    principal_id CHAR(36) NOT NULL,
    run_id VARCHAR(128) NOT NULL,
    action VARCHAR(32) NOT NULL,
    outcome VARCHAR(16) NOT NULL,
    CHECK (action IN ('READ_RUN', 'READ_CANDIDATES', 'EXPLAIN_RUN')),
    CHECK (outcome IN ('ALLOWED', 'DENIED'))
);
CREATE INDEX energy_audit_retention ON energy_security_audit (occurred_at);
