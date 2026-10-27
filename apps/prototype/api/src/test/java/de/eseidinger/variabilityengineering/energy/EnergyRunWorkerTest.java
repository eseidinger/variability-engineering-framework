package de.eseidinger.variabilityengineering.energy;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.eseidinger.variabilityengineering.persistence.JdbcEnergyRunRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.ArgumentCaptor;

class EnergyRunWorkerTest {
    @Test
    void timeoutAndExecutionFailureAreFailedRunsNotInfeasibleAndHideDiagnostics() throws Exception {
        for (boolean timeout : new boolean[]{true, false}) {
            var worker = new EnergyRunWorker();
            worker.runs = mock(JdbcEnergyRunRepository.class);
            worker.mapper = new ObjectMapper();
            var run = new JdbcEnergyRunRepository.Run("run-worker-test", "prn_" + "a".repeat(32), "{}",
                    "energy-reference-wasserburg-am-inn-2024", "1.0.0", "a".repeat(64), "RUNNING",
                    Instant.now(), Instant.now(), null, null, null, UUID.randomUUID());
            var claim = new JdbcEnergyRunRepository.Claimed(run, run.claimId());
            @SuppressWarnings("unchecked") // Future's erased class token is used only as a controlled test double.
            Future<EnergyRunWorker.Output> task = mock(Future.class);
            if (timeout) when(task.get(300, TimeUnit.SECONDS)).thenThrow(new TimeoutException("secret diagnostic"));
            else when(task.get(300, TimeUnit.SECONDS)).thenThrow(new ExecutionException(new IllegalStateException("secret diagnostic")));
            worker.await(claim, task);
            var error = ArgumentCaptor.forClass(String.class);
            verify(worker.runs).complete(eq(claim), eq("FAILED"), isNull(), error.capture(), eq(List.of()));
            assertTrue(error.getValue().contains(timeout ? "run_timeout" : "run_failed"));
            assertFalse(error.getValue().contains("secret diagnostic"));
            if (timeout) verify(task).cancel(true);
            worker.stop();
        }
    }
}
