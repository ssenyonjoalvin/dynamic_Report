package org.pahappa.systems.models.reporting;

import org.sers.webutils.model.BaseEntity;

import javax.persistence.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Entity
@Table(name = "saved_reports")
@Inheritance(strategy = InheritanceType.JOINED)
public class SavedReport extends BaseEntity {
    private String name;
    private String description;
    private ReportDataset dataset;
    private String sourceKey;
    private String ownerId;
    private List<String> selectedColumns = new ArrayList<String>();
    private Map<String, String> columnAliases = new HashMap<String, String>();
    private List<ReportFilter> filters = new ArrayList<ReportFilter>();
    private List<ReportSort> sort = new ArrayList<ReportSort>();
    private List<ReportComputedColumn> computedColumns = new ArrayList<ReportComputedColumn>();

    public SavedReport() {
    }

    @Column(name = "name")
    public String getName() {
        return this.name;
    }

    @Column(name = "description")
    public String getDescription() {
        return this.description;
    }

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "dataset")
    public ReportDataset getDataset() {
        return this.dataset;
    }

    /**
     * Key of the report source (a host project's report source enum constant)
     * this report reads from. Reports built on the older admin-defined
     * datasets carry {@link #getDataset()} instead.
     */
    @Column(name = "source_key")
    public String getSourceKey() {
        return this.sourceKey;
    }

    public void setSourceKey(String sourceKey) {
        this.sourceKey = sourceKey;
    }

    @Column(name = "owner_id")
    public String getOwnerId() {
        return this.ownerId;
    }

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "saved_report_columns", joinColumns = @JoinColumn(name = "saved_report_id"))
    @Column(name = "field_name")
    public List<String> getSelectedColumns() {
        return this.selectedColumns;
    }

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "saved_report_column_aliases", joinColumns = @JoinColumn(name = "saved_report_id"))
    @MapKeyColumn(name = "field_name")
    @Column(name = "display_name")
    public Map<String, String> getColumnAliases() {
        return this.columnAliases;
    }

    public void setColumnAliases(Map<String, String> columnAliases) {
        this.columnAliases = columnAliases != null ? columnAliases : new HashMap<String, String>();
    }

    @Transient
    public List<ReportFilter> getFilters() {
        return this.filters;
    }

    @Transient
    public List<ReportSort> getSort() {
        return this.sort;
    }

    @Transient
    public List<ReportComputedColumn> getComputedColumns() {
        return this.computedColumns;
    }

    public void setComputedColumns(List<ReportComputedColumn> computedColumns) {
        this.computedColumns = computedColumns != null ? computedColumns : new ArrayList<ReportComputedColumn>();
    }

    public void setFilters(List<ReportFilter> filters) {
        this.filters = filters;
    }

    public void setSort(List<ReportSort> sort) {
        this.sort = sort;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setDataset(ReportDataset dataset) {
        this.dataset = dataset;
    }

    public void setOwnerId(String ownerId) {
        this.ownerId = ownerId;
    }

    public void setSelectedColumns(List<String> selectedColumns) {
        this.selectedColumns = selectedColumns;
    }

    @Override
    public String toString() {
        return this.name;
    }
}
