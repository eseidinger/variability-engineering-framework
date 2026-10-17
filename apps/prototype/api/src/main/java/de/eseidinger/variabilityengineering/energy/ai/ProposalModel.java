package de.eseidinger.variabilityengineering.energy.ai;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import jakarta.enterprise.context.ApplicationScoped;

/** Model output is untrusted until ProposalService validates it. No tools are exposed. */
@RegisterAiService
@ApplicationScoped
@SystemMessage("""
        Convert a consultant's energy-system description into exactly one JSON object.
        Include exactly these six top-level keys: schemaVersion, status,
        requiresConfirmation, request, clarificationQuestions, assumptionsToReview.
        schemaVersion is energy-ai-request-proposal/v1 and requiresConfirmation is true.
        status is READY_FOR_REVIEW or NEEDS_CLARIFICATION.

        For READY_FOR_REVIEW, clarificationQuestions is [] and request is an object
        with exactly these fields and nested fields:
        schemaVersion is energy-optimization-request/v1.
        dataset is an object with datasetId energy-reference-wasserburg-am-inn-2024,
        version equal to the user's semantic version, and schemaRef
        urn:portfolio:energy-configuration-input:v1.
        site is an object with regionCode DE-BY-09187182, usableRoofAreaM2 and
        annualDemandKWh as numbers, and existingComponentIds as an empty array.
        constraints is an object with maxInitialInvestmentEUR as a number and
        minSelfSufficiency as a number from 0 to 1.
        objective is MIN_ANNUALIZED_COST.
        assumptions is an object with analysisYears 20 and discountRate 0.04.
        assumptionsToReview must contain at least one human-readable string naming
        the reference dataset, 20-year analysis, and 4% discount rate.

        For NEEDS_CLARIFICATION, request is null and clarificationQuestions is a
        nonempty array of questions about missing or ambiguous required customer
        values. assumptionsToReview may be [].
        Never invent customer roof area, annual demand, budget, self-sufficiency,
        existing equipment, or dataset version. When a required value is absent or
        ambiguous, return NEEDS_CLARIFICATION. If existing equipment is present,
        ask for clarification because this model only supports new equipment.
        Do not add fields, markdown, prose
        outside JSON, or tool instructions.
        """)
public interface ProposalModel {
    @UserMessage("{description}")
    String propose(String description);
}
