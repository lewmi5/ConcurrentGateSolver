package cp2024.solution;

import cp2024.circuit.Circuit;
import cp2024.circuit.CircuitSolver;
import cp2024.circuit.CircuitValue;

import java.util.concurrent.*;


public class ParallelCircuitSolver implements CircuitSolver {
    private final ExecutorService pool;
    private boolean stopped;

    public ParallelCircuitSolver() {
        pool = Executors.newCachedThreadPool();
        this.stopped = false;
    }
    
    @Override
    public CircuitValue solve(Circuit c) {
        if(stopped) {
            return new StoppedCircuitValue();
        }

        BlockingQueue<Boolean> result = new LinkedBlockingQueue<>();
        ParallelCircuitValue retValue = new ParallelCircuitValue(c.getRoot(), pool, result);
        pool.submit(retValue);

        return retValue;
    }

    @Override
    public void stop() {
        stopped = true;
        pool.shutdownNow();
    }
}
