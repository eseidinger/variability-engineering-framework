package de.eseidinger.variabilityengineering.energy;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Exhaustive reference optimization over one pinned, verified publication. */
public final class EnergyOptimizer {
    public static final String VERSION = "energy-optimizer/1.0.0";
    private final EnergyFeatureModel model = new EnergyFeatureModel();
    private final EnergySimulation simulation = new EnergySimulation();

    public record Constraint(String code, String status, Object actual, Object limit, String unit) { }

    public record Metrics(double initialInvestmentEUR, double annualizedCostEUR, double selfSufficiency,
                          double annualDemandKWh, double gridImportKWh, double gridExportKWh,
                          double onSiteSolarUseKWh, double unmetDemandKWh, double inverterClippingKWh,
                          double terminalDiscardedEnergyKWh) { }

    public record Candidate(EnergyConfiguration configuration, boolean feasible,
                            List<Constraint> constraints, Metrics metrics) {
        public Candidate {
            Objects.requireNonNull(configuration);
            constraints = List.copyOf(constraints);
            Objects.requireNonNull(metrics);
        }
    }

    public record Result(List<Candidate> candidates, List<String> ranking,
                         String recommendedConfigurationId, String status, String optimalityStatus) {
        public Result {
            candidates = List.copyOf(candidates);
            ranking = List.copyOf(ranking);
        }

        public long feasibleCandidateCount() {
            return ranking.size();
        }
    }

    public Result optimize(EnergyOptimizationRequest request, EnergyCatalog catalog, EnergyProfile profile) {
        Objects.requireNonNull(request, "request must not be null");
        Objects.requireNonNull(catalog, "catalog must not be null");
        Objects.requireNonNull(profile, "profile must not be null");
        if (!request.dataset().datasetId().equals(profile.datasetId())
                || !request.dataset().version().equals(profile.version())
                || !catalog.catalogVersion().equals(profile.version())) {
            throw new IllegalArgumentException("request, profile, and catalog must pin the same publication version");
        }
        var configurations = model.generate(request, catalog);
        var hours = profile.simulationHours(request.site().annualDemandKWh());
        double recoveryFactor = capitalRecoveryFactor(request.assumptions().discountRate(),
                request.assumptions().analysisYears());
        double investmentLimit = request.constraints().maxInitialInvestmentEUR();
        double sufficiencyLimit = request.constraints().minSelfSufficiency();
        var candidates = new ArrayList<Candidate>(configurations.size());
        for (var configuration : configurations) {
            var battery = configuration.battery();
            var simulationBattery = battery == null ? null : new EnergySimulation.Battery(
                    configuration.batteryQuantity(), battery.usableCapacityKWh(),
                    battery.maxChargePowerKW(), battery.maxDischargePowerKW(),
                    battery.roundTripEfficiencyFraction());
            var input = new EnergySimulation.Input(configuration.installedPvKWp(),
                    configuration.inverter().efficiencyFraction(), configuration.inverter().acPowerKW(),
                    simulationBattery, hours);
            var totals = simulation.simulate(input, false).totals();
            if (totals.netGridCostEUR() == null || Math.abs(totals.unmetDemandKWh()) > 1e-6) {
                throw new IllegalArgumentException("optimization requires tariffs and fully served demand");
            }
            double investment = configuration.initialInvestmentEUR();
            double annualizedCost = investment * recoveryFactor + totals.netGridCostEUR();
            boolean investmentPasses = investment <= investmentLimit + EnergyFeatureModel.COMPARISON_TOLERANCE;
            boolean sufficiencyPasses = totals.selfSufficiency() >= sufficiencyLimit
                    - EnergyFeatureModel.COMPARISON_TOLERANCE;
            var constraints = List.of(
                    new Constraint("roof_area", "PASS", round(configuration.usedRoofAreaM2(), 6),
                            request.site().usableRoofAreaM2(), "m2"),
                    new Constraint("dc_ac_ratio", "PASS", round(configuration.dcAcRatio(), 9),
                            "0.8..1.5", null),
                    new Constraint("max_initial_investment", investmentPasses ? "PASS" : "FAIL",
                            round(investment, 2), investmentLimit, "EUR"),
                    new Constraint("min_self_sufficiency", sufficiencyPasses ? "PASS" : "FAIL",
                            round(totals.selfSufficiency(), 9), sufficiencyLimit, null));
            var metrics = new Metrics(round(investment, 2), round(annualizedCost, 2),
                    round(totals.selfSufficiency(), 9), round(totals.annualDemandKWh(), 6),
                    round(totals.gridImportKWh(), 6), round(totals.gridExportKWh(), 6),
                    round(totals.onSiteSolarUseKWh(), 6), round(totals.unmetDemandKWh(), 6),
                    round(totals.inverterClippingKWh(), 6),
                    round(totals.terminalDiscardedEnergyKWh(), 6));
            candidates.add(new Candidate(configuration, investmentPasses && sufficiencyPasses,
                    constraints, metrics));
        }
        var ranking = candidates.stream().filter(Candidate::feasible)
                .sorted(Comparator.comparingDouble((Candidate c) -> c.metrics().annualizedCostEUR())
                        .thenComparingDouble(c -> c.metrics().initialInvestmentEUR())
                        .thenComparing(c -> c.configuration().id()))
                .map(c -> c.configuration().id()).toList();
        boolean feasible = !ranking.isEmpty();
        return new Result(candidates, ranking, feasible ? ranking.getFirst() : null,
                feasible ? "SUCCEEDED" : "INFEASIBLE", feasible ? "PROVEN_OPTIMAL" : "NOT_APPLICABLE");
    }

    public static double capitalRecoveryFactor(double rate, int years) {
        EnergyValidation.nonNegative(rate, "discount rate");
        if (years < 1) {
            throw new IllegalArgumentException("analysis years must be positive");
        }
        if (rate == 0) {
            return 1.0 / years;
        }
        double growth = Math.pow(1 + rate, years);
        return rate * growth / (growth - 1);
    }

    /** Match Python's round(binary64, digits), including exact half-even boundaries. */
    public static double round(double value, int digits) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("result must be finite");
        }
        return new BigDecimal(value).setScale(digits, RoundingMode.HALF_EVEN).doubleValue();
    }
}
