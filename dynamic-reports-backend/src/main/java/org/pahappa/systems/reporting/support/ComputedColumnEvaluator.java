package org.pahappa.systems.reporting.support;

import org.pahappa.systems.models.reporting.ComputedOperation;
import org.pahappa.systems.models.reporting.ReportComputedColumn;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Works out calculated columns for one row. Operands are looked up in the
 * row's raw values, which hold both source fields and the results of
 * calculated columns evaluated earlier in the list - so a calculation can
 * build on a previous one. A missing operand, a non-numeric operand or a
 * division by zero yields an empty cell rather than an error.
 */
public final class ComputedColumnEvaluator {

    private static final long MILLIS_PER_DAY = 24L * 60 * 60 * 1000;

    private ComputedColumnEvaluator() {
    }

    /** Evaluates every column in order, putting each result into {@code rawRow} under its key. */
    public static void evaluateAll(List<ReportComputedColumn> columns, Map<String, Object> rawRow) {
        for (ReportComputedColumn column : columns) {
            rawRow.put(column.getColumnKey(), evaluate(column, rawRow));
        }
    }

    public static Object evaluate(ReportComputedColumn column, Map<String, Object> rawRow) {
        ComputedOperation operation = column.getOperation();
        if (operation == null || column.getLeftOperand() == null) {
            return null;
        }
        Object left = rawRow.get(column.getLeftOperand());
        Object right = column.isRightIsConstant() ? column.getRightConstant()
                : (column.getRightOperand() != null ? rawRow.get(column.getRightOperand()) : null);

        if (operation == ComputedOperation.CONCATENATE) {
            String a = ReportValues.toText(left);
            String b = ReportValues.toText(right);
            String joined = (a + " " + b).trim();
            return joined.isEmpty() ? null : joined;
        }
        if (operation == ComputedOperation.DAYS_BETWEEN) {
            if (!(left instanceof Date) || !(right instanceof Date)) {
                return null;
            }
            long from = ReportValues.startOfDay((Date) left).getTime();
            long to = ReportValues.startOfDay((Date) right).getTime();
            return Math.round((to - from) / (double) MILLIS_PER_DAY);
        }

        Double a = ReportValues.toDouble(left);
        Double b = ReportValues.toDouble(right);
        if (a == null || b == null) {
            return null;
        }
        Double result;
        switch (operation) {
            case ADD:
                result = a + b;
                break;
            case SUBTRACT:
                result = a - b;
                break;
            case ABSOLUTE_DIFFERENCE:
                result = Math.abs(a - b);
                break;
            case MULTIPLY:
                result = a * b;
                break;
            case DIVIDE:
                result = b == 0 ? null : a / b;
                break;
            case PERCENTAGE:
                result = b == 0 ? null : a / b * 100d;
                break;
            case PERCENT_CHANGE:
                result = a == 0 ? null : (b - a) / Math.abs(a) * 100d;
                break;
            case AVERAGE:
                result = (a + b) / 2d;
                break;
            case MINIMUM:
                result = Math.min(a, b);
                break;
            case MAXIMUM:
                result = Math.max(a, b);
                break;
            case MODULO:
                result = b == 0 ? null : a % b;
                break;
            case POWER:
                result = Math.pow(a, b);
                break;
            default:
                result = null;
        }
        if (result == null || result.isNaN() || result.isInfinite()) {
            return null;
        }
        int decimals = column.getDecimalPlaces() == null ? 2 : Math.max(0, Math.min(10, column.getDecimalPlaces()));
        return BigDecimal.valueOf(result).setScale(decimals, RoundingMode.HALF_UP);
    }
}
