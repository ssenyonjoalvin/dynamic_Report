package org.pahappa.systems.models.reporting;

import org.sers.webutils.model.BaseEntity;

import javax.persistence.*;

/**
 * A calculated column on a saved report: {@code left <operation> right},
 * worked out per row when the report runs. Each operand is either a source
 * field key, the key of an earlier calculated column (so calculations can be
 * chained, e.g. {@code (a + b) × c}), or - for the right operand - a fixed
 * number.
 */
@Entity
@Table(name = "kpi_report_computed_columns")
@Inheritance(strategy = InheritanceType.JOINED)
public class ReportComputedColumn extends BaseEntity {

    /** Prefix that marks a column key as a calculated column rather than a source field. */
    public static final String KEY_PREFIX = "calc:";

    private SavedReport savedReport;
    private String columnKey;
    private String label;
    private ComputedOperation operation = ComputedOperation.ADD;
    private String leftOperand;
    private String rightOperand;
    private Double rightConstant;
    private boolean rightIsConstant = false;
    private Integer decimalPlaces = 2;
    private Integer position = 0;

    public ReportComputedColumn() {
    }

    public static boolean isComputedKey(String key) {
        return key != null && key.startsWith(KEY_PREFIX);
    }

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "saved_report", nullable = false)
    public SavedReport getSavedReport() {
        return this.savedReport;
    }

    /** Stable key ("calc:1", "calc:2", ...) the report's column list refers to. */
    @Column(name = "column_key", nullable = false)
    public String getColumnKey() {
        return this.columnKey;
    }

    @Column(name = "label")
    public String getLabel() {
        return this.label;
    }

    @Enumerated(EnumType.STRING)
    @Column(name = "operation", nullable = false)
    public ComputedOperation getOperation() {
        return this.operation;
    }

    @Column(name = "left_operand", length = 512)
    public String getLeftOperand() {
        return this.leftOperand;
    }

    @Column(name = "right_operand", length = 512)
    public String getRightOperand() {
        return this.rightOperand;
    }

    @Column(name = "right_constant")
    public Double getRightConstant() {
        return this.rightConstant;
    }

    @Column(name = "right_is_constant", nullable = false)
    public boolean isRightIsConstant() {
        return this.rightIsConstant;
    }

    @Column(name = "decimal_places")
    public Integer getDecimalPlaces() {
        return this.decimalPlaces;
    }

    @Column(name = "position")
    public Integer getPosition() {
        return this.position;
    }

    /** Value the builder's right-operand picker uses for "a fixed number". */
    public static final String CONSTANT_CHOICE = "#number";

    /** The right operand as one picker value: a column key, or {@link #CONSTANT_CHOICE}. */
    @Transient
    public String getRightChoice() {
        return this.rightIsConstant ? CONSTANT_CHOICE : this.rightOperand;
    }

    public void setRightChoice(String choice) {
        this.rightIsConstant = CONSTANT_CHOICE.equals(choice);
        this.rightOperand = this.rightIsConstant ? null : choice;
    }

    public void setSavedReport(SavedReport savedReport) {
        this.savedReport = savedReport;
    }

    public void setColumnKey(String columnKey) {
        this.columnKey = columnKey;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public void setOperation(ComputedOperation operation) {
        this.operation = operation;
    }

    public void setLeftOperand(String leftOperand) {
        this.leftOperand = leftOperand;
    }

    public void setRightOperand(String rightOperand) {
        this.rightOperand = rightOperand;
    }

    public void setRightConstant(Double rightConstant) {
        this.rightConstant = rightConstant;
    }

    public void setRightIsConstant(boolean rightIsConstant) {
        this.rightIsConstant = rightIsConstant;
    }

    public void setDecimalPlaces(Integer decimalPlaces) {
        this.decimalPlaces = decimalPlaces;
    }

    public void setPosition(Integer position) {
        this.position = position;
    }

    @Override
    public String toString() {
        return this.label != null ? this.label : this.columnKey;
    }
}
