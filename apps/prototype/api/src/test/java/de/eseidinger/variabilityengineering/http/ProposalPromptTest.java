package de.eseidinger.variabilityengineering.http;

import de.eseidinger.variabilityengineering.energy.ai.ProposalModel;
import de.eseidinger.variabilityengineering.energy.ai.ExplanationModel;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import io.quarkus.qute.Engine;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProposalPromptTest {
    @Test
    void explanationMessagesRenderWithExplicitCitationRequirements() throws Exception {
        Engine engine = Engine.builder().addDefaults().build();
        SystemMessage system = ExplanationModel.class.getAnnotation(SystemMessage.class);
        String rendered = engine.parse(String.join(system.delimiter(), system.value())).render();
        assertTrue(rendered.contains("CALCULATED_OUTPUT"));
        assertTrue(rendered.contains("/metrics/annualizedCostEUR"));
        assertTrue(rendered.contains("/constraints/0"));
        UserMessage user = ExplanationModel.class.getMethod("explain", String.class).getAnnotation(UserMessage.class);
        assertEquals("Approved evidence snapshot:\nexample evidence",
                engine.parse(String.join(user.delimiter(), user.value()))
                        .data("evidence", "example evidence").render());
    }

    @Test
    void systemAndUserMessagesRenderAsQuteTemplates() throws Exception {
        Engine engine = Engine.builder().addDefaults().build();
        SystemMessage system = ProposalModel.class.getAnnotation(SystemMessage.class);
        String renderedSystem = engine.parse(String.join(system.delimiter(), system.value())).render();
        assertTrue(renderedSystem.contains("energy-optimization-request/v1"));

        UserMessage user = ProposalModel.class.getMethod("propose", String.class).getAnnotation(UserMessage.class);
        assertEquals("example description", engine.parse(String.join(user.delimiter(), user.value()))
                .data("description", "example description").render());
    }
}
