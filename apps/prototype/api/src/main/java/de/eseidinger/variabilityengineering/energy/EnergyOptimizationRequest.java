package de.eseidinger.variabilityengineering.energy;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/** Validated customer input; unsupported existing-equipment semantics are rejected by the model. */
public record EnergyOptimizationRequest(String schemaVersion, DatasetReference dataset, Site site,
                                        Constraints constraints, String objective, Assumptions assumptions) {
    public static final String SCHEMA_VERSION = "energy-optimization-request/v1";
    public static final String PROFILE = "urn:portfolio:energy-configuration-input:v1";
    public static final String REGION = "DE-BY-09187182";

    public EnergyOptimizationRequest {
        if (!SCHEMA_VERSION.equals(schemaVersion)) {
            throw new IllegalArgumentException("unsupported optimization request schema version");
        }
        Objects.requireNonNull(dataset, "dataset reference must not be null");
        Objects.requireNonNull(site, "site must not be null");
        Objects.requireNonNull(constraints, "constraints must not be null");
        if (!"MIN_ANNUALIZED_COST".equals(objective)) {
            throw new IllegalArgumentException("unsupported optimization objective");
        }
        Objects.requireNonNull(assumptions, "assumptions must not be null");
    }

    public record DatasetReference(String datasetId, String version, String schemaRef) {
        public DatasetReference {
            EnergyValidation.id(datasetId, "dataset ID");
            EnergyValidation.version(version, "dataset version");
            if (!PROFILE.equals(schemaRef)) {
                throw new IllegalArgumentException("unsupported energy profile");
            }
        }
    }

    public record Site(String regionCode, double usableRoofAreaM2, double annualDemandKWh,
                       List<String> existingComponentIds) {
        public Site {
            if (!REGION.equals(regionCode)) {
                throw new IllegalArgumentException("unsupported region");
            }
            EnergyValidation.nonNegative(usableRoofAreaM2, "usableRoofAreaM2");
            EnergyValidation.positive(annualDemandKWh, "annualDemandKWh");
            existingComponentIds = List.copyOf(existingComponentIds);
            if (existingComponentIds.size() > 105 || new HashSet<>(existingComponentIds).size() != existingComponentIds.size()) {
                throw new IllegalArgumentException("existing component IDs must be unique and contain at most 105 entries");
            }
            existingComponentIds.forEach(id -> EnergyValidation.id(id, "existing component ID"));
        }
    }

    public record Constraints(double maxInitialInvestmentEUR, double minSelfSufficiency) {
        public Constraints {
            EnergyValidation.nonNegative(maxInitialInvestmentEUR, "maxInitialInvestmentEUR");
            EnergyValidation.fraction(minSelfSufficiency, false, "minSelfSufficiency");
        }
    }

    public record Assumptions(int analysisYears, double discountRate) {
        public Assumptions {
            if (analysisYears != 20 || discountRate != 0.04) {
                throw new IllegalArgumentException("v1 requires 20 analysis years and a 0.04 discount rate");
            }
        }
    }
}
