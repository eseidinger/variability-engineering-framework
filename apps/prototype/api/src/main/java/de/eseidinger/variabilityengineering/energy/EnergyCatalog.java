package de.eseidinger.variabilityengineering.energy;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/** One immutable catalog version, including unavailable models and source evidence. */
public record EnergyCatalog(String schemaVersion, String catalogVersion, String currency,
                            List<EnergyComponent> components) {
    public static final String SCHEMA_VERSION = "energy-component-catalog/v1";

    public EnergyCatalog {
        if (!SCHEMA_VERSION.equals(schemaVersion)) {
            throw new IllegalArgumentException("unsupported component catalog schema version");
        }
        EnergyValidation.version(catalogVersion, "catalog version");
        if (!"EUR".equals(currency)) {
            throw new IllegalArgumentException("energy catalog currency must be EUR");
        }
        Objects.requireNonNull(components, "catalog components must not be null");
        components = components.stream().sorted(Comparator.comparing(EnergyComponent::id)).toList();
        var ids = new HashSet<String>();
        for (var component : components) {
            if (!ids.add(component.id())) {
                throw new IllegalArgumentException("component IDs must be unique across the catalog");
            }
        }
        for (var type : EnergyComponent.Type.values()) {
            long count = components.stream().filter(component -> component.type() == type).count();
            if (count < 1 || count > EnergyFeatureModel.MAX_MODELS_PER_TYPE) {
                throw new IllegalArgumentException("catalog must contain 1..20 models of each type: " + type);
            }
        }
    }

    public <T extends EnergyComponent> List<T> availableModels(Class<T> type) {
        return components.stream().filter(type::isInstance).map(type::cast)
                .filter(component -> component.metadata().available()).toList();
    }
}
