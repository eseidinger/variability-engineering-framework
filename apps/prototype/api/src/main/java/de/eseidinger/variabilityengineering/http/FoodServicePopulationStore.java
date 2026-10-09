package de.eseidinger.variabilityengineering.http;

import de.eseidinger.variabilityengineering.adapter.foodservice.FoodServiceFixtureAdapter;
import de.eseidinger.variabilityengineering.core.AnalysisPopulation;
import de.eseidinger.variabilityengineering.persistence.JdbcPopulationRepository;
import io.smallrye.config.ConfigMapping;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/** Fixture-backed population source persisted by immutable population ID and version. */
@ApplicationScoped
public class FoodServicePopulationStore {

    @ConfigMapping(prefix = "variability.fixture")
    public interface FixtureConfig {
        String directory();
    }

    @Inject
    FixtureConfig fixtureConfig;

    @Inject
    JdbcPopulationRepository repository;

    private AnalysisPopulation population;
    private List<FoodServiceFixtureAdapter.RejectedRecord> rejectedRecords;

    @PostConstruct
    void loadFixture() {
        try {
            var imported = new FoodServiceFixtureAdapter().importFixture(Path.of(fixtureConfig.directory()));
            var persisted = repository.saveIfAbsent(imported.population(), imported.rejectedRecords());
            population = persisted.population();
            rejectedRecords = persisted.rejectedRecords();
        } catch (IOException exception) {
            throw new IllegalStateException("cannot load food-service fixture", exception);
        }
    }

    public AnalysisPopulation population(String populationId) {
        if (!population.id().equals(populationId)) {
            throw new NotFoundException("unknown population: " + populationId);
        }
        return population;
    }

    public List<FoodServiceFixtureAdapter.RejectedRecord> rejectedRecords(String populationId) {
        population(populationId);
        return rejectedRecords;
    }

    public boolean isReady() {
        return population != null && repository.contains(population.id(), population.version());
    }
}
