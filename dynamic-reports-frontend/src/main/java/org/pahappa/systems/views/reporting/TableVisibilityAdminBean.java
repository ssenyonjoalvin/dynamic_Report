package org.pahappa.systems.views.reporting;

import org.pahappa.systems.core.services.reporting.DatabaseTableDiscoveryService;
import org.pahappa.systems.core.services.reporting.EntityDiscoveryService;
import org.pahappa.systems.core.services.reporting.EntityDisplayService;
import org.pahappa.systems.core.services.reporting.TableVisibilityService;
import org.pahappa.systems.models.reporting.EntityDisplayConfig;
import org.pahappa.systems.models.reporting.TableVisibilityConfig;
import org.sers.webutils.model.exception.OperationFailedException;
import org.sers.webutils.model.exception.ValidationFailedException;
import org.sers.webutils.server.core.utils.ApplicationContextProvider;

import javax.annotation.PostConstruct;
import javax.faces.application.FacesMessage;
import javax.faces.bean.ManagedBean;
import javax.faces.bean.ViewScoped;
import javax.faces.context.FacesContext;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@ManagedBean(name = "tableVisibilityAdminBean")
@ViewScoped
public class TableVisibilityAdminBean implements Serializable {

    private static final long serialVersionUID = 1L;

    private DatabaseTableDiscoveryService databaseTableDiscoveryService;
    private TableVisibilityService tableVisibilityService;
    private EntityDiscoveryService entityDiscoveryService;
    private EntityDisplayService entityDisplayService;

    private List<TableRow> tableRows = new ArrayList<TableRow>();
    private String selectedTableName;
    private List<ColumnRow> columnRows = new ArrayList<ColumnRow>();
    private List<EntityDisplayRow> entityDisplayRows = new ArrayList<EntityDisplayRow>();
    private boolean allTablesVisible;
    private boolean allColumnsVisible;
    private boolean allColumnsFilterable;
    private boolean allColumnsSortable;

    @PostConstruct
    public void init() {
        this.databaseTableDiscoveryService = ApplicationContextProvider.getBean(DatabaseTableDiscoveryService.class);
        this.tableVisibilityService = ApplicationContextProvider.getBean(TableVisibilityService.class);
        this.entityDiscoveryService = ApplicationContextProvider.getBean(EntityDiscoveryService.class);
        this.entityDisplayService = ApplicationContextProvider.getBean(EntityDisplayService.class);
        refresh();
        refreshEntityDisplayRows();
    }

    public void refresh() {
        Map<String, TableVisibilityConfig> configByName = new HashMap<String, TableVisibilityConfig>();
        for (TableVisibilityConfig config : this.tableVisibilityService.getAll()) {
            configByName.put(config.getTableName(), config);
        }
        List<TableRow> rows = new ArrayList<TableRow>();
        for (String tableName : this.databaseTableDiscoveryService.listTables()) {
            TableVisibilityConfig config = configByName.get(tableName);
            TableRow row = new TableRow();
            row.setTableName(tableName);
            row.setDisplayName(config != null && config.getDisplayName() != null && !config.getDisplayName().isEmpty()
                    ? config.getDisplayName() : humanize(tableName));
            row.setVisible(config != null && config.isVisible());
            rows.add(row);
        }
        this.tableRows = rows;
    }

    public List<TableRow> getTableRows() {
        return this.tableRows;
    }

    public boolean isAllTablesVisible() {
        return this.allTablesVisible;
    }

    public void setAllTablesVisible(boolean allTablesVisible) {
        this.allTablesVisible = allTablesVisible;
    }

    public void toggleAllTablesVisible() {
        for (TableRow row : this.tableRows) {
            row.setVisible(this.allTablesVisible);
        }
    }

    public boolean isAllColumnsVisible() {
        return this.allColumnsVisible;
    }

    public void setAllColumnsVisible(boolean allColumnsVisible) {
        this.allColumnsVisible = allColumnsVisible;
    }

    public void toggleAllColumnsVisible() {
        for (ColumnRow row : this.columnRows) {
            row.setVisible(this.allColumnsVisible);
            if (!this.allColumnsVisible) {
                row.setFilterable(false);
                row.setSortable(false);
            }
        }
    }

    public boolean isAllColumnsFilterable() {
        return this.allColumnsFilterable;
    }

    public void setAllColumnsFilterable(boolean allColumnsFilterable) {
        this.allColumnsFilterable = allColumnsFilterable;
    }

    public void toggleAllColumnsFilterable() {
        for (ColumnRow row : this.columnRows) {
            if (row.isVisible()) {
                row.setFilterable(this.allColumnsFilterable);
            }
        }
    }

    public boolean isAllColumnsSortable() {
        return this.allColumnsSortable;
    }

    public void setAllColumnsSortable(boolean allColumnsSortable) {
        this.allColumnsSortable = allColumnsSortable;
    }

    public void toggleAllColumnsSortable() {
        for (ColumnRow row : this.columnRows) {
            if (row.isVisible()) {
                row.setSortable(this.allColumnsSortable);
            }
        }
    }

    public void onColumnVisibleToggled(ColumnRow row) {
        if (row != null && !row.isVisible()) {
            row.setFilterable(false);
            row.setSortable(false);
        }
    }

    public String getSelectedTableName() {
        return this.selectedTableName;
    }

    public List<ColumnRow> getColumnRows() {
        return this.columnRows;
    }

    public void saveTables() {
        try {
            for (TableRow row : this.tableRows) {
                TableVisibilityConfig config = this.tableVisibilityService.get(row.getTableName());
                if (config == null) {
                    config = new TableVisibilityConfig();
                    config.setTableName(row.getTableName());
                }
                config.setDisplayName(row.getDisplayName());
                config.setVisible(row.isVisible());
                this.tableVisibilityService.saveInstance(config);
            }
            message(FacesMessage.SEVERITY_INFO, "Saved", "Table visibility settings updated.");
        } catch (ValidationFailedException e) {
            message(FacesMessage.SEVERITY_ERROR, "Could Not Save", e.getMessage());
        } catch (OperationFailedException e) {
            message(FacesMessage.SEVERITY_ERROR, "Could Not Save", e.getMessage());
        }
    }

    public void editColumns(String tableName) {
        this.selectedTableName = tableName;
        this.columnRows = new ArrayList<ColumnRow>();
        if (tableName == null || tableName.isEmpty()) {
            return;
        }
        List<DatabaseTableDiscoveryService.DiscoveredColumn> discovered = this.databaseTableDiscoveryService.discoverColumns(tableName);
        TableVisibilityConfig config = this.tableVisibilityService.get(tableName);
        Set<String> hidden = config != null && config.getHiddenColumns() != null ? config.getHiddenColumns() : new HashSet<String>();
        Set<String> filterable = config != null && config.getFilterableColumns() != null ? config.getFilterableColumns() : new HashSet<String>();
        Set<String> sortable = config != null && config.getSortableColumns() != null ? config.getSortableColumns() : new HashSet<String>();
        for (DatabaseTableDiscoveryService.DiscoveredColumn column : discovered) {
            ColumnRow row = new ColumnRow();
            row.setColumnName(column.getColumnName());
            row.setTypeName(column.getTypeName());
            row.setVisible(!hidden.contains(column.getColumnName()));
            row.setFilterable(filterable.contains(column.getColumnName()));
            row.setSortable(sortable.contains(column.getColumnName()));
            this.columnRows.add(row);
        }
    }

    public void saveColumns() {
        if (this.selectedTableName == null || this.selectedTableName.isEmpty()) {
            return;
        }
        try {
            TableVisibilityConfig config = this.tableVisibilityService.get(this.selectedTableName);
            if (config == null) {
                config = new TableVisibilityConfig();
                config.setTableName(this.selectedTableName);
            }
            Set<String> hidden = new HashSet<String>();
            Set<String> filterable = new HashSet<String>();
            Set<String> sortable = new HashSet<String>();
            for (ColumnRow row : this.columnRows) {
                if (!row.isVisible()) {
                    hidden.add(row.getColumnName());
                }
                // A hidden column shouldn't be offered as a filterable/sortable
                // default either - same rule as "selectable" for dataset columns.
                if (row.isVisible() && row.isFilterable()) {
                    filterable.add(row.getColumnName());
                }
                if (row.isVisible() && row.isSortable()) {
                    sortable.add(row.getColumnName());
                }
            }
            config.setHiddenColumns(hidden);
            config.setFilterableColumns(filterable);
            config.setSortableColumns(sortable);
            this.tableVisibilityService.saveInstance(config);
            message(FacesMessage.SEVERITY_INFO, "Saved", "Column visibility for '" + this.selectedTableName + "' updated.");
        } catch (ValidationFailedException e) {
            message(FacesMessage.SEVERITY_ERROR, "Could Not Save", e.getMessage());
        } catch (OperationFailedException e) {
            message(FacesMessage.SEVERITY_ERROR, "Could Not Save", e.getMessage());
        }
    }

    public void closeColumns() {
        this.selectedTableName = null;
        this.columnRows = new ArrayList<ColumnRow>();
    }

    public boolean isEntitySourceAvailable() {
        return !this.entityDiscoveryService.approvedEntityClasses().isEmpty();
    }

    public void refreshEntityDisplayRows() {
        List<EntityDisplayRow> rows = new ArrayList<EntityDisplayRow>();
        for (Class<?> approved : this.entityDiscoveryService.approvedEntityClasses()) {
            String className = approved.getName();
            int lastDot = className.lastIndexOf('.');
            String defaultLabel = lastDot >= 0 ? className.substring(lastDot + 1) : className;
            String configured = this.entityDisplayService.getDisplayName(className);
            EntityDisplayRow row = new EntityDisplayRow();
            row.setEntityClassName(className);
            row.setDefaultLabel(defaultLabel);
            row.setDisplayName(configured != null ? configured : "");
            rows.add(row);
        }
        this.entityDisplayRows = rows;
    }

    public List<EntityDisplayRow> getEntityDisplayRows() {
        return this.entityDisplayRows;
    }

    public void saveEntityDisplayNames() {
        try {
            for (EntityDisplayRow row : this.entityDisplayRows) {
                EntityDisplayConfig config = this.entityDisplayService.get(row.getEntityClassName());
                if (config == null) {
                    config = new EntityDisplayConfig();
                    config.setEntityClassName(row.getEntityClassName());
                }
                config.setDisplayName(row.getDisplayName());
                this.entityDisplayService.saveInstance(config);
            }
            message(FacesMessage.SEVERITY_INFO, "Saved", "Entity display names updated.");
        } catch (ValidationFailedException e) {
            message(FacesMessage.SEVERITY_ERROR, "Could Not Save", e.getMessage());
        } catch (OperationFailedException e) {
            message(FacesMessage.SEVERITY_ERROR, "Could Not Save", e.getMessage());
        }
    }

    private String humanize(String identifier) {
        if (identifier == null || identifier.isEmpty()) {
            return identifier;
        }
        String[] parts = identifier.split("_");
        StringBuilder result = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (result.length() > 0) {
                result.append(' ');
            }
            result.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return result.toString();
    }

    private void message(FacesMessage.Severity severity, String summary, String detail) {
        FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(severity, summary, detail));
    }

    public static class TableRow implements Serializable {
        private static final long serialVersionUID = 1L;
        private String tableName;
        private String displayName;
        private boolean visible;

        public String getTableName() {
            return tableName;
        }

        public void setTableName(String tableName) {
            this.tableName = tableName;
        }

        public String getDisplayName() {
            return displayName;
        }

        public void setDisplayName(String displayName) {
            this.displayName = displayName;
        }

        public boolean isVisible() {
            return visible;
        }

        public void setVisible(boolean visible) {
            this.visible = visible;
        }
    }

    public static class ColumnRow implements Serializable {
        private static final long serialVersionUID = 1L;
        private String columnName;
        private String typeName;
        private boolean visible;
        private boolean filterable;
        private boolean sortable;

        public String getColumnName() {
            return columnName;
        }

        public void setColumnName(String columnName) {
            this.columnName = columnName;
        }

        public String getTypeName() {
            return typeName;
        }

        public void setTypeName(String typeName) {
            this.typeName = typeName;
        }

        public boolean isVisible() {
            return visible;
        }

        public void setVisible(boolean visible) {
            this.visible = visible;
        }

        public boolean isFilterable() {
            return filterable;
        }

        public void setFilterable(boolean filterable) {
            this.filterable = filterable;
        }

        public boolean isSortable() {
            return sortable;
        }

        public void setSortable(boolean sortable) {
            this.sortable = sortable;
        }
    }

    public static class EntityDisplayRow implements Serializable {
        private static final long serialVersionUID = 1L;
        private String entityClassName;
        private String defaultLabel;
        private String displayName;

        public String getEntityClassName() {
            return entityClassName;
        }

        public void setEntityClassName(String entityClassName) {
            this.entityClassName = entityClassName;
        }

        public String getDefaultLabel() {
            return defaultLabel;
        }

        public void setDefaultLabel(String defaultLabel) {
            this.defaultLabel = defaultLabel;
        }

        public String getDisplayName() {
            return displayName;
        }

        public void setDisplayName(String displayName) {
            this.displayName = displayName;
        }
    }
}