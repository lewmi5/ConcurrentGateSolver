# Parallel Boolean Circuits Solver

## Overview
This project implements a concurrent evaluation of Boolean expressions represented as Boolean circuits. Instead of evaluating expressions sequentially from left to right, the program takes advantage of parallel computation to evaluate subexpressions concurrently. The implementation also supports lazy evaluation, avoiding unnecessary computations when possible.

## Features
- **Parallel computation**: Subexpressions are evaluated concurrently to optimize performance.
- **Lazy evaluation**: Unnecessary computations are skipped if the result is already determined.
- **Support for various Boolean operations**: Includes AND, OR, NOT, conditional (IF), and threshold-based expressions (GT, LT).
- **Thread management**: Uses a thread pool to efficiently manage concurrent computations.
- **Graceful shutdown**: Provides a mechanism to stop ongoing computations.

## Boolean Expressions
Boolean expressions in this project are defined inductively:
- **Constants**: `true`, `false`
- **Negation**: `NOT(a)`
- **Conjunction**: `AND(a1, a2, ..., an)`, which evaluates to `true` if all subexpressions are `true`
- **Disjunction**: `OR(a1, a2, ..., an)`, which evaluates to `true` if at least one subexpression is `true`
- **Conditional**: `IF(a, b, c)`, which evaluates to `b` if `a` is `true`, otherwise evaluates to `c`
- **Threshold expressions**:
  - `GT_x(a1, a2, ..., an)`: Evaluates to `true` if at least `x+1` subexpressions are `true`
  - `LT_x(a1, a2, ..., an)`: Evaluates to `true` if at most `x-1` subexpressions are `true`

## Implementation Details
The project is implemented in Java and utilizes:
- **ExecutorService** for managing thread pools
- **BlockingQueue** for handling inter-thread communication
- **Semaphore** to synchronize computations

## How It Works
1. A Boolean circuit is represented as a tree-like structure.
2. Each node represents a Boolean operation and has child nodes as its arguments.
3. The `ParallelCircuitSolver` submits tasks for evaluating different parts of the circuit concurrently.
4. Lazy evaluation ensures that unnecessary computations are skipped whenever possible.
5. The result is computed efficiently and returned to the caller.

## How to Use
1. Create a Boolean circuit representation using the provided classes.
2. Instantiate `ParallelCircuitSolver`.
3. Call `solve()` on the circuit to get the result.
4. Use `stop()` to halt execution if needed.

## Requirements
- Java 11+
