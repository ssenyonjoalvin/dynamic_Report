package org.pahappa.systems.models.reporting;

import java.util.ArrayList;
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
            FilterOperator.IN, FilterOperator.NOT_IN),
    NUMBER(FilterOperator.EQUALS, FilterOperator.NOT_EQUALS,
            FilterOperator.GREATER_THAN, FilterOperator.GREATER_OR_EQUAL,
            FilterOperator.LESS_THAN, FilterOperator.LESS_OR_EQUAL,
            FilterOperator.BETWEEN),
    DATE(FilterOperator.EQUALS, FilterOperator.NOT_EQUALS,
            FilterOperator.LESS_THAN, FilterOperator.LESS_OR_EQUAL,
            FilterOperator.GREATER_THAN, FilterOperator.GREATER_OR_EQUAL,
            FilterOperator.BETWEEN),
    BOOLEAN(FilterOperator.IS_TRUE, FilterOperator.IS_FALSE),
    ENUM(FilterOperator.IN, FilterOperator.NOT_IN),
    REFERENCE(FilterOperator.IN, FilterOperator.NOT_IN);

    private final List<FilterOperator> operators;

    /**
     * Any field of any kind can hold no value, so every kind also offers
     * "is empty" and "is not empty", listed last (the first operator is the
     * default for a new filter).
     */
    FieldKind(FilterOperator... operators) {
        List<FilterOperator> all = new ArrayList<FilterOperator>(Arrays.asList(operators));
        all.add(FilterOperator.IS_EMPTY);
        all.add(FilterOperator.IS_NOT_EMPTY);
        this.operators = Collections.unmodifiableList(all);
    }

    /**
     * @return the operators that make sense for this kind, in the order the
     *         builder lists them. The first one is the default for a new filter.
     */
    public List<FilterOperator> operators() {
        return this.operators;
    }

    public boolean supports(FilterOperator operator) {
        return operator != null && this.operators.contains(operator.canonical());
    }

    /** Whether values of this kind can be sorted meaningfully. */
    public boolean isSortable() {
        return this != REFERENCE;
    }
}
