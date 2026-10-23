package de.eseidinger.variabilityengineering.adapter.odip;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlatformHttpClientFactoryTest {
    @TempDir Path directory;

    @Test
    void addsPlatformCaAndRetainsJvmRoots() throws Exception {
        var bundle = Path.of("src/test/resources/tls/test-ca.pem");
        X509Certificate certificate;
        try (var stream = Files.newInputStream(bundle)) {
            certificate = (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(stream);
        }
        var combined = PlatformHttpClientFactory.trustManager(bundle);
        assertTrue(Arrays.asList(combined.getAcceptedIssuers()).contains(certificate));
        combined.checkServerTrusted(new X509Certificate[] {certificate}, "RSA");

        var defaults = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        defaults.init((KeyStore) null);
        for (var manager : defaults.getTrustManagers()) {
            if (manager instanceof X509TrustManager roots) {
                assertTrue(Arrays.asList(combined.getAcceptedIssuers())
                        .containsAll(Arrays.asList(roots.getAcceptedIssuers())));
            }
        }
        var client = PlatformHttpClientFactory.create(Map.of("SSL_CERT_FILE", bundle.toString()));
        assertEquals(HttpClient.Redirect.NEVER, client.followRedirects());
    }

    @Test
    void supportsPublicDeploymentsWithoutAnInjectedBundle() {
        assertNotNull(PlatformHttpClientFactory.create(Map.of()));
        assertNotNull(PlatformHttpClientFactory.create(Map.of("SSL_CERT_FILE", " ")));
    }

    @Test
    void rejectsMissingEmptyAndMalformedConfiguredBundles() throws Exception {
        for (String content : new String[] {"", "not a certificate"}) {
            var bundle = directory.resolve("ca.pem");
            Files.writeString(bundle, content);
            assertThrows(IllegalStateException.class,
                    () -> PlatformHttpClientFactory.create(Map.of("SSL_CERT_FILE", bundle.toString())));
        }
        assertThrows(IllegalStateException.class, () -> PlatformHttpClientFactory.create(
                Map.of("SSL_CERT_FILE", directory.resolve("missing.pem").toString())));
    }
}
