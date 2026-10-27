package de.eseidinger.variabilityengineering.adapter.odip;

import com.fasterxml.jackson.databind.JsonNode;
import de.eseidinger.variabilityengineering.energy.EnergyContracts;
import de.eseidinger.variabilityengineering.energy.EnergyCatalog;
import de.eseidinger.variabilityengineering.energy.EnergyComponent;
import de.eseidinger.variabilityengineering.energy.EnergyOptimizationRequest;
import de.eseidinger.variabilityengineering.energy.EnergyProfile;

import java.net.URI;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Verifies downloaded bytes and cross-document semantics before admitting a publication. */
public final class EnergyPublicationVerifier {
    public static final int MAX_MANIFEST_BYTES = 1_048_576;
    public static final int MAX_ARTIFACT_BYTES = 10 * 1_048_576;
    public static final int MAX_TOTAL_BYTES = 50 * 1_048_576;

    private record Role(String schemaVersion, String unit) { }
    private static final Map<String, Role> ROLES = Map.of(
            "PV_YIELD_PER_KWP", new Role("energy-pv-yield/v1", "kWh/kWp"),
            "IMPORT_TARIFF", new Role("energy-import-tariff/v1", "EUR/kWh"),
            "EXPORT_TARIFF", new Role("energy-export-tariff/v1", "EUR/kWh"),
            "REFERENCE_LOAD_SHAPE", new Role("energy-reference-load-shape/v1", "fraction"),
            "COMPONENT_CATALOG", new Role(EnergyCatalog.SCHEMA_VERSION, null));

    public VerifiedEnergyPublication verify(byte[] manifestBytes, Map<String, byte[]> artifactBytes) {
        Objects.requireNonNull(manifestBytes, "manifest bytes must not be null");
        Objects.requireNonNull(artifactBytes, "artifact bytes must not be null");
        require(manifestBytes.length <= MAX_MANIFEST_BYTES, "manifest exceeds 1 MiB");
        long totalBytes = manifestBytes.length;
        for (var bytes : artifactBytes.values()) {
            require(bytes != null && bytes.length <= MAX_ARTIFACT_BYTES, "artifact exceeds 10 MiB");
            totalBytes += bytes.length;
        }
        require(totalBytes <= MAX_TOTAL_BYTES, "publication exceeds 50 MiB");
        var manifest = parse(manifestBytes);
        EnergyContracts.validate("dataset-publication-v1", manifest);
        fields(manifest, Set.of("schemaVersion", "datasetId", "version", "status", "schemaRef",
                "metadata", "publishedAt", "artifacts", "quality", "provenance"), Set.of());
        require("dataset-publication/v1".equals(string(manifest, "schemaVersion")), "unsupported manifest schema");
        require(EnergyOptimizationRequest.PROFILE.equals(string(manifest, "schemaRef")), "unsupported energy profile");
        require("PUBLISHED".equals(string(manifest, "status")), "publication is not published");
        String datasetId = string(manifest, "datasetId");
        require(datasetId.matches("[a-z][a-z0-9-]{2,99}"), "invalid dataset ID");
        String version = string(manifest, "version");
        require(version.matches("(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)"), "invalid version");
        instant(manifest, "publishedAt");
        var metadata = object(manifest, "metadata");
        require(EnergyOptimizationRequest.REGION.equals(string(metadata, "regionCode")), "unsupported region");
        require("SYNTHETIC_CALENDAR_PROFILE".equals(string(metadata, "profileKind")), "unsupported profile kind");
        var period = object(metadata, "period");
        fields(period, Set.of("start", "endExclusive", "step"), Set.of());
        Instant start = instant(period, "start");
        Instant end = instant(period, "endExclusive");
        require("PT1H".equals(string(period, "step")), "only one-hour intervals are supported");
        int hours = EnergyProfile.calendarYearHours(start, end);

        var quality = object(manifest, "quality");
        fields(quality, Set.of("status", "policyRef", "warnings"), Set.of());
        require("PASS".equals(string(quality, "status")), "quality checks did not pass");
        require("urn:portfolio:energy-quality:v1".equals(string(quality, "policyRef")), "unsupported quality policy");
        var warnings = new ArrayList<VerifiedEnergyPublication.Warning>();
        for (var warning : array(quality, "warnings")) {
            fields(warning, Set.of("code", "message"), Set.of());
            String code = string(warning, "code");
            String message = string(warning, "message");
            require(code.matches("[a-z][a-z0-9_]*") && message.length() <= 500,
                    "invalid publication warning");
            warnings.add(new VerifiedEnergyPublication.Warning(code, message));
        }
        var provenance = array(manifest, "provenance");
        require(!provenance.isEmpty(), "publication provenance is required");
        for (var source : provenance) {
            fields(source, Set.of("sourceId", "sourceOwner", "license", "retrievedAt",
                    "transformationVersion"), Set.of("sourceVersion", "rawArtifactId"));
            string(source, "sourceId");
            string(source, "sourceOwner");
            string(source, "license");
            string(source, "transformationVersion");
            instant(source, "retrievedAt");
        }

        var descriptors = array(manifest, "artifacts");
        require(descriptors.size() == ROLES.size() && artifactBytes.size() == ROLES.size(),
                "energy publication needs exactly five artifacts");
        var roleDocuments = new HashMap<String, JsonNode>();
        var ids = new HashSet<String>();
        var paths = new HashSet<String>();
        for (var descriptor : descriptors) {
            fields(descriptor, Set.of("artifactId", "role", "mediaType", "sha256", "path"), Set.of("unit"));
            String id = string(descriptor, "artifactId");
            String role = string(descriptor, "role");
            Role expected = ROLES.get(role);
            require(id.matches("[a-z][a-z0-9-]{1,99}"), "invalid artifact ID");
            require(expected != null && !roleDocuments.containsKey(role), "unknown or duplicate artifact role");
            require(ids.add(id), "duplicate artifact ID");
            String path = string(descriptor, "path");
            require(path.equals("artifacts/" + id) && paths.add(path), "unsafe or duplicate artifact path");
            require("application/json".equals(string(descriptor, "mediaType")), "artifact must be JSON");
            JsonNode unit = descriptor.get("unit");
            require(expected.unit() == null ? unit == null : unit != null && expected.unit().equals(unit.asText()),
                    "artifact manifest unit differs from role");
            byte[] bytes = artifactBytes.get(id);
            require(bytes != null, "missing artifact: " + id);
            require(sha256(bytes).equals(string(descriptor, "sha256")), "artifact digest mismatch: " + id);
            var document = parse(bytes);
            EnergyContracts.validate(expected.schemaVersion().replace("/", "-"), document);
            require(expected.schemaVersion().equals(string(document, "schemaVersion")), "unsupported artifact schema: " + id);
            roleDocuments.put(role, document);
        }
        require(ids.equals(artifactBytes.keySet()), "unexpected artifact bytes");

        var catalog = parseCatalog(roleDocuments.get("COMPONENT_CATALOG"), version);
        var pv = series(roleDocuments.get("PV_YIELD_PER_KWP"), period, start, hours, "kWh/kWp");
        var load = series(roleDocuments.get("REFERENCE_LOAD_SHAPE"), period, start, hours, "fraction");
        var imports = series(roleDocuments.get("IMPORT_TARIFF"), period, start, hours, "EUR/kWh");
        var exports = series(roleDocuments.get("EXPORT_TARIFF"), period, start, hours, "EUR/kWh");
        var profileHours = new ArrayList<EnergyProfile.Hour>(hours);
        for (int i = 0; i < hours; i++) {
            profileHours.add(new EnergyProfile.Hour(start.plus(i, ChronoUnit.HOURS),
                    pv.get(i), load.get(i), imports.get(i), exports.get(i)));
        }
        var profile = new EnergyProfile(datasetId, version, start, end, profileHours);
        return new VerifiedEnergyPublication(manifestBytes, artifactBytes, sha256(manifestBytes),
                catalog, profile, warnings);
    }

    private static EnergyCatalog parseCatalog(JsonNode document, String publicationVersion) {
        fields(document, Set.of("schemaVersion", "catalogVersion", "currency", "components"), Set.of());
        require(publicationVersion.equals(string(document, "catalogVersion")), "catalog version differs from publication");
        var components = new ArrayList<EnergyComponent>();
        for (var node : array(document, "components")) {
            String type = string(node, "type");
            Set<String> base = Set.of("componentId", "type", "displayName", "unitPriceEUR", "available", "source");
            Set<String> technical = switch (type) {
                case "PV_MODULE" -> Set.of("ratedPowerKWp", "areaM2");
                case "INVERTER" -> Set.of("acPowerKW", "efficiencyFraction");
                case "BATTERY" -> Set.of("usableCapacityKWh", "maxChargePowerKW",
                        "maxDischargePowerKW", "roundTripEfficiencyFraction");
                default -> throw new IllegalArgumentException("unsupported component type");
            };
            var all = new HashSet<>(base);
            all.addAll(technical);
            fields(node, all, Set.of());
            var source = object(node, "source");
            fields(source, Set.of("owner", "reference", "license", "retrievedAt"), Set.of("version"));
            var metadata = new EnergyComponent.Metadata(string(node, "componentId"), string(node, "displayName"),
                    number(node, "unitPriceEUR"), bool(node, "available"),
                    new EnergyComponent.Source(string(source, "owner"), URI.create(string(source, "reference")),
                            source.has("version") ? string(source, "version") : null,
                            string(source, "license"), instant(source, "retrievedAt")));
            components.add(switch (type) {
                case "PV_MODULE" -> new EnergyComponent.PvModule(metadata,
                        number(node, "ratedPowerKWp"), number(node, "areaM2"));
                case "INVERTER" -> new EnergyComponent.Inverter(metadata,
                        number(node, "acPowerKW"), number(node, "efficiencyFraction"));
                case "BATTERY" -> new EnergyComponent.Battery(metadata, number(node, "usableCapacityKWh"),
                        number(node, "maxChargePowerKW"), number(node, "maxDischargePowerKW"),
                        number(node, "roundTripEfficiencyFraction"));
                default -> throw new IllegalArgumentException("unsupported component type");
            });
        }
        require("EUR".equals(string(document, "currency")), "unsupported catalog currency");
        return new EnergyCatalog(string(document, "schemaVersion"), string(document, "catalogVersion"),
                string(document, "currency"), components);
    }

    private static List<Double> series(JsonNode document, JsonNode manifestPeriod, Instant start,
                                       int count, String unit) {
        fields(document, Set.of("schemaVersion", "unit", "period", "values"), Set.of());
        require(unit.equals(string(document, "unit")), "artifact unit differs from role");
        var period = object(document, "period");
        fields(period, Set.of("start", "endExclusive", "step"), Set.of());
        require(period.equals(manifestPeriod), "artifact period differs from manifest");
        var values = array(document, "values");
        require(values.size() == count, "series does not contain the declared number of hours");
        var numbers = new ArrayList<Double>(count);
        for (int i = 0; i < count; i++) {
            var item = values.get(i);
            fields(item, Set.of("start", "value"), Set.of());
            require(instant(item, "start").equals(start.plus(i, ChronoUnit.HOURS)),
                    "series has a missing, duplicate, or misordered hour");
            double value = number(item, "value");
            require(value >= 0, "series values must be non-negative");
            if ("fraction".equals(unit)) require(value <= 1, "load fractions must not exceed one");
            numbers.add(value);
        }
        return numbers;
    }

    private static JsonNode parse(byte[] bytes) {
        try {
            var node = EnergyContracts.parse(bytes);
            require(node != null && node.isObject(), "JSON document must be an object");
            return node;
        } catch (java.io.IOException e) {
            throw new IllegalArgumentException("invalid publication JSON", e);
        }
    }

    private static void fields(JsonNode node, Set<String> required, Set<String> optional) {
        require(node != null && node.isObject(), "expected JSON object");
        for (var name : required) require(node.has(name), "missing required field: " + name);
        node.fieldNames().forEachRemaining(name -> require(required.contains(name) || optional.contains(name),
                "unsupported field: " + name));
    }

    private static JsonNode object(JsonNode node, String field) {
        var value = node.path(field);
        require(value.isObject(), field + " must be an object");
        return value;
    }

    private static JsonNode array(JsonNode node, String field) {
        var value = node.path(field);
        require(value.isArray(), field + " must be an array");
        return value;
    }

    private static String string(JsonNode node, String field) {
        var value = node.path(field);
        require(value.isTextual() && !value.asText().isBlank(), field + " must be text");
        return value.asText();
    }

    private static boolean bool(JsonNode node, String field) {
        var value = node.path(field);
        require(value.isBoolean(), field + " must be boolean");
        return value.asBoolean();
    }

    private static double number(JsonNode node, String field) {
        var value = node.path(field);
        require(value.isNumber() && Double.isFinite(value.asDouble()), field + " must be finite number");
        return value.asDouble();
    }

    private static Instant instant(JsonNode node, String field) {
        String value = string(node, field);
        require(value.endsWith("Z"), field + " must be a UTC instant");
        try {
            return Instant.parse(value);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException(field + " must be a UTC instant", e);
        }
    }

    private static String sha256(byte[] bytes) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }
}
