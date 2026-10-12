package de.eseidinger.variabilityengineering.core.configuration;

/** A quantitative selection, separate from the observed population's binary variant identity. */
public record ElementSelection(String elementId, int quantity, boolean existing) {
    public ElementSelection {
        if (elementId == null || elementId.isBlank()) {
            throw new IllegalArgumentException("element ID must be non-blank");
        }
        if (quantity < 1) {
            throw new IllegalArgumentException("selection quantity must be positive");
        }
    }
}
