package de.eseidinger.variabilityengineering.energy;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** One complete UTC calendar year of aligned, normalized publication data. */
public record EnergyProfile(String datasetId, String version, Instant start, Instant endExclusive,
                            List<Hour> hours) {
    private static final double LOAD_SHAPE_TOLERANCE = 1e-6;

    public record Hour(Instant start, double pvYieldPerKWp, double loadFraction,
                       double importTariffEURPerKWh, double exportTariffEURPerKWh) {
        public Hour {
            Objects.requireNonNull(start, "hour start must not be null");
            EnergyValidation.nonNegative(pvYieldPerKWp, "pvYieldPerKWp");
            EnergyValidation.nonNegative(loadFraction, "loadFraction");
            EnergyValidation.nonNegative(importTariffEURPerKWh, "importTariffEURPerKWh");
            EnergyValidation.nonNegative(exportTariffEURPerKWh, "exportTariffEURPerKWh");
        }
    }

    public EnergyProfile {
        EnergyValidation.id(datasetId, "dataset ID");
        EnergyValidation.version(version, "dataset version");
        Objects.requireNonNull(start, "period start must not be null");
        Objects.requireNonNull(endExclusive, "period end must not be null");
        int expectedHours = calendarYearHours(start, endExclusive);
        hours = List.copyOf(hours);
        if (hours.size() != expectedHours) {
            throw new IllegalArgumentException("profile must contain every hour of the declared UTC calendar year");
        }
        double[] fractions = new double[hours.size()];
        for (int i = 0; i < hours.size(); i++) {
            if (!hours.get(i).start().equals(start.plus(i, ChronoUnit.HOURS))) {
                throw new IllegalArgumentException("profile hours must match the declared UTC period");
            }
            fractions[i] = hours.get(i).loadFraction();
        }
        if (Math.abs(compensatedSum(fractions) - 1) > LOAD_SHAPE_TOLERANCE) {
            throw new IllegalArgumentException("reference load shape must sum to one");
        }
    }

    public static int calendarYearHours(Instant start, Instant endExclusive) {
        Objects.requireNonNull(start, "period start must not be null");
        Objects.requireNonNull(endExclusive, "period end must not be null");
        var utcStart = start.atOffset(ZoneOffset.UTC);
        if (utcStart.getDayOfYear() != 1 || !utcStart.toLocalTime().equals(LocalTime.MIDNIGHT)
                || !endExclusive.equals(utcStart.plusYears(1).toInstant())) {
            throw new IllegalArgumentException("energy profiles require one complete UTC calendar year");
        }
        return Math.toIntExact(ChronoUnit.HOURS.between(start, endExclusive));
    }

    public List<EnergySimulation.HourInput> simulationHours(double annualDemandKWh) {
        EnergyValidation.positive(annualDemandKWh, "annualDemandKWh");
        var inputs = new ArrayList<EnergySimulation.HourInput>(hours.size());
        for (var hour : hours) {
            inputs.add(new EnergySimulation.HourInput(hour.start(), hour.pvYieldPerKWp(),
                    annualDemandKWh * hour.loadFraction(), hour.importTariffEURPerKWh(),
                    hour.exportTariffEURPerKWh()));
        }
        return List.copyOf(inputs);
    }

    // The reference validator uses math.fsum for the load-shape invariant.
    private static double compensatedSum(double[] values) {
        double sum = 0, correction = 0;
        for (double value : values) {
            double adjusted = value - correction;
            double next = sum + adjusted;
            correction = (next - sum) - adjusted;
            sum = next;
        }
        return sum;
    }
}
