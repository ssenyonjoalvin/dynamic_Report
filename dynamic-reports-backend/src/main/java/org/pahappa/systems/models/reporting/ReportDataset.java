package org.pahappa.systems.models.reporting;

import org.sers.webutils.model.BaseEntity;

import javax.persistence.*;

@Entity
@Table(name = "kpi_report_datasets")
@Inheritance(strategy = InheritanceType.JOINED)
public class ReportDataset extends BaseEntity {

    public static final String SOURCE_ENTITY = "ENTITY";
    public static final String SOURCE_TABLE = "TABLE";

    private String name;
    private String description;
    private String entityClassName;
    private String tableName;
    private String datasetType = SOURCE_ENTITY;
    private boolean enabled = true;

    public ReportDataset() {
    }

    @Column(name = "name", nullable = false, unique = true)
    public String getName() {
        return this.name;
    }

    @Column(name = "description")
    public String getDescription() {
        return this.description;
    }

    @Column(name = "entity_class_name", nullable = false)
    public String getEntityClassName() {
        return this.entityClassName;
    }

    @Column(name = "table_name")
    public String getTableName() {
        return this.tableName;
    }

    @Column(name = "dataset_type")
    public String getDatasetType() {
        return this.datasetType;
    }

    @Transient
    public boolean isTableSourced() {
        return SOURCE_TABLE.equals(this.datasetType);
    }

    @Column(name = "enabled", nullable = false)
    public boolean isEnabled() {
        return this.enabled;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setEntityClassName(String entityClassName) {
        this.entityClassName = entityClassName;
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    public void setDatasetType(String datasetType) {
        this.datasetType = datasetType;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (object == null || getClass() != object.getClass()) return false;
        ReportDataset that = (ReportDataset) object;
        return name != null && name.equals(that.name);
    }

    @Override
    public int hashCode() {
        return name != null ? name.hashCode() : 0;
    }

    @Override
    public String toString() {
        return name;
    }
}
