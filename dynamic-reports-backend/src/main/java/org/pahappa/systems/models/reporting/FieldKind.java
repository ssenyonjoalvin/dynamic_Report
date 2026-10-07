package org.pahappa.systems.models.reporting;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * What kind of value a report field holds. Decides which filter operators a
 * field offers and which value editor the report builder shows for it.
 */
public enum FieldKind {
    TEXT(FilterOperator.CONTAINS, FilterOperator.EQUALS, FilterOperator.NOT_EQUALS,
            FilterOperator.STARTS_WITH, FilterOperator.ENDS_WITH, FilterOperator.NOT_CONTAINS,
            FilterOperator.IN, FilterOperator.NOT_IN,
            FilterOperator.IS_EMPTY, FilterOperator.IS_NOT_EMPTY),
    NUMBER(FilterOperator.EQUALS, FilterOperator.NOT_EQUALS,
            FilterOperator.GREATER_THAN, FilterOperator.GREATER_OR_EQUAL,
            FilterOperator.LESS_THAN, FilterOperator.LESS_OR_EQUAL,
            FilterOperator.BETWEEN, FilterOperator.IS_NULL, FilterOperator.IS_NOT_NULL),
    DATE(FilterOperator.EQUALS, FilterOperator.NOT_EQUALS,
            FilterOperator.LESS_THAN, FilterOperator.LESS_OR_EQUAL,
            FilterOperator.GREATER_THAN, FilterOperator.GREATER_OR_EQUAL,
            FilterOperator.BETWEEN, FilterOperator.IS_NULL, FilterOperator.IS_NOT_NULL),
    BOOLEAN(FilterOperator.IS_TRUE, FilterOperator.IS_FALSE, FilterOperator.IS_NULL, FilterOperator.IS_NOT_NULL),
    ENUM(FilterOperator.IN, FilterOperator.NOT_IN, FilterOperator.IS_NULL, FilterOperator.IS_NOT_NULL),
    REFERENCE(FilterOperator.IN, FilterOperator.NOT_IN, FilterOperator.IS_NULL, FilterOperator.IS_NOT_NULL);

    private final List<FilterOperator> operators;

    FieldKind(FilterOperator... operators) {
        this.operators = Collections.unmodifiableList(Arrays.asList(operators));
    }

    /**
     * @return the operators that make sense for this kind, in the order the
     *         builder lists them. The first one is the default for a new filter.
     */
    public List<FilterOperator> operators() {
        return this.operators;
    }

    public boolean supports(FilterOperator operator) {
        return operator != null && this.operators.contains(operator);
    }

    /** Whether values of this kind can be sorted meaningfully. */
    public boolean isSortable() {
        return this != REFERENCE;
    }
}
