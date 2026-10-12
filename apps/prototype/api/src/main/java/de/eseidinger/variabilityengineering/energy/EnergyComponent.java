package de.eseidinger.variabilityengineering.energy;

import java.net.URI;
import java.time.Instant;
import java.util.Objects;

/** Energy-profile attributes with explicit units; no transport or database dependencies. */
public sealed interface EnergyComponent {
    enum Type { PV_MODULE, INVERTER, BATTERY }

    Metadata metadata();

    Type type();

    default String id() {
        return metadata().componentId();
    }

    record Source(String owner, URI reference, String version, String license, Instant retrievedAt) {
        public Source {
            EnergyValidation.text(owner, 200, "source owner");
            Objects.requireNonNull(reference, "source reference must not be null");
            if (!reference.isAbsolute()) {
                throw new IllegalArgumentException("source reference must be an absolute URI");
            }
            if (version != null) {
                EnergyValidation.text(version, 100, "source version");
            }
            EnergyValidation.text(license, 200, "source license");
            Objects.requireNonNull(retrievedAt, "source retrieval time must not be null");
        }
    }

    record Metadata(String componentId, String displayName, double unitPriceEUR, boolean available, Source source) {
        public Metadata {
            EnergyValidation.id(componentId, "component ID");
            EnergyValidation.text(displayName, 200, "display name");
            EnergyValidation.nonNegative(unitPriceEUR, "unitPriceEUR");
            Objects.requireNonNull(source, "component source must not be null");
        }
    }

    record PvModule(Metadata metadata, double ratedPowerKWp, double areaM2) implements EnergyComponent {
        public PvModule {
            Objects.requireNonNull(metadata, "component metadata must not be null");
            EnergyValidation.positive(ratedPowerKWp, "ratedPowerKWp");
            EnergyValidation.positive(areaM2, "areaM2");
        }

        @Override
        public Type type() {
            return Type.PV_MODULE;
        }
    }

    record Inverter(Metadata metadata, double acPowerKW, double efficiencyFraction) implements EnergyComponent {
        public Inverter {
            Objects.requireNonNull(metadata, "component metadata must not be null");
            EnergyValidation.positive(acPowerKW, "acPowerKW");
            EnergyValidation.fraction(efficiencyFraction, true, "efficiencyFraction");
        }

        @Override
        public Type type() {
            return Type.INVERTER;
        }
    }

    record Battery(Metadata metadata, double usableCapacityKWh, double maxChargePowerKW,
                   double maxDischargePowerKW, double roundTripEfficiencyFraction) implements EnergyComponent {
        public Battery {
            Objects.requireNonNull(metadata, "component metadata must not be null");
            EnergyValidation.positive(usableCapacityKWh, "usableCapacityKWh");
            EnergyValidation.positive(maxChargePowerKW, "maxChargePowerKW");
            EnergyValidation.positive(maxDischargePowerKW, "maxDischargePowerKW");
            EnergyValidation.fraction(roundTripEfficiencyFraction, true, "roundTripEfficiencyFraction");
        }

        @Override
        public Type type() {
            return Type.BATTERY;
        }
    }
}
