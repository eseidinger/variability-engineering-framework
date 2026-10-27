package de.eseidinger.variabilityengineering.adapter.odip;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/** Last observed dependency state on private management metrics, never readiness. */
@ApplicationScoped
public class OdipDependencyState {
    public enum State { NOT_CHECKED, AVAILABLE, UNAVAILABLE, DENIED, INVALID, NOT_CONFIGURED }
    private final AtomicReference<State> state = new AtomicReference<>(State.NOT_CHECKED);
    private final AtomicLong observedAt = new AtomicLong();
    private final AtomicLong fallbacks = new AtomicLong();

    @Inject
    public OdipDependencyState(MeterRegistry registry) {
        for (State value : State.values()) {
            registry.gauge("energy.odip.state", java.util.List.of(io.micrometer.core.instrument.Tag.of("state", value.name())),
                    state, current -> current.get() == value ? 1 : 0);
        }
        registry.gauge("energy.odip.observed.epoch.seconds", observedAt);
        registry.gauge("energy.odip.cache.fallbacks", fallbacks);
    }

    public void observe(State value, boolean fallback) {
        state.set(value);
        observedAt.set(Instant.now().getEpochSecond());
        if (fallback) fallbacks.incrementAndGet();
    }

    public State state() { return state.get(); }
    public long fallbackCount() { return fallbacks.get(); }
}
