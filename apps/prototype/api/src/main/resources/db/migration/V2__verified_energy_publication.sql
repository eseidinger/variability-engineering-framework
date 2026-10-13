CREATE TABLE verified_energy_publication (
    dataset_id VARCHAR(100) NOT NULL,
    publication_version VARCHAR(64) NOT NULL,
    manifest_sha256 CHAR(64) NOT NULL,
    manifest_bytes BYTEA NOT NULL,
    manifest_etag VARCHAR(512),
    verified_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (dataset_id, publication_version),
    CHECK (octet_length(manifest_bytes) <= 1048576)
);

CREATE TABLE verified_energy_artifact (
    dataset_id VARCHAR(100) NOT NULL,
    publication_version VARCHAR(64) NOT NULL,
    artifact_id VARCHAR(100) NOT NULL,
    artifact_bytes BYTEA NOT NULL,
    artifact_etag VARCHAR(512),
    PRIMARY KEY (dataset_id, publication_version, artifact_id),
    FOREIGN KEY (dataset_id, publication_version)
        REFERENCES verified_energy_publication (dataset_id, publication_version)
        ON DELETE CASCADE,
    CHECK (octet_length(artifact_bytes) <= 10485760)
);
