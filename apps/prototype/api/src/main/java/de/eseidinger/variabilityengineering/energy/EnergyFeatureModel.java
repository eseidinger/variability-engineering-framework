package de.eseidinger.variabilityengineering.energy;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/** Bounded reference energy-model enumeration; simulation/customer feasibility follows later. */
public final class EnergyFeatureModel {
    public static final String VERSION = "energy-model/1.0.0";
    public static final int MAX_MODELS_PER_TYPE = 20;
    public static final int MAX_PV_MODULES = 100;
    public static final int MAX_BATTERIES = 4;
    public static final long MAX_CANDIDATE_EVALUATIONS = 100_000;
    public static final double COMPARISON_TOLERANCE = 1e-9;
    public static final double MIN_DC_AC_RATIO = 0.8;
    public static final double MAX_DC_AC_RATIO = 1.5;

    public long estimateCandidateEvaluations(EnergyOptimizationRequest request, EnergyCatalog catalog) {
        requireSupportedInputs(request, catalog);
        long pairs = 0;
        var inverters = catalog.availableModels(EnergyComponent.Inverter.class);
        for (var pv : catalog.availableModels(EnergyComponent.PvModule.class)) {
            int maxQuantity = maxPvQuantity(request.site().usableRoofAreaM2(), pv);
            for (int quantity = 1; quantity <= maxQuantity; quantity++) {
                for (var inverter : inverters) {
                    if (compatible(pv, quantity, inverter)) {
                        pairs = Math.addExact(pairs, 1);
                    }
                }
            }
        }
        long batteryOptions = 1L + MAX_BATTERIES * catalog.availableModels(EnergyComponent.Battery.class).size();
        return Math.multiplyExact(pairs, batteryOptions);
    }

    public List<EnergyConfiguration> generate(EnergyOptimizationRequest request, EnergyCatalog catalog) {
        long estimate = estimateCandidateEvaluations(request, catalog);
        if (estimate > MAX_CANDIDATE_EVALUATIONS) {
            throw new EnergyModelException("search_space_limit_exceeded",
                    "search requires " + estimate + " candidate evaluations; maximum is " + MAX_CANDIDATE_EVALUATIONS);
        }
        var candidates = new ArrayList<EnergyConfiguration>((int) estimate);
        var inverters = catalog.availableModels(EnergyComponent.Inverter.class);
        var batteries = catalog.availableModels(EnergyComponent.Battery.class);
        for (var pv : catalog.availableModels(EnergyComponent.PvModule.class)) {
            int maxQuantity = maxPvQuantity(request.site().usableRoofAreaM2(), pv);
            for (int quantity = 1; quantity <= maxQuantity; quantity++) {
                for (var inverter : inverters) {
                    if (!compatible(pv, quantity, inverter)) {
                        continue;
                    }
                    candidates.add(new EnergyConfiguration(pv, quantity, inverter, null, 0));
                    for (var battery : batteries) {
                        for (int batteryQuantity = 1; batteryQuantity <= MAX_BATTERIES; batteryQuantity++) {
                            candidates.add(new EnergyConfiguration(pv, quantity, inverter, battery, batteryQuantity));
                        }
                    }
                }
            }
        }
        candidates.sort(Comparator.comparing(EnergyConfiguration::id));
        var ids = new HashSet<String>();
        for (var candidate : candidates) {
            if (!ids.add(candidate.id())) {
                throw new EnergyModelException("configuration_id_collision",
                        "component IDs produce ambiguous configuration identities under the reference model");
            }
        }
        return List.copyOf(candidates);
    }

    private static int maxPvQuantity(double roofAreaM2, EnergyComponent.PvModule pv) {
        // Clamp as a double before converting: a large finite roof must not overflow an integer.
        return (int) Math.min(MAX_PV_MODULES, Math.floor((roofAreaM2 + COMPARISON_TOLERANCE) / pv.areaM2()));
    }

    private static boolean compatible(EnergyComponent.PvModule pv, int quantity, EnergyComponent.Inverter inverter) {
        double ratio = pv.ratedPowerKWp() * quantity / inverter.acPowerKW();
        return ratio >= MIN_DC_AC_RATIO - COMPARISON_TOLERANCE && ratio <= MAX_DC_AC_RATIO + COMPARISON_TOLERANCE;
    }

    private static void requireSupportedInputs(EnergyOptimizationRequest request, EnergyCatalog catalog) {
        Objects.requireNonNull(request, "request must not be null");
        Objects.requireNonNull(catalog, "catalog must not be null");
        if (!request.site().existingComponentIds().isEmpty()) {
            throw new EnergyModelException("existing_equipment_not_supported",
                    "existing equipment requires shared quantity, compatibility, and identity acceptance examples");
        }
    }
}
