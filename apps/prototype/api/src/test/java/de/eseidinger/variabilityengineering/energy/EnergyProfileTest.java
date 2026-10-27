package de.eseidinger.variabilityengineering.energy;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EnergyProfileTest {
    @Test
    void acceptsCompleteOrdinaryAndLeapYearsIncludingCenturyBoundaries() {
        for (int year : new int[] {1900, 2000, 2023, 2024, 2100}) {
            var start = Instant.parse(year + "-01-01T00:00:00Z");
            var end = Instant.parse((year + 1) + "-01-01T00:00:00Z");
            int count = (int) ChronoUnit.HOURS.between(start, end);
            var hours = new ArrayList<EnergyProfile.Hour>();
            for (int i = 0; i < count; i++) {
                hours.add(new EnergyProfile.Hour(start.plus(i, ChronoUnit.HOURS), 0, 1.0 / count, 0.3, 0.08));
            }
            var profile = new EnergyProfile("energy-calendar-test", "1.0.0", start, end, hours);
            assertEquals(year == 2000 || year == 2024 ? 8784 : 8760, profile.hours().size());
        }
    }

    @Test
    void rejectsPartialRollingShiftedAndMultipleYearsBeforeOptimization() {
        var start = Instant.parse("2024-01-01T00:00:00Z");
        var end = Instant.parse("2025-01-01T00:00:00Z");
        for (var period : List.of(
                List.of(start, start.plus(1, ChronoUnit.HOURS)),
                List.of(start, start.plus(8760, ChronoUnit.HOURS)),
                List.of(start, end.minus(1, ChronoUnit.SECONDS)),
                List.of(start.plus(1, ChronoUnit.HOURS), end.plus(1, ChronoUnit.HOURS)),
                List.of(start.minus(1, ChronoUnit.HOURS), end.minus(1, ChronoUnit.HOURS)),
                List.of(start.plusNanos(1), end.plusNanos(1)),
                List.of(Instant.parse("2023-02-01T00:00:00Z"), Instant.parse("2024-02-01T00:00:00Z")),
                List.of(start, Instant.parse("2026-01-01T00:00:00Z")),
                List.of(end, start))) {
            assertThrows(IllegalArgumentException.class, () -> EnergyProfile.calendarYearHours(period.get(0), period.get(1)));
        }
        assertThrows(IllegalArgumentException.class, () -> new EnergyProfile("energy-calendar-test", "1.0.0",
                start, start.plus(1, ChronoUnit.HOURS), List.of(new EnergyProfile.Hour(start, 0, 1, 0.3, 0.08))));
    }

    @Test
    void rejectsIncompleteAnnualSeriesEvenWhenLoadFractionsSumToOne() {
        var start = Instant.parse("2024-01-01T00:00:00Z");
        assertThrows(IllegalArgumentException.class, () -> new EnergyProfile("energy-calendar-test", "1.0.0",
                start, Instant.parse("2025-01-01T00:00:00Z"), List.of(new EnergyProfile.Hour(start, 0, 1, 0.3, 0.08))));
    }
}
