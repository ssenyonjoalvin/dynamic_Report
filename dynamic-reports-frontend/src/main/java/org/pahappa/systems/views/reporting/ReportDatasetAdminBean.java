package org.pahappa.systems.views.reporting;

import org.pahappa.systems.core.services.reporting.DatabaseTableDiscoveryService;
import org.pahappa.systems.core.services.reporting.EntityDiscoveryService;
import org.pahappa.systems.core.services.reporting.EntityDisplayService;
import org.pahappa.systems.core.services.reporting.ReportDatasetColumnService;
import org.pahappa.systems.core.services.reporting.ReportDatasetService;
import org.pahappa.systems.core.services.reporting.TableVisibilityService;
import org.pahappa.systems.models.reporting.ReportDataset;
import org.pahappa.systems.models.reporting.ReportDatasetColumn;
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
import java.util.List;

@ManagedBean(name = "reportDatasetAdminBean")
@ViewScoped
public class ReportDatasetAdminBean implements Serializable {

    private static final long serialVersionUID = 1L;

    private ReportDatasetService reportDatasetService;
    private ReportDatasetColumnService reportDatasetColumnService;
    private EntityDiscoveryService entityDiscoveryService;
    private EntityDisplayService entityDisplayService;
    private DatabaseTableDiscoveryService databaseTableDiscoveryService;
    private TableVisibilityService tableVisibilityService;

    private List<ReportDataset> datasets = new ArrayList<ReportDataset>();
    private ReportDataset editing;
    private String sourceType = ReportDataset.SOURCE_ENTITY;
    private String selectedEntityClassName;
    private String selectedTableName;
    private List<String> availableTables = new ArrayList<String>();
    private List<ColumnRow> columnRows = new ArrayList<ColumnRow>();
    private boolean allSelectable;
    private boolean allFilterable;
    private boolean allSortable;
    private boolean allLookupEnabled;

    @PostConstruct
    public void init() {
        this.reportDatasetService = ApplicationContextProvider.getBean(ReportDatasetService.class);
        this.reportDatasetColumnService = ApplicationContextProvider.getBean(ReportDatasetColumnService.class);
        this.entityDiscoveryService = ApplicationContextProvider.getBean(EntityDiscoveryService.class);
        this.entityDisplayService = ApplicationContextProvider.getBean(EntityDisplayService.class);
        this.databaseTableDiscoveryService = ApplicationContextProvider.getBean(DatabaseTableDiscoveryService.class);
        this.tableVisibilityService = ApplicationContextProvider.getBean(TableVisibilityService.class);
        this.availableTables = this.tableVisibilityService.getVisibleTables();
        refresh();
    }

    public void refresh() {
        this.datasets = this.reportDatasetService.getAll();
    }

    public List<ReportDataset> getDatasets() {
        return this.datasets;
    }

    public List<String> getApprovedEntityClassNames() {
        List<String> names = new ArrayList<String>();
        for (Class<?> approved : this.entityDiscoveryService.approvedEntityClasses()) {
            names.add(approved.getName());
        }
        return names;
    }

    public List<String> getAvailableTables() {
        return this.availableTables;
    }

    public String entityLabel(String className) {
        if (className == null) {
            return "";
        }
        String configured = this.entityDisplayService.getDisplayName(className);
        if (configured != null) {
            return configured;
        }
        int lastDot = className.lastIndexOf('.');
        return lastDot >= 0 ? className.substring(lastDot + 1) : className;
    }

    public String fieldTypeLabel(String fieldType) {
        if (fieldType == null || fieldType.isEmpty()) {
            return "";
        }
        if (fieldType.contains(".")) {
            return entityLabel(fieldType);
        }
        return fieldType;
    }

    public String sourceLabel(ReportDataset dataset) {
        if (dataset == null) {
            return "";
        }
        if (dataset.isTableSourced()) {
            return "Table: " + tableLabel(dataset.getTableName());
        }
        return "Entity: " + entityLabel(dataset.getEntityClassName());
    }

    public String tableLabel(String tableName) {
        if (tableName == null || tableName.isEmpty()) {
            return "";
        }
        TableVisibilityConfig config = this.tableVisibilityService.get(tableName);
        if (config != null && config.getDisplayName() != null && !config.getDisplayName().trim().isEmpty()) {
            return config.getDisplayName();
        }
        return humanize(tableName);
    }

    public ReportDataset getEditing() {
        return this.editing;
    }

    public String getSourceType() {
        return this.sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    public boolean isEntitySource() {
        return ReportDataset.SOURCE_ENTITY.equals(this.sourceType);
    }

    public boolean isEntitySourceAvailable() {
        return !this.entityDiscoveryService.approvedEntityClasses().isEmpty();
    }

    public boolean isTableSource() {
        return ReportDataset.SOURCE_TABLE.equals(this.sourceType);
    }

    public String getSelectedEntityClassName() {
        return this.selectedEntityClassName;
    }

    public void setSelectedEntityClassName(String selectedEntityClassName) {
        this.selectedEntityClassName = selectedEntityClassName;
    }

    public String getSelectedTableName() {
        return this.selectedTableName;
    }

    public void setSelectedTableName(String selectedTableName) {
        this.selectedTableName = selectedTableName;
    }

    public List<ColumnRow> getColumnRows() {
        return this.columnRows;
    }

    public boolean isAllSelectable() {
        return this.allSelectable;
    }

    public void setAllSelectable(boolean allSelectable) {
        this.allSelectable = allSelectable;
    }

    public void toggleAllSelectable() {
        for (ColumnRow row : this.columnRows) {
            row.setSelectable(this.allSelectable);
            if (!this.allSelectable) {
                row.setFilterable(false);
                row.setSortable(false);
            }
        }
    }

    public boolean isAllFilterable() {
        return this.allFilterable;
    }

    public void setAllFilterable(boolean allFilterable) {
        this.allFilterable = allFilterable;
    }

    public void toggleAllFilterable() {
        for (ColumnRow row : this.columnRows) {
            if (row.isSelectable()) {
                row.setFilterable(this.allFilterable);
            }
        }
    }

    public boolean isAllSortable() {
        return this.allSortable;
    }

    public void setAllSortable(boolean allSortable) {
        this.allSortable = allSortable;
    }

    public void toggleAllSortable() {
        for (ColumnRow row : this.columnRows) {
            if (row.isSelectable()) {
                row.setSortable(this.allSortable);
            }
        }
    }

    public boolean isAllLookupEnabled() {
        return this.allLookupEnabled;
    }

    public void setAllLookupEnabled(boolean allLookupEnabled) {
        this.allLookupEnabled = allLookupEnabled;
    }

    public void toggleAllLookupEnabled() {
        for (ColumnRow row : this.columnRows) {
            if (row.isRelationship()) {
                row.setLookupEnabled(this.allLookupEnabled);
            }
        }
    }

    public void onSelectableToggled(ColumnRow row) {
        if (row != null && !row.isSelectable()) {
            row.setFilterable(false);
            row.setSortable(false);
        }
    }

    public void newDataset() {
        this.editing = new ReportDataset();
        this.sourceType = isEntitySourceAvailable() ? ReportDataset.SOURCE_ENTITY : ReportDataset.SOURCE_TABLE;
        this.selectedEntityClassName = null;
        this.selectedTableName = null;
        this.columnRows = new ArrayList<ColumnRow>();
    }

    public void edit(ReportDataset dataset) {
        if (dataset == null) {
            return;
        }
        this.editing = dataset;
        this.sourceType = dataset.isTableSourced() ? ReportDataset.SOURCE_TABLE : ReportDataset.SOURCE_ENTITY;
        this.selectedEntityClassName = dataset.getEntityClassName();
        this.selectedTableName = dataset.getTableName();
        loadColumnRows();
    }

    public void onSourceTypeChanged() {
        this.selectedEntityClassName = null;
        this.selectedTableName = null;
        this.columnRows = new ArrayList<ColumnRow>();
    }

    public void onEntitySelected() {
        this.columnRows = new ArrayList<ColumnRow>();
        if (this.editing == null || this.selectedEntityClassName == null || this.selectedEntityClassName.isEmpty()) {
            return;
        }
        Class<?> entityClass = this.entityDiscoveryService.resolveApprovedClass(this.selectedEntityClassName);
        if (entityClass == null) {
            return;
        }
        List<EntityDiscoveryService.DiscoveredField> discovered = this.entityDiscoveryService.discoverFields(entityClass);
        int order = 0;
        for (EntityDiscoveryService.DiscoveredField field : discovered) {
            ColumnRow row = new ColumnRow();
            row.setFieldName(field.getFieldName());
            row.setLabel(field.getLabel());
            row.setFieldType(field.getJavaType().getName());
            row.setSelectable(true);
            row.setFilterable(true);
            row.setSortable(true);
            row.setDisplayOrder(order);
            if (field.isAssociation()) {
                row.setRelationship(true);
                row.setReferencedLabel(entityLabel(field.getJavaType().getName()));
                row.setLookupCandidates(datasetsForEntity(field.getJavaType().getName()));
            }
            order++;
            this.columnRows.add(row);
        }
    }

    private List<ReportDataset> datasetsForEntity(String entityClassName) {
        List<ReportDataset> candidates = new ArrayList<ReportDataset>();
        for (ReportDataset dataset : this.datasets) {
            if (!dataset.isTableSourced() && entityClassName.equals(dataset.getEntityClassName())) {
                candidates.add(dataset);
            }
        }
        return candidates;
    }

    private List<ReportDataset> datasetsForTable(String tableName) {
        List<ReportDataset> candidates = new ArrayList<ReportDataset>();
        for (ReportDataset dataset : this.datasets) {
            if (dataset.isTableSourced() && tableName.equals(dataset.getTableName())) {
                candidates.add(dataset);
            }
        }
        return candidates;
    }

    public void onTableSelected() {
        this.columnRows = new ArrayList<ColumnRow>();
        if (this.editing == null || this.selectedTableName == null || this.selectedTableName.isEmpty()) {
            return;
        }
        List<DatabaseTableDiscoveryService.DiscoveredColumn> discovered =
                this.tableVisibilityService.getVisibleColumns(this.selectedTableName);
        int order = 0;
        for (DatabaseTableDiscoveryService.DiscoveredColumn column : discovered) {
            ColumnRow row = new ColumnRow();
            row.setFieldName(column.getColumnName());
            row.setLabel(humanize(column.getColumnName()));
            row.setFieldType(column.isForeignKey() ? column.getReferencedTable() : column.getTypeName());
            row.setSelectable(true);
            row.setFilterable(true);
            row.setSortable(true);
            row.setDisplayOrder(order);
            if (column.isForeignKey()) {
                row.setRelationship(true);
                row.setReferencedLabel(column.getReferencedTable());
                row.setLookupCandidates(datasetsForTable(column.getReferencedTable()));
            }
            order++;
            this.columnRows.add(row);
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

    private void loadColumnRows() {
        this.columnRows = new ArrayList<ColumnRow>();
        if (this.editing == null || this.editing.getId() == null) {
            return;
        }
        java.util.Map<String, ColumnRow> discoveredByField = new java.util.HashMap<String, ColumnRow>();
        java.util.Map<String, String> typeByField = new java.util.HashMap<String, String>();
        if (this.editing.isTableSourced() && this.editing.getTableName() != null) {
            for (DatabaseTableDiscoveryService.DiscoveredColumn column : this.databaseTableDiscoveryService.discoverColumns(this.editing.getTableName())) {
                typeByField.put(column.getColumnName(),
                        column.isForeignKey() ? column.getReferencedTable() : column.getTypeName());
                if (column.isForeignKey()) {
                    ColumnRow marker = new ColumnRow();
                    marker.setRelationship(true);
                    marker.setReferencedLabel(column.getReferencedTable());
                    marker.setLookupCandidates(datasetsForTable(column.getReferencedTable()));
                    discoveredByField.put(column.getColumnName(), marker);
                }
            }
        } else if (this.editing.getEntityClassName() != null && !this.editing.getEntityClassName().isEmpty()) {
            Class<?> entityClass = this.entityDiscoveryService.resolveApprovedClass(this.editing.getEntityClassName());
            if (entityClass != null) {
                for (EntityDiscoveryService.DiscoveredField field : this.entityDiscoveryService.discoverFields(entityClass)) {
                    typeByField.put(field.getFieldName(), field.getJavaType().getName());
                    if (field.isAssociation()) {
                        ColumnRow marker = new ColumnRow();
                        marker.setRelationship(true);
                        marker.setReferencedLabel(entityLabel(field.getJavaType().getName()));
                        marker.setLookupCandidates(datasetsForEntity(field.getJavaType().getName()));
                        discoveredByField.put(field.getFieldName(), marker);
                    }
                }
            }
        }

        List<ReportDatasetColumn> existing = this.reportDatasetColumnService.getForDataset(this.editing);
        for (ReportDatasetColumn column : existing) {
            ColumnRow row = new ColumnRow();
            row.setFieldName(column.getFieldName());
            row.setLabel(column.getLabel());
            String fieldType = column.getFieldType();
            if (fieldType == null || fieldType.isEmpty()) {
                fieldType = typeByField.get(column.getFieldName());
            }
            row.setFieldType(fieldType);
            row.setSelectable(column.isSelectable());
            row.setFilterable(column.isFilterable());
            row.setSortable(column.isSortable());
            row.setDisplayOrder(column.getDisplayOrder());
            row.setLookupEnabled(column.isLookupEnabled());
            row.setLookupDisplayField(column.getLookupDisplayField());
            if (column.getLookupTargetDataset() != null) {
                row.setLookupTargetDatasetId(column.getLookupTargetDataset().getId());
            }
            ColumnRow marker = discoveredByField.get(column.getFieldName());
            if (marker != null) {
                row.setRelationship(true);
                row.setReferencedLabel(marker.getReferencedLabel());
                row.setLookupCandidates(marker.getLookupCandidates());
            }
            this.columnRows.add(row);
        }
    }

    public void save() {
        try {
            if (isTableSource()) {
                this.editing.setDatasetType(ReportDataset.SOURCE_TABLE);
                this.editing.setTableName(this.selectedTableName);
                this.editing.setEntityClassName("");
            } else {
                this.editing.setDatasetType(ReportDataset.SOURCE_ENTITY);
                this.editing.setEntityClassName(this.selectedEntityClassName);
                this.editing.setTableName(null);
            }
            ReportDataset saved = this.reportDatasetService.saveInstance(this.editing);
            this.editing = saved;

            List<ReportDatasetColumn> existingColumns = this.reportDatasetColumnService.getForDataset(saved);
            for (ReportDatasetColumn existing : existingColumns) {
                this.reportDatasetColumnService.deleteInstance(existing);
            }
            for (ColumnRow row : this.columnRows) {
                ReportDatasetColumn column = new ReportDatasetColumn();
                column.setDataset(saved);
                column.setFieldName(row.getFieldName());
                column.setLabel(row.getLabel());
                column.setFieldType(row.getFieldType());
                column.setSelectable(row.isSelectable());
                column.setFilterable(row.isFilterable());
                column.setSortable(row.isSortable());
                column.setDisplayOrder(row.getDisplayOrder());
                boolean lookupUsable = row.isRelationship() && row.isLookupEnabled()
                        && row.getLookupTargetDatasetId() != null && !row.getLookupTargetDatasetId().isEmpty()
                        && row.getLookupDisplayField() != null && !row.getLookupDisplayField().trim().isEmpty();
                column.setLookupEnabled(lookupUsable);
                if (lookupUsable) {
                    column.setLookupTargetDataset(this.reportDatasetService.getInstanceByID(row.getLookupTargetDatasetId()));
                    column.setLookupDisplayField(row.getLookupDisplayField().trim());
                } else {
                    column.setLookupTargetDataset(null);
                    column.setLookupDisplayField(null);
                }
                this.reportDatasetColumnService.saveInstance(column);
            }
            message(FacesMessage.SEVERITY_INFO, "Dataset Saved", "'" + saved.getName() + "' configuration was saved.");
            refresh();
        } catch (ValidationFailedException e) {
            message(FacesMessage.SEVERITY_ERROR, "Could Not Save", e.getMessage());
        } catch (OperationFailedException e) {
            message(FacesMessage.SEVERITY_ERROR, "Could Not Save", e.getMessage());
        }
    }

    public void delete(ReportDataset dataset) {
        if (dataset == null) {
            return;
        }
        try {
            this.reportDatasetService.deleteInstance(dataset);
            message(FacesMessage.SEVERITY_INFO, "Dataset Deleted", "'" + dataset.getName() + "' was removed.");
            refresh();
        } catch (OperationFailedException e) {
            message(FacesMessage.SEVERITY_ERROR, "Could Not Delete", e.getMessage());
        }
    }

    private void message(FacesMessage.Severity severity, String summary, String detail) {
        FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(severity, summary, detail));
    }

    public static class ColumnRow implements Serializable {
        private static final long serialVersionUID = 1L;
        private String fieldName;
        private String label;
        private String fieldType;
        private boolean selectable;
        private boolean filterable;
        private boolean sortable;
        private int displayOrder;
        private boolean relationship;
        private String referencedLabel;
        private List<ReportDataset> lookupCandidates = new ArrayList<ReportDataset>();
        private boolean lookupEnabled;
        private String lookupTargetDatasetId;
        private String lookupDisplayField;

        public boolean isRelationship() {
            return relationship;
        }

        public void setRelationship(boolean relationship) {
            this.relationship = relationship;
        }

        public String getReferencedLabel() {
            return referencedLabel;
        }

        public void setReferencedLabel(String referencedLabel) {
            this.referencedLabel = referencedLabel;
        }

        public List<ReportDataset> getLookupCandidates() {
            return lookupCandidates;
        }

        public void setLookupCandidates(List<ReportDataset> lookupCandidates) {
            this.lookupCandidates = lookupCandidates;
        }

        public boolean isLookupEnabled() {
            return lookupEnabled;
        }

        public void setLookupEnabled(boolean lookupEnabled) {
            this.lookupEnabled = lookupEnabled;
        }

        public String getLookupTargetDatasetId() {
            return lookupTargetDatasetId;
        }

        public void setLookupTargetDatasetId(String lookupTargetDatasetId) {
            this.lookupTargetDatasetId = lookupTargetDatasetId;
        }

        public String getLookupDisplayField() {
            return lookupDisplayField;
        }

        public void setLookupDisplayField(String lookupDisplayField) {
            this.lookupDisplayField = lookupDisplayField;
        }

        public String getFieldName() {
            return fieldName;
        }

        public void setFieldName(String fieldName) {
            this.fieldName = fieldName;
        }

        public String getLabel() {
            return label;
        }

        public void setLabel(String label) {
            this.label = label;
        }

        public String getFieldType() {
            return fieldType;
        }

        public void setFieldType(String fieldType) {
            this.fieldType = fieldType;
        }

        public boolean isSelectable() {
            return selectable;
        }

        public void setSelectable(boolean selectable) {
            this.selectable = selectable;
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

        public int getDisplayOrder() {
            return displayOrder;
        }

        public void setDisplayOrder(int displayOrder) {
            this.displayOrder = displayOrder;
        }
    }
}