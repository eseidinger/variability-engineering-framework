package de.eseidinger.variabilityengineering.adapter.odip;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;

/** Retrieves one exact immutable ODIP version and verifies it before exposure. */
public final class EnergyPublicationClient {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);
    private static final int MAX_ATTEMPTS = 2;

    public interface TokenProvider {
        String token() throws IOException, InterruptedException;
    }

    public interface Transport {
        Response get(URI uri, String bearerToken, String etag, int maxBytes)
                throws IOException, InterruptedException;
    }

    public record Response(int status, String etag, byte[] bytes) {
        public Response {
            bytes = bytes == null ? null : bytes.clone();
        }
        @Override public byte[] bytes() { return bytes == null ? null : bytes.clone(); }
    }

    public record Cached(VerifiedEnergyPublication publication, String manifestEtag,
                         Map<String, String> artifactEtags) {
        public Cached {
            Objects.requireNonNull(publication);
            artifactEtags = Map.copyOf(artifactEtags);
        }
    }

    private final URI origin;
    private final TokenProvider tokens;
    private final Transport transport;
    private final EnergyPublicationVerifier verifier = new EnergyPublicationVerifier();

    public EnergyPublicationClient(URI origin, TokenProvider tokens, Transport transport) {
        Objects.requireNonNull(origin, "ODIP origin must not be null");
        if (!"https".equalsIgnoreCase(origin.getScheme()) || origin.getHost() == null
                || origin.getRawUserInfo() != null || origin.getRawQuery() != null || origin.getRawFragment() != null
                || !"".equals(origin.getRawPath()) && !"/".equals(origin.getRawPath())) {
            throw new IllegalArgumentException("ODIP must be configured as a bare HTTPS origin");
        }
        this.origin = origin;
        this.tokens = Objects.requireNonNull(tokens);
        this.transport = Objects.requireNonNull(transport);
    }

    public Cached fetchExact(String datasetId, String version, Cached cached) throws IOException, InterruptedException {
        if (datasetId == null || !datasetId.matches("[a-z][a-z0-9-]{2,99}")
                || version == null || !version.matches("(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)")) {
            throw new IllegalArgumentException("invalid immutable dataset reference");
        }
        if (cached != null && (!datasetId.equals(cached.publication().profile().datasetId())
                || !version.equals(cached.publication().profile().version()))) {
            throw new IllegalArgumentException("cached publication is for another immutable version");
        }
        String prefix = "/api/v1/datasets/" + datasetId + "/versions/" + version;
        String token = tokens.token();
        var manifestResponse = get(prefix, token, cached == null ? null : cached.manifestEtag(),
                EnergyPublicationVerifier.MAX_MANIFEST_BYTES);
        byte[] manifest = selectedBytes(manifestResponse, cached == null ? null : cached.publication().manifestBytes());
        String manifestEtag = selectedEtag(manifestResponse, cached == null ? null : cached.manifestEtag());
        var descriptors = descriptors(manifest);
        var artifacts = new HashMap<String, byte[]>();
        var artifactEtags = new HashMap<String, String>();
        long total = manifest.length;
        for (var id : descriptors) {
            byte[] previous = cached == null ? null : cached.publication().artifactBytes().get(id);
            String previousEtag = cached == null ? null : cached.artifactEtags().get(id);
            var response = get(prefix + "/artifacts/" + id, token, previousEtag,
                    EnergyPublicationVerifier.MAX_ARTIFACT_BYTES);
            byte[] bytes = selectedBytes(response, previous);
            total += bytes.length;
            if (total > EnergyPublicationVerifier.MAX_TOTAL_BYTES) {
                throw new IOException("publication exceeds aggregate byte limit");
            }
            artifacts.put(id, bytes);
            String etag = selectedEtag(response, previousEtag);
            if (etag != null) artifactEtags.put(id, etag);
        }
        var publication = verifier.verify(manifest, artifacts);
        if (!datasetId.equals(publication.profile().datasetId())
                || !version.equals(publication.profile().version())) {
            throw new IOException("ODIP returned a different immutable dataset version");
        }
        return new Cached(publication, manifestEtag, artifactEtags);
    }

    private Response get(String path, String token, String etag, int maxBytes) throws IOException, InterruptedException {
        URI uri = origin.resolve(path);
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            Response response;
            try {
                response = transport.get(uri, token, etag, maxBytes);
            } catch (IOException e) {
                if (attempt + 1 == MAX_ATTEMPTS) throw e;
                continue;
            }
            if (response.status() == 200 || response.status() == 304) {
                return response;
            }
            if (response.status() != 429 && response.status() < 500 || attempt + 1 == MAX_ATTEMPTS) {
                throw new IOException("ODIP returned HTTP " + response.status());
            }
        }
        throw new IllegalStateException("retry loop exhausted");
    }

    private static byte[] selectedBytes(Response response, byte[] previous) throws IOException {
        if (response.status() == 304) {
            if (previous == null) throw new IOException("ODIP returned 304 without verified cached bytes");
            return previous;
        }
        if (response.bytes() == null) throw new IOException("ODIP returned no bytes");
        return response.bytes();
    }

    private static String selectedEtag(Response response, String previous) {
        return response.status() == 304 ? previous : response.etag();
    }

    private static LinkedHashSet<String> descriptors(byte[] manifest) throws IOException {
        JsonNode document = MAPPER.readTree(manifest);
        if (document == null || !document.isObject() || !document.path("artifacts").isArray()
                || document.path("artifacts").size() != 5) {
            throw new IOException("invalid ODIP manifest artifact list");
        }
        var ids = new LinkedHashSet<String>();
        for (var descriptor : document.path("artifacts")) {
            String id = descriptor.path("artifactId").asText();
            if (!id.matches("[a-z][a-z0-9-]{1,99}") || !ids.add(id)) {
                throw new IOException("invalid or duplicate ODIP artifact ID");
            }
        }
        return ids;
    }

    /** JDK transport refuses redirects and stops decoding once the byte limit is hit. */
    public static final class JdkTransport implements Transport {
        private final HttpClient client;

        public JdkTransport() {
            client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
                    .followRedirects(HttpClient.Redirect.NEVER).build();
        }

        @Override
        public Response get(URI uri, String bearerToken, String etag, int maxBytes)
                throws IOException, InterruptedException {
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
                throw new IllegalArgumentException("ODIP requests must use HTTPS");
            }
            var builder = HttpRequest.newBuilder(uri).GET().timeout(REQUEST_TIMEOUT)
                    .header("Accept", "application/json").header("Authorization", "Bearer " + bearerToken);
            if (etag != null) builder.header("If-None-Match", etag);
            var response = client.send(builder.build(), HttpResponse.BodyHandlers.ofInputStream());
            try (var body = response.body()) {
                if (response.statusCode() != 200) {
                    return new Response(response.statusCode(), response.headers().firstValue("ETag").orElse(null), null);
                }
                // readNBytes caps retained memory; the extra byte detects an oversized body.
                byte[] bytes = body.readNBytes(maxBytes + 1);
                if (bytes.length > maxBytes) throw new IOException("ODIP response exceeds byte limit");
                return new Response(response.statusCode(), response.headers().firstValue("ETag").orElse(null), bytes);
            }
        }
    }
}
