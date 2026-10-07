package org.pahappa.systems.core.services.reporting;

import org.pahappa.systems.models.reporting.ReportDataset;
import org.pahappa.systems.models.reporting.SavedReport;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public interface ReportExecutionService {

    ReportResult execute(SavedReport report, int page, int pageSize);

    List<ReportField> availableFields(ReportDataset dataset);

    byte[] exportCsv(SavedReport report);

    class ReportResult implements Serializable {
        private List<String> columnKeys = new ArrayList<String>();
        private Map<String, String> columnLabels = new LinkedHashMap<String, String>();
        private List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
        private long totalCount;

        public List<String> getColumnKeys() {
            return columnKeys;
        }

        public void setColumnKeys(List<String> columnKeys) {
            this.columnKeys = columnKeys;
        }

        public Map<String, String> getColumnLabels() {
            return columnLabels;
        }

        public void setColumnLabels(Map<String, String> columnLabels) {
            this.columnLabels = columnLabels;
        }

        public List<Map<String, Object>> getRows() {
            return rows;
        }

        public void setRows(List<Map<String, Object>> rows) {
            this.rows = rows;
        }

        public long getTotalCount() {
            return totalCount;
        }

        public void setTotalCount(long totalCount) {
            this.totalCount = totalCount;
        }
    }

    class ReportField implements Serializable {
        private String key;
        private String label;
        private String type;
        private boolean selectable;
        private boolean filterable;
        private boolean sortable;
        private boolean enumType;
        private boolean dateType;
        private boolean reference;
        private List<String> enumValues = new ArrayList<String>();
        private List<FieldOption> referenceValues = new ArrayList<FieldOption>();

        public String getKey() {
            return key;
        }

        public void setKey(String key) {
            this.key = key;
        }

        public String getLabel() {
            return label;
        }

        public void setLabel(String label) {
            this.label = label;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
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

        public boolean isEnumType() {
            return enumType;
        }

        public void setEnumType(boolean enumType) {
            this.enumType = enumType;
        }

        public List<String> getEnumValues() {
            return enumValues;
        }

        public void setEnumValues(List<String> enumValues) {
            this.enumValues = enumValues != null ? enumValues : new ArrayList<String>();
        }

        public boolean isDateType() {
            return dateType;
        }

        public void setDateType(boolean dateType) {
            this.dateType = dateType;
        }

        public boolean isReference() {
            return reference;
        }

        public void setReference(boolean reference) {
            this.reference = reference;
        }

        public List<FieldOption> getReferenceValues() {
            return referenceValues;
        }

        public void setReferenceValues(List<FieldOption> referenceValues) {
            this.referenceValues = referenceValues != null ? referenceValues : new ArrayList<FieldOption>();
        }
    }

    class FieldOption implements Serializable {
        private String id;
        private String label;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getLabel() {
            return label;
        }

        public void setLabel(String label) {
            this.label = label;
        }
    }
}
