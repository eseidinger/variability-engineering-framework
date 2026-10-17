package de.eseidinger.variabilityengineering.http;

import de.eseidinger.variabilityengineering.energy.ai.ProposalModel;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import io.quarkus.qute.Engine;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProposalPromptTest {
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
