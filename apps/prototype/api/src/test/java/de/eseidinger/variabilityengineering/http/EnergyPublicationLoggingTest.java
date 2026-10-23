package de.eseidinger.variabilityengineering.http;

import de.eseidinger.variabilityengineering.adapter.odip.EnergyPublicationClient;
import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLHandshakeException;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EnergyPublicationLoggingTest {
    @Test
    void reportsTlsCauseWithoutLoggingExceptionMessages() {
        var failure = new IOException("response contains secret-token",
                new SSLHandshakeException("private identity URL and credential"));
        String summary = EnergyOptimizationResource.publicationFailureSummary(failure);
        assertEquals("java.io.IOException caused by javax.net.ssl.SSLHandshakeException", summary);
        assertFalse(summary.contains("secret-token"));
        assertFalse(summary.contains("credential"));
    }

    @Test
    void includesUpstreamHttpStatus() {
        String summary = EnergyOptimizationResource.publicationFailureSummary(
                new EnergyPublicationClient.HttpStatusException(403));
        assertTrue(summary.endsWith(" (HTTP 403)"));
    }

    @Test
    void boundsCyclicExceptionCauses() {
        var first = new IOException("first");
        var second = new IOException("second", first);
        first.initCause(second);
        String summary = EnergyOptimizationResource.publicationFailureSummary(first);
        assertEquals(8, summary.split(" caused by ").length);
    }
}
