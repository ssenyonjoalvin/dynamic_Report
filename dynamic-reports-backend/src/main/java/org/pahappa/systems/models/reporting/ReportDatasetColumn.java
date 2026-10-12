package org.pahappa.systems.models.reporting;

import org.sers.webutils.model.BaseEntity;

import javax.persistence.*;

@Entity
@Table(name = "report_dataset_columns")
@Inheritance(strategy = InheritanceType.JOINED)
public class ReportDatasetColumn extends BaseEntity {
    private ReportDataset dataset;
    private String fieldName;
    private String label;
    private String fieldType;
    private boolean selectable = true;
    private boolean filterable = false;
    private boolean sortable = false;
    private int displayOrder;

    private boolean lookupEnabled = false;
    private ReportDataset lookupTargetDataset;
    private String lookupDisplayField;

    public ReportDatasetColumn() {
    }

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "dataset", nullable = false)
    public ReportDataset getDataset() {
        return this.dataset;
    }

    @Column(name = "field_name", nullable = false)
    public String getFieldName() {
        return this.fieldName;
    }

    @Column(name = "label")
    public String getLabel() {
        return this.label;
    }

    /**
     * The type of this field. When the field refers to another entity this
     * is that referred entity's class name (e.g.
     * org.pahappa.systems.kpiTracker.models.organizationStructure.Department
     * for Team.department); for table-sourced datasets a relationship column
     * carries the referenced table name instead. Scalar fields carry their
     * plain java (or SQL) type name.
     */
    @Column(name = "field_type", length = 512)
    public String getFieldType() {
        return this.fieldType;
    }

    @Column(name = "selectable", nullable = false)
    public boolean isSelectable() {
        return this.selectable;
    }

    @Column(name = "filterable", nullable = false)
    public boolean isFilterable() {
        return this.filterable;
    }

    @Column(name = "sortable", nullable = false)
    public boolean isSortable() {
        return this.sortable;
    }

    @Column(name = "display_order", nullable = false)
    public int getDisplayOrder() {
        return this.displayOrder;
    }

    @Column(name = "lookup_enabled", nullable = false)
    public boolean isLookupEnabled() {
        return this.lookupEnabled;
    }

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "lookup_target_dataset")
    public ReportDataset getLookupTargetDataset() {
        return this.lookupTargetDataset;
    }

    @Column(name = "lookup_display_field")
    public String getLookupDisplayField() {
        return this.lookupDisplayField;
    }

    public void setDataset(ReportDataset dataset) {
        this.dataset = dataset;
    }

    public void setFieldName(String fieldName) {
        this.fieldName = fieldName;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public void setFieldType(String fieldType) {
        this.fieldType = fieldType;
    }

    public void setSelectable(boolean selectable) {
        this.selectable = selectable;
    }

    public void setFilterable(boolean filterable) {
        this.filterable = filterable;
    }

    public void setSortable(boolean sortable) {
        this.sortable = sortable;
    }

    public void setDisplayOrder(int displayOrder) {
        this.displayOrder = displayOrder;
    }

    public void setLookupEnabled(boolean lookupEnabled) {
        this.lookupEnabled = lookupEnabled;
    }

    public void setLookupTargetDataset(ReportDataset lookupTargetDataset) {
        this.lookupTargetDataset = lookupTargetDataset;
    }

    public void setLookupDisplayField(String lookupDisplayField) {
        this.lookupDisplayField = lookupDisplayField;
    }

    @Override
    public String toString() {
        return this.label != null ? this.label : this.fieldName;
    }
}
