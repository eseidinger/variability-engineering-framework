package de.eseidinger.variabilityengineering.persistence;

import de.eseidinger.variabilityengineering.adapter.foodservice.FoodServiceFixtureAdapter;
import de.eseidinger.variabilityengineering.core.AnalysisPopulation;
import de.eseidinger.variabilityengineering.core.DimensionDefinition;
import de.eseidinger.variabilityengineering.core.DimensionValue;
import de.eseidinger.variabilityengineering.core.Variant;
import de.eseidinger.variabilityengineering.core.VariantRecord;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/** PostgreSQL repository for immutable, versioned analysis populations. */
@ApplicationScoped
public class JdbcPopulationRepository {

    public record PersistedPopulation(
            AnalysisPopulation population,
            List<FoodServiceFixtureAdapter.RejectedRecord> rejectedRecords) {
        public PersistedPopulation {
            rejectedRecords = List.copyOf(rejectedRecords);
        }
    }

    @Inject
    DataSource dataSource;

    public PersistedPopulation saveIfAbsent(
            AnalysisPopulation population,
            List<FoodServiceFixtureAdapter.RejectedRecord> rejectedRecords) {
        try (var connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                var existing = load(connection, population.id(), population.version());
                if (existing.isPresent()) {
                    connection.commit();
                    return existing.get();
                }
                insert(connection, population, rejectedRecords);
                connection.commit();
                return new PersistedPopulation(population, rejectedRecords);
            } catch (RuntimeException | SQLException exception) {
                rollback(connection, exception);
                throw persistenceFailure(exception);
            }
        } catch (SQLException exception) {
            throw persistenceFailure(exception);
        }
    }

    public Optional<PersistedPopulation> find(String populationId, String populationVersion) {
        try (var connection = dataSource.getConnection()) {
            return load(connection, populationId, populationVersion);
        } catch (SQLException exception) {
            throw persistenceFailure(exception);
        }
    }

    public boolean contains(String populationId, String populationVersion) {
        try (var connection = dataSource.getConnection()) {
            return exists(connection, populationId, populationVersion);
        } catch (SQLException exception) {
            throw persistenceFailure(exception);
        }
    }

    private static Optional<PersistedPopulation> load(Connection connection, String populationId, String populationVersion)
            throws SQLException {
        if (!exists(connection, populationId, populationVersion)) {
            return Optional.empty();
        }
        var provenance = artifacts(connection, populationId, populationVersion);
        var dimensions = definitions(connection, populationId, populationVersion);
        var records = records(connection, populationId, populationVersion, dimensions.keySet());
        var population = new AnalysisPopulation(populationId, populationVersion, provenance, dimensions, records);
        return Optional.of(new PersistedPopulation(population, rejectedRecords(connection, populationId, populationVersion)));
    }

    private static boolean exists(Connection connection, String populationId, String populationVersion) throws SQLException {
        try (var statement = connection.prepareStatement("""
                SELECT 1 FROM analysis_population WHERE population_id = ? AND population_version = ?
                """)) {
            statement.setString(1, populationId);
            statement.setString(2, populationVersion);
            try (var result = statement.executeQuery()) {
                return result.next();
            }
        }
    }

    private static Map<String, AnalysisPopulation.ArtifactVersion> artifacts(
            Connection connection, String populationId, String populationVersion) throws SQLException {
        var result = new TreeMap<String, AnalysisPopulation.ArtifactVersion>();
        try (var statement = connection.prepareStatement("""
                SELECT artifact_role, artifact_id, artifact_version, sha256
                FROM population_artifact
                WHERE population_id = ? AND population_version = ?
                ORDER BY artifact_role
                """)) {
            bindPopulation(statement, populationId, populationVersion);
            try (var rows = statement.executeQuery()) {
                while (rows.next()) {
                    result.put(rows.getString("artifact_role"), new AnalysisPopulation.ArtifactVersion(
                            rows.getString("artifact_id"), rows.getString("artifact_version"), rows.getString("sha256")));
                }
            }
        }
        return Map.copyOf(result);
    }

    private static Map<String, DimensionDefinition> definitions(
            Connection connection, String populationId, String populationVersion) throws SQLException {
        var result = new TreeMap<String, DimensionDefinition>();
        try (var statement = connection.prepareStatement("""
                SELECT dimension_id, cardinality, origin
                FROM population_dimension
                WHERE population_id = ? AND population_version = ?
                ORDER BY dimension_id
                """)) {
            bindPopulation(statement, populationId, populationVersion);
            try (var rows = statement.executeQuery()) {
                while (rows.next()) {
                    var id = rows.getString("dimension_id");
                    result.put(id, new DimensionDefinition(id,
                            DimensionDefinition.Cardinality.valueOf(rows.getString("cardinality")),
                            DimensionDefinition.Origin.valueOf(rows.getString("origin"))));
                }
            }
        }
        return Map.copyOf(result);
    }

    private static List<VariantRecord> records(
            Connection connection, String populationId, String populationVersion, Set<String> dimensionIds) throws SQLException {
        var records = new ArrayList<VariantRecord>();
        try (var statement = connection.prepareStatement("""
                SELECT record_id
                FROM population_record
                WHERE population_id = ? AND population_version = ?
                ORDER BY record_id
                """)) {
            bindPopulation(statement, populationId, populationVersion);
            try (var rows = statement.executeQuery()) {
                while (rows.next()) {
                    var recordId = rows.getString("record_id");
                    records.add(new VariantRecord(recordId,
                            new Variant(elements(connection, populationId, populationVersion, recordId)),
                            recordDimensions(connection, populationId, populationVersion, recordId, dimensionIds)));
                }
            }
        }
        return List.copyOf(records);
    }

    private static Set<String> elements(Connection connection, String populationId, String populationVersion, String recordId)
            throws SQLException {
        var result = new TreeSet<String>();
        try (var statement = connection.prepareStatement("""
                SELECT element_id FROM record_element
                WHERE population_id = ? AND population_version = ? AND record_id = ?
                ORDER BY element_id
                """)) {
            bindRecord(statement, populationId, populationVersion, recordId);
            try (var rows = statement.executeQuery()) {
                while (rows.next()) {
                    result.add(rows.getString("element_id"));
                }
            }
        }
        return Set.copyOf(result);
    }

    private static Map<String, DimensionValue> recordDimensions(
            Connection connection, String populationId, String populationVersion, String recordId, Set<String> dimensionIds)
            throws SQLException {
        var result = new TreeMap<String, DimensionValue>();
        try (var statement = connection.prepareStatement("""
                SELECT dimension_id, value_state FROM record_dimension
                WHERE population_id = ? AND population_version = ? AND record_id = ?
                ORDER BY dimension_id
                """)) {
            bindRecord(statement, populationId, populationVersion, recordId);
            try (var rows = statement.executeQuery()) {
                while (rows.next()) {
                    var dimensionId = rows.getString("dimension_id");
                    var state = DimensionValue.State.valueOf(rows.getString("value_state"));
                    result.put(dimensionId, state == DimensionValue.State.UNKNOWN
                            ? DimensionValue.unknown()
                            : DimensionValue.known(dimensionValues(connection, populationId, populationVersion, recordId, dimensionId)));
                }
            }
        }
        if (!result.keySet().equals(dimensionIds)) {
            throw new IllegalStateException("persisted record dimensions do not match population definitions");
        }
        return Map.copyOf(result);
    }

    private static Set<String> dimensionValues(
            Connection connection, String populationId, String populationVersion, String recordId, String dimensionId) throws SQLException {
        var result = new TreeSet<String>();
        try (var statement = connection.prepareStatement("""
                SELECT dimension_value FROM record_dimension_value
                WHERE population_id = ? AND population_version = ? AND record_id = ? AND dimension_id = ?
                ORDER BY dimension_value
                """)) {
            bindRecord(statement, populationId, populationVersion, recordId);
            statement.setString(4, dimensionId);
            try (var rows = statement.executeQuery()) {
                while (rows.next()) {
                    result.add(rows.getString("dimension_value"));
                }
            }
        }
        return Set.copyOf(result);
    }

    private static List<FoodServiceFixtureAdapter.RejectedRecord> rejectedRecords(
            Connection connection, String populationId, String populationVersion) throws SQLException {
        var result = new ArrayList<FoodServiceFixtureAdapter.RejectedRecord>();
        try (var statement = connection.prepareStatement("""
                SELECT source_record_id, reason, detail FROM rejected_source_record
                WHERE population_id = ? AND population_version = ?
                ORDER BY source_record_id
                """)) {
            bindPopulation(statement, populationId, populationVersion);
            try (var rows = statement.executeQuery()) {
                while (rows.next()) {
                    result.add(new FoodServiceFixtureAdapter.RejectedRecord(
                            rows.getString("source_record_id"), rows.getString("reason"), rows.getString("detail")));
                }
            }
        }
        return List.copyOf(result);
    }

    private static void insert(
            Connection connection,
            AnalysisPopulation population,
            List<FoodServiceFixtureAdapter.RejectedRecord> rejectedRecords) throws SQLException {
        update(connection, "INSERT INTO analysis_population (population_id, population_version) VALUES (?, ?)",
                population.id(), population.version());
        for (var artifact : population.provenance().entrySet()) {
            update(connection, """
                    INSERT INTO population_artifact
                    (population_id, population_version, artifact_role, artifact_id, artifact_version, sha256)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """, population.id(), population.version(), artifact.getKey(), artifact.getValue().id(),
                    artifact.getValue().version(), artifact.getValue().sha256());
        }
        for (var definition : population.dimensionDefinitions().values()) {
            update(connection, """
                    INSERT INTO population_dimension
                    (population_id, population_version, dimension_id, cardinality, origin)
                    VALUES (?, ?, ?, ?, ?)
                    """, population.id(), population.version(), definition.id(), definition.cardinality().name(), definition.origin().name());
        }
        for (var record : population.records()) {
            insertRecord(connection, population, record);
        }
        for (var rejected : rejectedRecords) {
            update(connection, """
                    INSERT INTO rejected_source_record
                    (population_id, population_version, source_record_id, reason, detail)
                    VALUES (?, ?, ?, ?, ?)
                    """, population.id(), population.version(), rejected.sourceRecordId(), rejected.reason(), rejected.detail());
        }
    }

    private static void insertRecord(Connection connection, AnalysisPopulation population, VariantRecord record) throws SQLException {
        update(connection, """
                INSERT INTO population_record (population_id, population_version, record_id)
                VALUES (?, ?, ?)
                """, population.id(), population.version(), record.id());
        for (var elementId : record.variant().elementIds()) {
            update(connection, """
                    INSERT INTO record_element (population_id, population_version, record_id, element_id)
                    VALUES (?, ?, ?, ?)
                    """, population.id(), population.version(), record.id(), elementId);
        }
        for (var dimension : record.dimensions().entrySet()) {
            update(connection, """
                    INSERT INTO record_dimension
                    (population_id, population_version, record_id, dimension_id, value_state)
                    VALUES (?, ?, ?, ?, ?)
                    """, population.id(), population.version(), record.id(), dimension.getKey(), dimension.getValue().state().name());
            for (var value : dimension.getValue().values()) {
                update(connection, """
                        INSERT INTO record_dimension_value
                        (population_id, population_version, record_id, dimension_id, dimension_value)
                        VALUES (?, ?, ?, ?, ?)
                        """, population.id(), population.version(), record.id(), dimension.getKey(), value);
            }
        }
    }

    private static void update(Connection connection, String sql, String... values) throws SQLException {
        try (var statement = connection.prepareStatement(sql)) {
            for (var index = 0; index < values.length; index++) {
                statement.setString(index + 1, values[index]);
            }
            statement.executeUpdate();
        }
    }

    private static void bindPopulation(PreparedStatement statement, String populationId, String populationVersion) throws SQLException {
        statement.setString(1, populationId);
        statement.setString(2, populationVersion);
    }

    private static void bindRecord(PreparedStatement statement, String populationId, String populationVersion, String recordId)
            throws SQLException {
        bindPopulation(statement, populationId, populationVersion);
        statement.setString(3, recordId);
    }

    private static void rollback(Connection connection, Exception cause) {
        try {
            connection.rollback();
        } catch (SQLException rollbackFailure) {
            cause.addSuppressed(rollbackFailure);
        }
    }

    private static IllegalStateException persistenceFailure(Exception exception) {
        return new IllegalStateException("population persistence failed", exception);
    }
}
