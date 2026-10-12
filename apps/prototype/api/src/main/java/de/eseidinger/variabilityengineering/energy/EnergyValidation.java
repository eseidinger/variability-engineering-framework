package de.eseidinger.variabilityengineering.energy;

final class EnergyValidation {
    private EnergyValidation() {
    }

    static void text(String value, int maxLength, String field) {
        if (value == null || value.isBlank() || value.length() > maxLength) {
            throw new IllegalArgumentException(field + " must be non-blank and at most " + maxLength + " characters");
        }
    }

    static void id(String value, String field) {
        if (value == null || !value.matches("[a-z][a-z0-9-]{2,99}")) {
            throw new IllegalArgumentException(field + " must match the shared component/dataset ID contract");
        }
    }

    static void version(String value, String field) {
        if (value == null || !value.matches("(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)")) {
            throw new IllegalArgumentException(field + " must be a major.minor.patch version");
        }
    }

    static void nonNegative(double value, String field) {
        if (!Double.isFinite(value) || value < 0) {
            throw new IllegalArgumentException(field + " must be finite and non-negative");
        }
    }

    static void positive(double value, String field) {
        if (!Double.isFinite(value) || value <= 0) {
            throw new IllegalArgumentException(field + " must be finite and positive");
        }
    }

    static void fraction(double value, boolean positive, String field) {
        if (!Double.isFinite(value) || value > 1 || (positive ? value <= 0 : value < 0)) {
            throw new IllegalArgumentException(field + " must be in " + (positive ? "(0, 1]" : "[0, 1]"));
        }
    }
}
