// Class representing a circuit value when execution is stopped
package cp2024.solution;

import cp2024.circuit.CircuitValue;

public class StoppedCircuitValue implements CircuitValue {
    @Override
    public boolean getValue() throws InterruptedException {
        throw new InterruptedException();
    }
}
