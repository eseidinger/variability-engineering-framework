package de.eseidinger.variabilityengineering.adapter.odip;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/** Short-lived managed client_credentials tokens for the approved ODIP reader binding. */
public final class ManagedClientCredentialsTokenProvider implements EnergyPublicationClient.TokenProvider {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final URI tokenUri;
    private final String clientId;
    private final String clientSecret;
    private final HttpClient client;
    private String cachedToken;
    private Instant refreshAt = Instant.EPOCH;

    public ManagedClientCredentialsTokenProvider(String secretPrefix, Map<String, String> environment) {
        Objects.requireNonNull(secretPrefix);
        Objects.requireNonNull(environment);
        if (!secretPrefix.matches("[A-Z][A-Z0-9_]*")) {
            throw new IllegalArgumentException("invalid managed secret prefix");
        }
        clientId = required(environment, secretPrefix + "_CLIENT_ID");
        clientSecret = required(environment, secretPrefix + "_CLIENT_SECRET");
        tokenUri = URI.create(required(environment, secretPrefix + "_TOKEN_URL"));
        if (!"https".equalsIgnoreCase(tokenUri.getScheme()) || tokenUri.getHost() == null
                || tokenUri.getRawUserInfo() != null || tokenUri.getRawFragment() != null) {
            throw new IllegalArgumentException("managed token URL must use HTTPS");
        }
        client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER).build();
    }

    @Override
    public synchronized String token() throws IOException, InterruptedException {
        if (cachedToken != null && Instant.now().isBefore(refreshAt)) {
            return cachedToken;
        }
        String form = "grant_type=client_credentials&client_id=" + encode(clientId)
                + "&client_secret=" + encode(clientSecret);
        var request = HttpRequest.newBuilder(tokenUri).timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(form)).build();
        var response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        try (var body = response.body()) {
            if (response.statusCode() != 200) {
                throw new IOException("managed token endpoint returned HTTP " + response.statusCode());
            }
            byte[] bytes = body.readNBytes(16_385);
            if (bytes.length > 16_384) {
                throw new IOException("managed token response exceeds byte limit");
            }
            var document = MAPPER.readTree(bytes);
            if (document == null || !document.path("access_token").isTextual()
                    || document.path("access_token").asText().isBlank()
                    || !document.path("expires_in").canConvertToLong()) {
                throw new IOException("invalid managed token response");
            }
            long expiresIn = document.path("expires_in").asLong();
            if (expiresIn <= 0 || expiresIn > 86_400) {
                throw new IOException("invalid managed token lifetime");
            }
            cachedToken = document.path("access_token").asText();
            refreshAt = Instant.now().plusSeconds(Math.max(1, expiresIn - Math.min(60, expiresIn / 10)));
            return cachedToken;
        }
    }

    private static String required(Map<String, String> environment, String name) {
        var value = environment.get(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("missing managed secret binding: " + name);
        }
        return value;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
