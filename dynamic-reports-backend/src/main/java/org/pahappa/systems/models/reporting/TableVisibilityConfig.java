package org.pahappa.systems.models.reporting;

import org.sers.webutils.model.BaseEntity;

import javax.persistence.*;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "kpi_table_visibility_configs")
@Inheritance(strategy = InheritanceType.JOINED)
public class TableVisibilityConfig extends BaseEntity {
    private String tableName;
    private String displayName;
    private boolean visible = false;
    private Set<String> hiddenColumns = new HashSet<String>();
    private Set<String> filterableColumns = new HashSet<String>();
    private Set<String> sortableColumns = new HashSet<String>();

    public TableVisibilityConfig() {
    }

    @Column(name = "table_name", nullable = false, unique = true)
    public String getTableName() {
        return this.tableName;
    }

    @Column(name = "display_name")
    public String getDisplayName() {
        return this.displayName;
    }

    @Column(name = "visible", nullable = false)
    public boolean isVisible() {
        return this.visible;
    }

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "kpi_table_visibility_hidden_columns", joinColumns = @JoinColumn(name = "table_visibility_config_id"))
    @Column(name = "column_name")
    public Set<String> getHiddenColumns() {
        return this.hiddenColumns;
    }

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "kpi_table_visibility_filterable_columns", joinColumns = @JoinColumn(name = "table_visibility_config_id"))
    @Column(name = "column_name")
    public Set<String> getFilterableColumns() {
        return this.filterableColumns;
    }

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "kpi_table_visibility_sortable_columns", joinColumns = @JoinColumn(name = "table_visibility_config_id"))
    @Column(name = "column_name")
    public Set<String> getSortableColumns() {
        return this.sortableColumns;
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    public void setHiddenColumns(Set<String> hiddenColumns) {
        this.hiddenColumns = hiddenColumns;
    }

    public void setFilterableColumns(Set<String> filterableColumns) {
        this.filterableColumns = filterableColumns;
    }

    public void setSortableColumns(Set<String> sortableColumns) {
        this.sortableColumns = sortableColumns;
    }

    @Override
    public String toString() {
        return this.tableName;
    }
}
