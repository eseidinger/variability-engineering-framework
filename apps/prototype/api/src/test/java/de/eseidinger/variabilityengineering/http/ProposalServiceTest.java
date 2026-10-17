package de.eseidinger.variabilityengineering.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProposalServiceTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void acceptsReleasedReadyAndClarificationEnvelopes() throws Exception {
        String ready = example("ai-request-proposal.json");
        String clarification = example("ai-request-clarification.json");
        assertEquals("READY_FOR_REVIEW", service(ready).propose("complete").path("status").textValue());
        assertEquals("NEEDS_CLARIFICATION", service(clarification).propose("incomplete").path("status").textValue());
    }

    @Test
    void rejectsInvalidOrUnsupportedModelOutput() throws Exception {
        String ready = example("ai-request-proposal.json");
        assertEquals("invalid_envelope", assertThrows(ProposalService.InvalidProposal.class,
                () -> service(ready.replace("\"requiresConfirmation\": true", "\"requiresConfirmation\": false"))
                        .propose("complete")).reason());
        assertEquals("invalid_ready_domain", assertThrows(ProposalService.InvalidProposal.class,
                () -> service(ready.replace("\"version\": \"1.0.0\"", "\"version\": \"invented\""))
                        .propose("complete")).reason());
        assertEquals("invalid_json", assertThrows(ProposalService.InvalidProposal.class,
                () -> service(ready + "{}")
                        .propose("complete")).reason());
        assertEquals("invalid_ready_shape", assertThrows(ProposalService.InvalidProposal.class,
                () -> service(ready.replace("\"existingComponentIds\": []", "\"components\": []"))
                        .propose("complete")).reason());
    }

    private static ProposalService service(String output) {
        ProposalService service = new ProposalService();
        service.mapper = MAPPER;
        service.model = description -> output;
        return service;
    }

    private static String example(String file) throws Exception {
        return Files.readString(Path.of(System.getenv("ENERGY_CONTRACT_KIT_DIRECTORY"), "examples", file));
    }
}
