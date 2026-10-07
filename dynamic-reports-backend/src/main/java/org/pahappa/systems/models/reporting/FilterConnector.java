package org.pahappa.systems.models.reporting;

/**
 * How a filter joins onto the filters before it. Conditions are evaluated
 * with the usual precedence: AND binds tighter than OR, so
 * {@code A OR B AND C} means {@code A OR (B AND C)}.
 */
public enum FilterConnector {
    AND,
    OR
}
