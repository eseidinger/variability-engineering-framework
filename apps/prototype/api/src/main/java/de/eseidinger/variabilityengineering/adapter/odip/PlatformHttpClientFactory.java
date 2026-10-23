package de.eseidinger.variabilityengineering.adapter.odip;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.time.Duration;
import java.util.Map;

/** Adds platform-managed PEM trust to the JVM roots without disabling TLS validation. */
final class PlatformHttpClientFactory {
    private PlatformHttpClientFactory() {}

    static HttpClient create(Map<String, String> environment) {
        var builder = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER);
        String bundle = environment.get("SSL_CERT_FILE");
        if (bundle != null && !bundle.isBlank()) {
            try {
                var context = SSLContext.getInstance("TLS");
                context.init(null, new TrustManager[] {trustManager(Path.of(bundle))}, null);
                builder.sslContext(context);
            } catch (IOException | GeneralSecurityException exception) {
                throw new IllegalStateException("Unable to load platform TLS CA bundle", exception);
            }
        }
        return builder.build();
    }

    static X509TrustManager trustManager(Path bundle) throws GeneralSecurityException, IOException {
        var defaults = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        defaults.init((KeyStore) null);
        var roots = KeyStore.getInstance(KeyStore.getDefaultType());
        roots.load(null, null);
        int index = 0;
        for (var manager : defaults.getTrustManagers()) {
            if (manager instanceof X509TrustManager certificates) {
                for (var certificate : certificates.getAcceptedIssuers()) {
                    roots.setCertificateEntry("jvm-" + index++, certificate);
                }
            }
        }
        try (var stream = Files.newInputStream(bundle)) {
            var certificates = CertificateFactory.getInstance("X.509").generateCertificates(stream);
            if (certificates.isEmpty()) {
                throw new GeneralSecurityException("Platform TLS CA bundle contains no certificates");
            }
            for (var certificate : certificates) {
                roots.setCertificateEntry("platform-" + index++, certificate);
            }
        }
        var combined = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        combined.init(roots);
        for (var manager : combined.getTrustManagers()) {
            if (manager instanceof X509TrustManager certificates) return certificates;
        }
        throw new GeneralSecurityException("No X.509 trust manager available");
    }
}
