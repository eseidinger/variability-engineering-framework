CREATE TABLE energy_optimization_run (
    run_id VARCHAR(128) PRIMARY KEY,
    principal_id CHAR(36) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    request_sha256 CHAR(64) NOT NULL,
    request_json JSONB NOT NULL,
    dataset_id VARCHAR(100) NOT NULL,
    publication_version VARCHAR(64) NOT NULL,
    manifest_sha256 CHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'QUEUED',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    result_json JSONB,
    error_json JSONB,
    claim_id UUID,
    UNIQUE (principal_id, idempotency_key),
    FOREIGN KEY (dataset_id, publication_version)
        REFERENCES verified_energy_publication (dataset_id, publication_version),
    CHECK (status IN ('QUEUED', 'RUNNING', 'SUCCEEDED', 'INFEASIBLE', 'FAILED')),
    CHECK (octet_length(request_json::text) <= 65536),
    CHECK (result_json IS NULL OR octet_length(result_json::text) <= 65536)
);

CREATE INDEX energy_run_queue ON energy_optimization_run (created_at) WHERE status = 'QUEUED';

CREATE TABLE energy_optimization_candidate (
    run_id VARCHAR(128) NOT NULL REFERENCES energy_optimization_run (run_id) ON DELETE CASCADE,
    position INTEGER NOT NULL,
    candidate_json JSONB NOT NULL,
    PRIMARY KEY (run_id, position),
    CHECK (position >= 0),
    CHECK (octet_length(candidate_json::text) <= 16384)
);
