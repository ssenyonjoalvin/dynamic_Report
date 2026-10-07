package org.pahappa.systems.models.reporting;

/**
 * A row-level calculation between two report columns (or a column and a
 * fixed number) that produces a calculated column.
 */
public enum ComputedOperation {
    ADD("Add (A + B)"),
    SUBTRACT("Difference (A − B)"),
    ABSOLUTE_DIFFERENCE("Absolute difference |A − B|"),
    MULTIPLY("Multiply (A × B)"),
    DIVIDE("Divide (A ÷ B)"),
    PERCENTAGE("A as a percentage of B"),
    PERCENT_CHANGE("Percentage change from A to B"),
    AVERAGE("Average of A and B"),
    MINIMUM("Smaller of A and B"),
    MAXIMUM("Larger of A and B"),
    MODULO("Remainder of A ÷ B"),
    POWER("A to the power of B"),
    DAYS_BETWEEN("Days from date A to date B"),
    CONCATENATE("Join A and B as text");

    private final String label;

    ComputedOperation(String label) {
        this.label = label;
    }

    public String getLabel() {
        return this.label;
    }

    /** The result is a number that decimal places apply to. */
    public boolean isNumeric() {
        return this != CONCATENATE && this != DAYS_BETWEEN;
    }
}
