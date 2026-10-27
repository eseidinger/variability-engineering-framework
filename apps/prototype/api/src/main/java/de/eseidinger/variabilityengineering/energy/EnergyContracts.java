package de.eseidinger.variabilityengineering.energy;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SchemaValidatorsConfig;
import com.networknt.schema.SpecVersion;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/** Offline, immutable release schemas; schema IDs never cause network retrieval. */
public final class EnergyContracts {
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private static final Map<String, JsonSchema> SCHEMAS = compile();

    private EnergyContracts() { }

    public static JsonNode parse(String json) throws JsonProcessingException {
        return MAPPER.readTree(json);
    }

    public static JsonNode parse(byte[] json) throws IOException {
        return MAPPER.readTree(json);
    }

    public static void validate(String name, JsonNode document) {
        JsonSchema schema = SCHEMAS.get(name);
        if (schema == null) throw new IllegalStateException("unregistered energy schema");
        if (document == null || !schema.validate(document).isEmpty()) {
            throw new IllegalArgumentException("Document does not conform to " + name);
        }
    }

    private static Map<String, JsonSchema> compile() {
        try {
            var definitions = new HashMap<String, JsonNode>();
            var names = new HashMap<String, String>();
            for (JsonNode file : resource("index.json")) {
                String filename = file.asText();
                String name = filename.replace(".schema.json", "");
                JsonNode schema = resource(filename);
                definitions.put(name, schema);
                names.put(schema.path("$id").asText(), name);
            }
            var factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);
            var config = SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build();
            var compiled = new HashMap<String, JsonSchema>();
            for (String name : definitions.keySet()) {
                var root = MAPPER.createObjectNode();
                root.put("$schema", "https://json-schema.org/draft/2020-12/schema");
                root.put("$ref", "#/$defs/" + name);
                var schemas = root.putObject("$defs");
                definitions.forEach((key, value) -> schemas.set(key, rewrite(value, key, names)));
                compiled.put(name, factory.getSchema(root, config));
            }
            return Map.copyOf(compiled);
        } catch (IOException exception) {
            throw new IllegalStateException("packaged energy schemas unavailable", exception);
        }
    }

    private static JsonNode resource(String file) throws IOException {
        try (var stream = EnergyContracts.class.getResourceAsStream("/energy-contract/" + file)) {
            if (stream == null) throw new IOException("missing energy schema resource");
            return MAPPER.readTree(stream);
        }
    }

    private static JsonNode rewrite(JsonNode node, String context, Map<String, String> names) {
        if (node.isArray()) {
            var array = MAPPER.createArrayNode();
            node.forEach(value -> array.add(rewrite(value, context, names)));
            return array;
        }
        if (!node.isObject()) return node;
        var result = MAPPER.createObjectNode();
        node.properties().forEach(entry -> {
            if ("$id".equals(entry.getKey())) return;
            if ("$ref".equals(entry.getKey())) {
                String[] parts = entry.getValue().asText().split("#", 2);
                String target = parts[0].isEmpty() ? context : names.get(parts[0]);
                if (target == null) throw new IllegalStateException("unregistered energy schema reference");
                result.put("$ref", "#/$defs/" + target + (parts.length == 2 ? parts[1] : ""));
            } else result.set(entry.getKey(), rewrite(entry.getValue(), context, names));
        });
        return result;
    }
}
