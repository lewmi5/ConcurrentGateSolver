package cp2024.solution;

import cp2024.circuit.CircuitNode;
import cp2024.circuit.CircuitValue;
import cp2024.circuit.LeafNode;
import cp2024.circuit.ThresholdNode;

import java.util.ArrayList;
import java.util.concurrent.*;

// Class representing the parallel execution of a circuit node
public class ParallelCircuitValue implements CircuitValue, Runnable {
    private final ExecutorService pool;
    private final CircuitNode node;
    private final Semaphore isFree = new Semaphore(0); // Synchronization semaphore

    private boolean evaluation;
    private final BlockingQueue<Boolean> resultsInParent; // Parent node result queue

    private final ArrayList<Future<?>> futures = new ArrayList<>(); // Stores future tasks
    private final ArrayList<ParallelCircuitValue> tasks = new ArrayList<>(); // Child tasks
    private final BlockingQueue<Boolean> resultsFromSons = new LinkedBlockingQueue<>(); // Child results queue

    ParallelCircuitValue(CircuitNode node, ExecutorService pool, BlockingQueue<Boolean> resultsQueue) {
        this.node = node;
        this.pool = pool;
        this.resultsInParent = resultsQueue;
    }

    // Returns the computed value of the circuit node
    @Override
    public boolean getValue() throws InterruptedException {
        isFree.acquire(); // Wait until computation is done
        boolean result = evaluation;
        isFree.release(); // Release semaphore for next access
        return result;
    }

    @Override
    public void run() {
        try {
            switch (node.getType()) {
                case IF -> evaluateIF(node);
                case AND -> evaluateAND(node);
                case OR -> evaluateOR(node);
                case NOT -> evaluateNOT(node);
                case GT -> evaluateGT((ThresholdNode) node);
                case LT -> evaluateLT((ThresholdNode) node);
                case LEAF -> evaluateSLEEPYLEAFNODE((LeafNode) node);
                default -> throw new RuntimeException("Illegal type " + node.getType());
            }
            isFree.release(); // Mark completion
        } catch (InterruptedException | ExecutionException e) {
            handleInterruption();
            return;
        }
    }

    // Creates tasks for each child node
    private void createTasksFromArgs() throws InterruptedException {
        for (CircuitNode toAdd : node.getArgs()) {
            tasks.add(new ParallelCircuitValue(toAdd, pool, resultsFromSons));
        }
    }

    // Submits tasks for execution
    private void submitTasks() {
        for (ParallelCircuitValue task : tasks) {
            futures.add(pool.submit(task));
        }
    }

    // Handles thread interruption
    private void handleInterruption() {
        stopChildren();
        isFree.release();
    }

    // Stops execution of all child tasks
    private void stopChildren() {
        for (Future<?> f : futures) {
            f.cancel(true);
        }
    }

    // Evaluates an AND gate in parallel
    private void evaluateAND(CircuitNode node) throws InterruptedException {
        createTasksFromArgs();
        submitTasks();

        evaluation = true;
        int subtreesEvaluated = 0;
        while (evaluation && subtreesEvaluated < node.getArgs().length) {
            if (!resultsFromSons.take()) {
                evaluation = false;
                break;
            }
            subtreesEvaluated++;
        }
        stopChildren();
        resultsInParent.put(evaluation);
    }

    // Evaluates an OR gate in parallel
    private void evaluateOR(CircuitNode node) throws InterruptedException {
        createTasksFromArgs();
        submitTasks();

        evaluation = false;
        int subtreesEvaluated = 0;
        while (!evaluation && subtreesEvaluated < node.getArgs().length) {
            if (resultsFromSons.take()) {
                evaluation = true;
                break;
            }
            subtreesEvaluated++;
        }
        stopChildren();
        resultsInParent.put(evaluation);
    }

    // Evaluates a NOT gate in parallel
    private void evaluateNOT(CircuitNode node) throws InterruptedException {
        createTasksFromArgs();
        submitTasks();

        evaluation = !resultsFromSons.take();
        resultsInParent.put(evaluation);
    }

    // Evaluates an IF condition node in parallel
    private void evaluateIF(CircuitNode node) throws InterruptedException, ExecutionException {
        BlockingQueue<Boolean> result_a = new LinkedBlockingQueue<>();
        BlockingQueue<Boolean> result_b = new LinkedBlockingQueue<>();
        BlockingQueue<Boolean> result_c = new LinkedBlockingQueue<>();

        Future<?> f_a = pool.submit(new ParallelCircuitValue(node.getArgs()[0], pool, result_a));
        Future<?> f_b = pool.submit(new ParallelCircuitValue(node.getArgs()[1], pool, result_b));
        Future<?> f_c = pool.submit(new ParallelCircuitValue(node.getArgs()[2], pool, result_c));

        f_a.get();
        if (result_a.take()) {
            f_c.cancel(true);
            f_b.get();
            evaluation = result_b.take();
        } else {
            f_b.cancel(true);
            f_c.get();
            evaluation = result_c.take();
        }
        resultsInParent.put(evaluation);
    }

    // Evaluates a leaf node (final value node)
    private void evaluateSLEEPYLEAFNODE(LeafNode node) throws InterruptedException {
        evaluation = node.getValue();
        resultsInParent.put(evaluation);
    }

    // Evaluates a GT (greater than threshold) node
    private void evaluateGT(ThresholdNode node) throws InterruptedException {
        createTasksFromArgs();
        submitTasks();

        int gotTrue = 0;
        int threshold = node.getThreshold();
        int subtreesEvaluated = 0;

        while (gotTrue <= threshold && subtreesEvaluated < node.getArgs().length) {
            if (resultsFromSons.take()) {
                gotTrue++;
            }
            subtreesEvaluated++;
        }
        stopChildren();
        evaluation = gotTrue > threshold;
        resultsInParent.put(evaluation);
    }
}