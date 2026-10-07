package org.pahappa.systems.models.reporting;

/**
 * A comparison a report filter applies to one field. Which operators a field
 * offers depends on its {@link FieldKind}; labels likewise read differently
 * per kind (a date is "before", an age is "less than").
 */
public enum FilterOperator {
    EQUALS,
    NOT_EQUALS,
    CONTAINS,
    NOT_CONTAINS,
    STARTS_WITH,
    ENDS_WITH,
    GREATER_THAN,
    GREATER_OR_EQUAL,
    LESS_THAN,
    LESS_OR_EQUAL,
    BETWEEN,
    IN,
    NOT_IN,
    IS_NULL,
    IS_NOT_NULL,
    IS_EMPTY,
    IS_NOT_EMPTY,
    IS_TRUE,
    IS_FALSE;

    /** The operator compares against a single typed-in or picked value. */
    public boolean needsValue() {
        return this != IS_NULL && this != IS_NOT_NULL && this != IS_EMPTY && this != IS_NOT_EMPTY
                && this != IS_TRUE && this != IS_FALSE && this != IN && this != NOT_IN;
    }

    public boolean needsSecondValue() {
        return this == BETWEEN;
    }

    /** The operator compares against a list of picked values. */
    public boolean isMultiValue() {
        return this == IN || this == NOT_IN;
    }

    /**
     * Negative operators also match rows where the field has no value at all:
     * a staff member with no department "is not one of" HR and Finance.
     */
    public boolean includesMissingValues() {
        return this == NOT_EQUALS || this == NOT_CONTAINS || this == NOT_IN;
    }

    public String label(FieldKind kind, boolean ageMode) {
        switch (this) {
            case EQUALS:
                return kind == FieldKind.DATE && !ageMode ? "is on" : "equals";
            case NOT_EQUALS:
                return kind == FieldKind.DATE && !ageMode ? "is not on" : "does not equal";
            case CONTAINS:
                return "contains";
            case NOT_CONTAINS:
                return "does not contain";
            case STARTS_WITH:
                return "starts with";
            case ENDS_WITH:
                return "ends with";
            case GREATER_THAN:
                return kind == FieldKind.DATE && !ageMode ? "is after" : "greater than";
            case GREATER_OR_EQUAL:
                return kind == FieldKind.DATE && !ageMode ? "is on or after" : "greater than or equal to";
            case LESS_THAN:
                return kind == FieldKind.DATE && !ageMode ? "is before" : "less than";
            case LESS_OR_EQUAL:
                return kind == FieldKind.DATE && !ageMode ? "is on or before" : "less than or equal to";
            case BETWEEN:
                return "is between";
            case IN:
                return "is one of";
            case NOT_IN:
                return "is not one of";
            case IS_NULL:
                return "is not set";
            case IS_NOT_NULL:
                return "is set";
            case IS_EMPTY:
                return "is empty";
            case IS_NOT_EMPTY:
                return "is not empty";
            case IS_TRUE:
                return "is yes";
            case IS_FALSE:
                return "is no";
            default:
                return name();
        }
    }
}
