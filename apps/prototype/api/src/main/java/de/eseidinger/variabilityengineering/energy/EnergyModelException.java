package de.eseidinger.variabilityengineering.energy;

/** A model-bound failure that a future HTTP adapter can map to a machine-readable error. */
public final class EnergyModelException extends IllegalArgumentException {
    private final String code;

    public EnergyModelException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
