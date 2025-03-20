package cp2024.solution;

import cp2024.circuit.Circuit;
import cp2024.circuit.CircuitSolver;
import cp2024.circuit.CircuitValue;

import java.util.concurrent.*;

// Class responsible for solving circuits in parallel
public class ParallelCircuitSolver implements CircuitSolver {
    private final ExecutorService pool;
    private boolean stopped;

    // Constructor initializing a thread pool for parallel execution
    public ParallelCircuitSolver() {
        pool = Executors.newCachedThreadPool();
        this.stopped = false;
    }
    
    @Override
    public CircuitValue solve(Circuit c) {
        if(stopped) {
            return new StoppedCircuitValue(); // Return a special value if stopped
        }

        BlockingQueue<Boolean> result = new LinkedBlockingQueue<>();
        ParallelCircuitValue retValue = new ParallelCircuitValue(c.getRoot(), pool, result);
        pool.submit(retValue); // Execute circuit evaluation in a separate thread

        return retValue;
    }

    @Override
    public void stop() {
        stopped = true;
        pool.shutdownNow(); // Stop all running tasks
    }
}
