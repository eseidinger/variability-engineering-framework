package de.eseidinger.variabilityengineering;

import de.eseidinger.variabilityengineering.http.FoodServicePopulationStore;
import de.eseidinger.variabilityengineering.persistence.JdbcEnergyRunRepository;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.ServiceUnavailableException;
import jakarta.ws.rs.core.MediaType;

@Path("/api/status")
public class StatusResource {

    @Inject
    FoodServicePopulationStore store;

    @Inject
    JdbcEnergyRunRepository energyRuns;

    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String status() {
        if (!store.isReady()) {
            throw new ServiceUnavailableException("food-service population is not ready");
        }
        if (!energyRuns.isReady()) {
            throw new ServiceUnavailableException("energy run store is not ready");
        }
        return "Variability Engineering API and population store are ready";
    }
}
