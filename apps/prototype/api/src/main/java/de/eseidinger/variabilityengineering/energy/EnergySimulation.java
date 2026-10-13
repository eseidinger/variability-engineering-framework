package de.eseidinger.variabilityengineering.energy;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Pure hourly AC-side dispatch. All calculations retain binary64 precision. */
public final class EnergySimulation {
    public static final String VERSION = "energy-simulation/1.0.0";
    private static final double ENERGY_TOLERANCE = 1e-6;

    public record HourInput(Instant start, double pvYieldPerKWp, double loadKWh,
                            Double importTariffEURPerKWh, Double exportTariffEURPerKWh) {
        public HourInput {
            Objects.requireNonNull(start, "hour start must not be null");
            EnergyValidation.nonNegative(pvYieldPerKWp, "pvYieldPerKWp");
            EnergyValidation.nonNegative(loadKWh, "loadKWh");
            if ((importTariffEURPerKWh == null) != (exportTariffEURPerKWh == null)) {
                throw new IllegalArgumentException("both tariffs must be supplied together");
            }
            if (importTariffEURPerKWh != null) {
                EnergyValidation.nonNegative(importTariffEURPerKWh, "importTariffEURPerKWh");
                EnergyValidation.nonNegative(exportTariffEURPerKWh, "exportTariffEURPerKWh");
            }
        }
    }

    public record Battery(int quantity, double usableCapacityKWh, double maxChargePowerKW,
                          double maxDischargePowerKW, double roundTripEfficiencyFraction) {
        public Battery {
            if (quantity < 1 || quantity > EnergyFeatureModel.MAX_BATTERIES) {
                throw new IllegalArgumentException("battery quantity must be 1..4");
            }
            EnergyValidation.positive(usableCapacityKWh, "usableCapacityKWh");
            EnergyValidation.positive(maxChargePowerKW, "maxChargePowerKW");
            EnergyValidation.positive(maxDischargePowerKW, "maxDischargePowerKW");
            EnergyValidation.fraction(roundTripEfficiencyFraction, true, "roundTripEfficiencyFraction");
        }
    }

    public record Input(double pvCapacityKWp, double inverterEfficiencyFraction,
                        double inverterAcPowerKW, Battery battery, List<HourInput> hours) {
        public Input {
            EnergyValidation.positive(pvCapacityKWp, "pvCapacityKWp");
            EnergyValidation.fraction(inverterEfficiencyFraction, true, "inverterEfficiencyFraction");
            EnergyValidation.positive(inverterAcPowerKW, "inverterAcPowerKW");
            hours = List.copyOf(hours);
            if (hours.isEmpty()) {
                throw new IllegalArgumentException("simulation requires hours");
            }
            boolean hasTariffs = hours.getFirst().importTariffEURPerKWh() != null;
            for (int i = 0; i < hours.size(); i++) {
                if ((hours.get(i).importTariffEURPerKWh() != null) != hasTariffs) {
                    throw new IllegalArgumentException("tariffs must be present for every hour or no hours");
                }
                if (i > 0 && !hours.get(i).start().equals(hours.get(i - 1).start().plus(1, ChronoUnit.HOURS))) {
                    throw new IllegalArgumentException("simulation hours must be consecutive UTC hours");
                }
            }
        }
    }

    public record HourResult(Instant start, double pvDcKWh, double pvAcKWh, double loadKWh,
                             double directSolarKWh, double batteryChargeAcKWh,
                             double batteryDischargeAcKWh, double stateOfChargeKWh,
                             double gridImportKWh, double gridExportKWh, double inverterClippingKWh) { }

    public record Totals(double pvDcKWh, double pvAcKWh, double annualDemandKWh,
                         double gridImportKWh, double gridExportKWh, double onSiteSolarUseKWh,
                         double batteryChargeAcKWh, double batteryDischargeAcKWh,
                         double inverterClippingKWh, double unmetDemandKWh,
                         double terminalDiscardedEnergyKWh, double selfSufficiency,
                         Double netGridCostEUR) { }

    public record Result(List<HourResult> hours, Totals totals) {
        public Result {
            hours = List.copyOf(hours);
            Objects.requireNonNull(totals);
        }
    }

    public Result simulate(Input input, boolean includeHours) {
        Objects.requireNonNull(input, "simulation input must not be null");
        var battery = input.battery();
        double capacity = battery == null ? 0 : battery.usableCapacityKWh() * battery.quantity();
        double chargeLimit = battery == null ? 0 : battery.maxChargePowerKW() * battery.quantity();
        double dischargeLimit = battery == null ? 0 : battery.maxDischargePowerKW() * battery.quantity();
        double efficiency = battery == null ? 1 : Math.sqrt(battery.roundTripEfficiencyFraction());
        EnergyValidation.nonNegative(capacity, "total battery capacity");
        EnergyValidation.nonNegative(chargeLimit, "total battery charge power");
        EnergyValidation.nonNegative(dischargeLimit, "total battery discharge power");

        double stateOfCharge = 0, pvDcTotal = 0, pvAcTotal = 0, demandTotal = 0;
        double importTotal = 0, exportTotal = 0, solarUseTotal = 0;
        double chargeTotal = 0, dischargeTotal = 0, clippingTotal = 0, netGridCost = 0;
        boolean hasTariffs = input.hours().getFirst().importTariffEURPerKWh() != null;
        var resultHours = includeHours ? new ArrayList<HourResult>(input.hours().size()) : new ArrayList<HourResult>(0);
        for (var hour : input.hours()) {
            double pvDc = input.pvCapacityKWp() * hour.pvYieldPerKWp();
            double potentialAc = pvDc * input.inverterEfficiencyFraction();
            double pvAc = Math.min(potentialAc, input.inverterAcPowerKW());
            double clipping = Math.max(0, potentialAc - input.inverterAcPowerKW());
            double directSolar = Math.min(pvAc, hour.loadKWh());
            double surplus = pvAc - directSolar;
            double deficit = hour.loadKWh() - directSolar;
            double charge = Math.min(Math.min(surplus, chargeLimit), Math.max(0, capacity - stateOfCharge) / efficiency);
            stateOfCharge += charge * efficiency;
            double export = surplus - charge;
            double discharge = Math.min(Math.min(deficit, dischargeLimit), stateOfCharge * efficiency);
            stateOfCharge -= discharge / efficiency;
            double gridImport = deficit - discharge;
            if (hasTariffs) {
                netGridCost += gridImport * hour.importTariffEURPerKWh()
                        - export * hour.exportTariffEURPerKWh();
            }
            double balanceError = Math.abs(pvAc + gridImport + discharge - hour.loadKWh() - charge - export);
            if (balanceError > ENERGY_TOLERANCE || !Double.isFinite(balanceError)
                    || stateOfCharge < -ENERGY_TOLERANCE || stateOfCharge > capacity + ENERGY_TOLERANCE) {
                throw new IllegalArgumentException("hourly energy conservation or battery capacity violated");
            }
            if (includeHours) {
                resultHours.add(new HourResult(hour.start(), pvDc, pvAc, hour.loadKWh(), directSolar,
                        charge, discharge, stateOfCharge, gridImport, export, clipping));
            }
            pvDcTotal += pvDc;
            pvAcTotal += pvAc;
            demandTotal += hour.loadKWh();
            importTotal += gridImport;
            exportTotal += export;
            solarUseTotal += directSolar + discharge;
            chargeTotal += charge;
            dischargeTotal += discharge;
            clippingTotal += clipping;
        }
        EnergyValidation.positive(demandTotal, "annual demand");
        for (double value : new double[] {pvDcTotal, pvAcTotal, importTotal, exportTotal,
                solarUseTotal, chargeTotal, dischargeTotal, clippingTotal, netGridCost}) {
            if (!Double.isFinite(value)) {
                throw new IllegalArgumentException("simulation aggregate must be finite");
            }
        }
        return new Result(resultHours, new Totals(pvDcTotal, pvAcTotal, demandTotal, importTotal,
                exportTotal, solarUseTotal, chargeTotal, dischargeTotal, clippingTotal, 0,
                stateOfCharge, 1 - importTotal / demandTotal, hasTariffs ? netGridCost : null));
    }
}
