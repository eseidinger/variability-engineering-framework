package de.eseidinger.variabilityengineering.energy;

import de.eseidinger.variabilityengineering.core.configuration.ElementSelection;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Complete new-equipment selection; identity follows the kit's versioned energy model. */
public record EnergyConfiguration(EnergyComponent.PvModule pvModule, int pvQuantity,
                                  EnergyComponent.Inverter inverter,
                                  EnergyComponent.Battery battery, int batteryQuantity) {
    public EnergyConfiguration {
        Objects.requireNonNull(pvModule, "PV module must not be null");
        Objects.requireNonNull(inverter, "inverter must not be null");
        if (pvQuantity < 1 || pvQuantity > EnergyFeatureModel.MAX_PV_MODULES) {
            throw new IllegalArgumentException("PV quantity must be 1..100");
        }
        if ((battery == null && batteryQuantity != 0)
                || (battery != null && (batteryQuantity < 1 || batteryQuantity > EnergyFeatureModel.MAX_BATTERIES))) {
            throw new IllegalArgumentException("battery selection requires no battery or 1..4 units of one model");
        }
        if (!pvModule.metadata().available() || !inverter.metadata().available()
                || (battery != null && !battery.metadata().available())) {
            throw new IllegalArgumentException("configuration components must be available");
        }
        String id = configurationId(pvModule, pvQuantity, inverter, battery, batteryQuantity);
        if (id.length() > 128) {
            throw new EnergyModelException("configuration_id_limit_exceeded",
                    "selected component IDs exceed the shared configuration ID length bound");
        }
        EnergyValidation.positive(pvModule.ratedPowerKWp() * pvQuantity, "installed PV capacity");
        EnergyValidation.positive(pvModule.ratedPowerKWp() * pvQuantity / inverter.acPowerKW(), "DC/AC ratio");
        EnergyValidation.positive(pvModule.areaM2() * pvQuantity, "used roof area");
        double investment = pvModule.metadata().unitPriceEUR() * pvQuantity + inverter.metadata().unitPriceEUR();
        if (battery != null) {
            EnergyValidation.positive(battery.usableCapacityKWh() * batteryQuantity, "total battery capacity");
            EnergyValidation.positive(battery.maxChargePowerKW() * batteryQuantity, "total battery charge power");
            EnergyValidation.positive(battery.maxDischargePowerKW() * batteryQuantity, "total battery discharge power");
            investment += battery.metadata().unitPriceEUR() * batteryQuantity;
        }
        EnergyValidation.nonNegative(investment, "initial investment");
    }

    public String id() {
        return configurationId(pvModule, pvQuantity, inverter, battery, batteryQuantity);
    }

    public double installedPvKWp() {
        return pvModule.ratedPowerKWp() * pvQuantity;
    }

    public double usedRoofAreaM2() {
        return pvModule.areaM2() * pvQuantity;
    }

    public double dcAcRatio() {
        return installedPvKWp() / inverter.acPowerKW();
    }

    public List<ElementSelection> selections() {
        var selections = new ArrayList<ElementSelection>();
        selections.add(new ElementSelection(pvModule.id(), pvQuantity, false));
        selections.add(new ElementSelection(inverter.id(), 1, false));
        if (battery != null) {
            selections.add(new ElementSelection(battery.id(), batteryQuantity, false));
        }
        return List.copyOf(selections);
    }

    public double initialInvestmentEUR() {
        double investment = pvModule.metadata().unitPriceEUR() * pvQuantity + inverter.metadata().unitPriceEUR();
        if (battery != null) {
            investment += battery.metadata().unitPriceEUR() * batteryQuantity;
        }
        return investment;
    }

    private static String configurationId(EnergyComponent.PvModule pv, int quantity,
                                          EnergyComponent.Inverter inverter, EnergyComponent.Battery battery,
                                          int batteryQuantity) {
        String batteryPart = battery == null ? "battery-none" : battery.id() + "-q" + batteryQuantity;
        return pv.id() + "-q" + quantity + "-" + inverter.id() + "-" + batteryPart;
    }
}
