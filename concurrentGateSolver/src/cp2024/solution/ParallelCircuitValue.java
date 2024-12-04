package cp2024.solution;

import cp2024.circuit.CircuitNode;
import cp2024.circuit.CircuitValue;
import cp2024.circuit.LeafNode;
import cp2024.circuit.ThresholdNode;

import java.util.ArrayList;
import java.util.concurrent.*;

public class ParallelCircuitValue implements CircuitValue, Runnable {
    private final ExecutorService pool;
    private final CircuitNode node;
    private final Semaphore isFree = new Semaphore(0);

    private boolean evaluation;
    private final BlockingQueue<Boolean> resultsInParent;

    private final ArrayList<Future<?>> futures = new ArrayList<>();
    private final ArrayList<ParallelCircuitValue> tasks = new ArrayList<>();
    private final BlockingQueue<Boolean> resultsFromSons = new LinkedBlockingQueue<>();

    ParallelCircuitValue(CircuitNode node, ExecutorService pool, BlockingQueue<Boolean> resultsQueue) {
        this.node = node;
        this.pool = pool;
        this.resultsInParent = resultsQueue;
    }

    /*
     * Returns evaluation of gate when finished.
     */
    @Override
    public boolean getValue() throws InterruptedException {
        isFree.acquire();
        boolean result = evaluation;
        isFree.release();

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

            // Marking that gate was evaluated.
            isFree.release();
            // Now getValue() is being woken up on semaphore.
        } catch (InterruptedException | ExecutionException e) {
            handleInterruption();
            return;
        }
        return;
    }

    private void createTasksFromArgs() throws InterruptedException {
        for (int i = 0; i < node.getArgs().length; i++) {
            CircuitNode toAdd = node.getArgs()[i];
            tasks.add(new ParallelCircuitValue(toAdd, pool, resultsFromSons));
        }
    }

    private void submitTasks() {
        for (ParallelCircuitValue task : tasks) {
            futures.add(pool.submit(task));
        }
    }

    private void handleInterruption() {
        stopChildren();
        isFree.release();
    }

    /*
     * Stops execution of children threads.
     */
    private void stopChildren() {
        for (Future<?> f : futures) {
            f.cancel(true);
        }
    }

    private void evaluateAND(CircuitNode node) throws InterruptedException {
        createTasksFromArgs();
        submitTasks();

        evaluation = true;
        int subtreesEvaluated = 0;
        while (evaluation && subtreesEvaluated < node.getArgs().length) {
            if (!resultsFromSons.take()) {
                evaluation = false;
                break;
            } else {
                subtreesEvaluated++;
            }

            if(Thread.currentThread().isInterrupted()) {
                handleInterruption();
                return;
            }
        }
        stopChildren();

        resultsInParent.put(evaluation);
    }

    private void evaluateOR(CircuitNode node) throws InterruptedException {
        createTasksFromArgs();
        submitTasks();

        evaluation = false;
        int subtreesEvaluated = 0;
        while (!evaluation && subtreesEvaluated < node.getArgs().length) {
            if (resultsFromSons.take()) {
                evaluation = true;
                break;
            } else {
                subtreesEvaluated++;
            }

            if(Thread.currentThread().isInterrupted()) {
                handleInterruption();
                return;
            }
        }
        stopChildren();

        resultsInParent.put(evaluation);
    }

    private void evaluateNOT(CircuitNode node) throws InterruptedException {
        createTasksFromArgs();
        submitTasks();

        evaluation = !resultsFromSons.take();
        resultsInParent.put(evaluation);
    }

    /*
     * if(args[0])
     *      evaluation = args[1];
     * else
     *      evaluation = args[2];
     */
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

    private void evaluateSLEEPYLEAFNODE(LeafNode node) throws InterruptedException {
        evaluation = node.getValue();
        resultsInParent.put(evaluation);
    }

    /*
     * If true occurs more than node.getThreshold()
     * gate is evaluated to true.
     * Otherwise to false.
     */
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
        
            if(Thread.currentThread().isInterrupted()) {
                handleInterruption();
                return;
            }
        }
        stopChildren();

        evaluation = gotTrue > threshold;
        resultsInParent.put(evaluation);
    }


    /*
     * If true occurs less than node.getThreshold()
     * gate is evaluated to true.
     * Otherwise to false.
     */
    private void evaluateLT(ThresholdNode node) throws InterruptedException {
        createTasksFromArgs();
        submitTasks();

        // True lest than x - 1 times
        // <=>
        // False at most n - x + 1 razy
        int gotFalse = 0;
        int threshold = node.getArgs().length - node.getThreshold();
        int subtreesEvaluated = 0;

        while (gotFalse <= threshold && subtreesEvaluated < node.getArgs().length) {
            if (!resultsFromSons.take()) {
                gotFalse++;
            }
            subtreesEvaluated++;

            if(Thread.currentThread().isInterrupted()) {
                handleInterruption();
                return;
            }
        }
        stopChildren();

        evaluation = gotFalse > threshold;
        resultsInParent.put(evaluation);
    }
}
