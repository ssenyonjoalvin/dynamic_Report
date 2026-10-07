package org.pahappa.systems.core.services.reporting;

import org.pahappa.systems.models.reporting.FieldKind;
import org.pahappa.systems.models.reporting.SavedReport;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds and runs reports on {@link ReportSourceDefinition report sources}.
 * A source's fields are its entity's own properties plus the properties of
 * the entities it references, addressed by dotted paths
 * ("department.departmentName").
 */
public interface ReportSourceQueryService {

    /** Fields of the source, the source's own first, then one group per referenced entity. */
    List<SourceField> fields(String sourceKey);

    /** Pickable rows for a {@link FieldKind#REFERENCE} field ("is one of" lists). */
    List<ReportExecutionService.FieldOption> referenceOptions(String sourceKey, String fieldKey);

    ReportExecutionService.ReportResult execute(SavedReport report, int page, int pageSize);

    byte[] exportCsv(SavedReport report);

    class SourceField implements Serializable {
        private static final long serialVersionUID = 1L;

        private String key;
        private String label;
        private String group;
        private FieldKind kind;
        private String javaType;
        private boolean persistent;
        private List<ReportExecutionService.FieldOption> enumOptions = new ArrayList<ReportExecutionService.FieldOption>();

        /** Dotted property path from the source entity, e.g. "department.departmentName". */
        public String getKey() {
            return key;
        }

        public void setKey(String key) {
            this.key = key;
        }

        /** Full label, e.g. "Department: Department Name". */
        public String getLabel() {
            return label;
        }

        public void setLabel(String label) {
            this.label = label;
        }

        /** Heading the field is grouped under in pickers, e.g. "Staff" or "Department". */
        public String getGroup() {
            return group;
        }

        public void setGroup(String group) {
            this.group = group;
        }

        public FieldKind getKind() {
            return kind;
        }

        public void setKind(FieldKind kind) {
            this.kind = kind;
        }

        public String getJavaType() {
            return javaType;
        }

        public void setJavaType(String javaType) {
            this.javaType = javaType;
        }

        /** Every segment of the path is a mapped JPA property, so it can be queried in HQL. */
        public boolean isPersistent() {
            return persistent;
        }

        public void setPersistent(boolean persistent) {
            this.persistent = persistent;
        }

        public List<ReportExecutionService.FieldOption> getEnumOptions() {
            return enumOptions;
        }

        public void setEnumOptions(List<ReportExecutionService.FieldOption> enumOptions) {
            this.enumOptions = enumOptions != null ? enumOptions : new ArrayList<ReportExecutionService.FieldOption>();
        }

        public boolean isSortable() {
            return kind != null && kind.isSortable();
        }
    }
}
