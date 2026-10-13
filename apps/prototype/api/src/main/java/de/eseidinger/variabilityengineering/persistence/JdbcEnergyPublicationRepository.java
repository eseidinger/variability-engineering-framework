package de.eseidinger.variabilityengineering.persistence;

import de.eseidinger.variabilityengineering.adapter.odip.EnergyPublicationClient;
import de.eseidinger.variabilityengineering.adapter.odip.EnergyPublicationVerifier;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Atomic immutable byte cache. Loaded rows are independently reverified. */
@ApplicationScoped
public class JdbcEnergyPublicationRepository {
    @Inject
    DataSource dataSource;

    private final EnergyPublicationVerifier verifier = new EnergyPublicationVerifier();

    public EnergyPublicationClient.Cached saveIfAbsent(EnergyPublicationClient.Cached candidate) {
        Objects.requireNonNull(candidate);
        String id = candidate.publication().profile().datasetId();
        String version = candidate.publication().profile().version();
        try (var connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                int inserted;
                try (var statement = connection.prepareStatement("""
                        INSERT INTO verified_energy_publication
                            (dataset_id, publication_version, manifest_sha256, manifest_bytes, manifest_etag)
                        VALUES (?, ?, ?, ?, ?) ON CONFLICT DO NOTHING
                        """)) {
                    statement.setString(1, id);
                    statement.setString(2, version);
                    statement.setString(3, candidate.publication().manifestSha256());
                    statement.setBytes(4, candidate.publication().manifestBytes());
                    statement.setString(5, candidate.manifestEtag());
                    inserted = statement.executeUpdate();
                }
                if (inserted == 1) {
                    var bytes = candidate.publication().artifactBytes();
                    try (var statement = connection.prepareStatement("""
                            INSERT INTO verified_energy_artifact
                                (dataset_id, publication_version, artifact_id, artifact_bytes, artifact_etag)
                            VALUES (?, ?, ?, ?, ?)
                            """)) {
                        for (var entry : bytes.entrySet()) {
                            statement.setString(1, id);
                            statement.setString(2, version);
                            statement.setString(3, entry.getKey());
                            statement.setBytes(4, entry.getValue());
                            statement.setString(5, candidate.artifactEtags().get(entry.getKey()));
                            statement.addBatch();
                        }
                        statement.executeBatch();
                    }
                }
                var stored = load(connection, id, version).orElseThrow();
                if (!stored.publication().manifestSha256().equals(candidate.publication().manifestSha256())
                        || !sameArtifacts(stored.publication().artifactBytes(), candidate.publication().artifactBytes())) {
                    throw new IllegalStateException("immutable publication version has different bytes");
                }
                connection.commit();
                return stored;
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("energy publication persistence failed", e);
        }
    }

    public Optional<EnergyPublicationClient.Cached> find(String datasetId, String version) {
        try (var connection = dataSource.getConnection()) {
            return load(connection, datasetId, version);
        } catch (SQLException e) {
            throw new IllegalStateException("energy publication retrieval failed", e);
        }
    }

    private Optional<EnergyPublicationClient.Cached> load(Connection connection, String datasetId, String version)
            throws SQLException {
        byte[] manifest;
        String manifestEtag;
        String storedDigest;
        try (var statement = connection.prepareStatement("""
                SELECT manifest_bytes, manifest_etag, manifest_sha256
                FROM verified_energy_publication WHERE dataset_id = ? AND publication_version = ?
                """)) {
            statement.setString(1, datasetId);
            statement.setString(2, version);
            try (var rows = statement.executeQuery()) {
                if (!rows.next()) return Optional.empty();
                manifest = rows.getBytes("manifest_bytes");
                manifestEtag = rows.getString("manifest_etag");
                storedDigest = rows.getString("manifest_sha256");
            }
        }
        var bytes = new HashMap<String, byte[]>();
        var etags = new HashMap<String, String>();
        try (var statement = connection.prepareStatement("""
                SELECT artifact_id, artifact_bytes, artifact_etag
                FROM verified_energy_artifact WHERE dataset_id = ? AND publication_version = ?
                """)) {
            statement.setString(1, datasetId);
            statement.setString(2, version);
            try (var rows = statement.executeQuery()) {
                while (rows.next()) {
                    String id = rows.getString("artifact_id");
                    bytes.put(id, rows.getBytes("artifact_bytes"));
                    String etag = rows.getString("artifact_etag");
                    if (etag != null) etags.put(id, etag);
                }
            }
        }
        var publication = verifier.verify(manifest, bytes);
        if (!storedDigest.equals(publication.manifestSha256())
                || !datasetId.equals(publication.profile().datasetId())
                || !version.equals(publication.profile().version())) {
            throw new IllegalStateException("cached publication identity or digest changed");
        }
        return Optional.of(new EnergyPublicationClient.Cached(publication, manifestEtag, etags));
    }

    private static boolean sameArtifacts(Map<String, byte[]> left, Map<String, byte[]> right) {
        if (!left.keySet().equals(right.keySet())) return false;
        for (var id : left.keySet()) {
            if (!java.util.Arrays.equals(left.get(id), right.get(id))) return false;
        }
        return true;
    }
}
