package org.pahappa.systems.views.reporting;

import org.pahappa.systems.core.services.reporting.ReportComputedColumnService;
import org.pahappa.systems.core.services.reporting.ReportExecutionService.FieldOption;
import org.pahappa.systems.core.services.reporting.ReportExecutionService.ReportResult;
import org.pahappa.systems.core.services.reporting.ReportFilterService;
import org.pahappa.systems.core.services.reporting.ReportSortService;
import org.pahappa.systems.core.services.reporting.ReportSourceDefinition;
import org.pahappa.systems.core.services.reporting.ReportSourceQueryService;
import org.pahappa.systems.core.services.reporting.ReportSourceQueryService.SourceField;
import org.pahappa.systems.core.services.reporting.ReportSourceService;
import org.pahappa.systems.core.services.reporting.SavedReportService;
import org.pahappa.systems.models.reporting.ComputedOperation;
import org.pahappa.systems.models.reporting.FieldKind;
import org.pahappa.systems.models.reporting.FilterConnector;
import org.pahappa.systems.models.reporting.FilterOperator;
import org.pahappa.systems.models.reporting.ReportComputedColumn;
import org.pahappa.systems.models.reporting.ReportFilter;
import org.pahappa.systems.models.reporting.ReportSort;
import org.pahappa.systems.models.reporting.SavedReport;
import org.primefaces.component.tabview.TabView;
import org.primefaces.event.TabChangeEvent;
import org.primefaces.model.DefaultStreamedContent;
import org.primefaces.model.StreamedContent;
import org.sers.webutils.model.exception.OperationFailedException;
import org.sers.webutils.model.exception.ValidationFailedException;
import org.sers.webutils.server.core.utils.ApplicationContextProvider;

import javax.annotation.PostConstruct;
import javax.faces.application.FacesMessage;
import javax.faces.bean.ManagedBean;
import javax.faces.bean.ViewScoped;
import javax.faces.context.FacesContext;
import javax.faces.model.SelectItem;
import javax.faces.model.SelectItemGroup;
import java.io.ByteArrayInputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Backs the report editor page (reportEditor.xhtml): name, source, visible
 * columns, filters, sorting, calculated columns and the preview. Opened
 * with {@code ?reportId=...} to edit a saved report (add {@code &mode=run}
 * to land on the preview), or without parameters to create one.
 */
@ManagedBean(name = "reportEditorBean")
@ViewScoped
public class ReportEditorBean implements Serializable {

    private static final long serialVersionUID = 1L;

    private static final int PAGE_SIZE = 25;
    private static final int TAB_PREVIEW = 2;

    private transient SavedReportService savedReportService;
    private transient ReportSourceService reportSourceService;
    private transient ReportSourceQueryService queryService;
    private transient ReportFilterService reportFilterService;
    private transient ReportSortService reportSortService;
    private transient ReportComputedColumnService computedColumnService;

    private SavedReport editing;
    private List<SourceField> fields = new ArrayList<SourceField>();
    private Map<String, SourceField> fieldByKey = new HashMap<String, SourceField>();
    private final Map<String, List<FieldOption>> referenceOptionCache = new HashMap<String, List<FieldOption>>();

    private ReportResult previewResult;
    private String errorMessage;
    private int previewPage = 1;
    private int activeTabIndex = 0;
    private StreamedContent csvExport;

    @PostConstruct
    public void init() {
        services();
        Map<String, String> params = FacesContext.getCurrentInstance().getExternalContext().getRequestParameterMap();
        String reportId = params.get("reportId");
        this.editing = new SavedReport();
        if (reportId != null && !reportId.trim().isEmpty()) {
            SavedReport source = this.savedReportService.getMine(reportId);
            if (source == null) {
                this.errorMessage = "That report doesn't exist or isn't yours. You can build a new one below.";
            } else {
                load(source);
                if ("run".equals(params.get("mode"))) {
                    this.activeTabIndex = TAB_PREVIEW;
                    preview();
                }
            }
        }
    }

    private void services() {
        if (this.savedReportService == null) {
            this.savedReportService = ApplicationContextProvider.getBean(SavedReportService.class);
            this.reportSourceService = ApplicationContextProvider.getBean(ReportSourceService.class);
            this.queryService = ApplicationContextProvider.getBean(ReportSourceQueryService.class);
            this.reportFilterService = ApplicationContextProvider.getBean(ReportFilterService.class);
            this.reportSortService = ApplicationContextProvider.getBean(ReportSortService.class);
            this.computedColumnService = ApplicationContextProvider.getBean(ReportComputedColumnService.class);
        }
    }

    /** Edits a detached copy so nothing reaches the database until Save. */
    private void load(SavedReport source) {
        SavedReport copy = new SavedReport();
        copy.setId(source.getId());
        copy.setName(source.getName());
        copy.setDescription(source.getDescription());
        copy.setSourceKey(source.getSourceKey());
        copy.setDataset(source.getDataset());
        copy.setOwnerId(source.getOwnerId());
        copy.setSelectedColumns(new ArrayList<String>(source.getSelectedColumns()));
        copy.setColumnAliases(new HashMap<String, String>(source.getColumnAliases()));
        for (ReportFilter filter : this.reportFilterService.getForReport(source)) {
            ReportFilter filterCopy = new ReportFilter();
            filterCopy.setFieldName(filter.getFieldName());
            filterCopy.setOperator(filter.getOperator());
            filterCopy.setConnector(filter.getConnector() != null ? filter.getConnector() : FilterConnector.AND);
            filterCopy.setValue(filter.getValue());
            filterCopy.setValue2(filter.getValue2());
            filterCopy.setValues(new ArrayList<String>(filter.getValues()));
            filterCopy.setValueMode(filter.getValueMode() != null ? filter.getValueMode() : ReportFilter.MODE_DATE);
            filterCopy.setSavedReport(copy);
            copy.getFilters().add(filterCopy);
        }
        for (ReportSort sort : this.reportSortService.getForReport(source)) {
            ReportSort sortCopy = new ReportSort();
            sortCopy.setFieldName(sort.getFieldName());
            sortCopy.setDirection(sort.getDirection());
            sortCopy.setPriority(sort.getPriority());
            sortCopy.setSavedReport(copy);
            copy.getSort().add(sortCopy);
        }
        for (ReportComputedColumn column : this.computedColumnService.getForReport(source)) {
            ReportComputedColumn columnCopy = new ReportComputedColumn();
            columnCopy.setColumnKey(column.getColumnKey());
            columnCopy.setLabel(column.getLabel());
            columnCopy.setOperation(column.getOperation());
            columnCopy.setLeftOperand(column.getLeftOperand());
            columnCopy.setRightOperand(column.getRightOperand());
            columnCopy.setRightConstant(column.getRightConstant());
            columnCopy.setRightIsConstant(column.isRightIsConstant());
            columnCopy.setDecimalPlaces(column.getDecimalPlaces());
            columnCopy.setSavedReport(copy);
            copy.getComputedColumns().add(columnCopy);
        }
        this.editing = copy;
        refreshFields();
    }

    private void refreshFields() {
        this.fields = this.editing.getSourceKey() != null
                ? this.queryService.fields(this.editing.getSourceKey()) : new ArrayList<SourceField>();
        this.fieldByKey = new HashMap<String, SourceField>();
        for (SourceField field : this.fields) {
            this.fieldByKey.put(field.getKey(), field);
        }
        this.referenceOptionCache.clear();
    }

    // ------------------------------------------------------------- header

    public SavedReport getEditing() {
        return this.editing;
    }

    public boolean isExisting() {
        return this.editing.getId() != null;
    }

    public String getErrorMessage() {
        return this.errorMessage;
    }

    public int getActiveTabIndex() {
        return this.activeTabIndex;
    }

    public void setActiveTabIndex(int activeTabIndex) {
        this.activeTabIndex = activeTabIndex;
    }

    /** Opening the Preview tab runs the preview with whatever is on screen. */
    public void onTabChange(TabChangeEvent event) {
        if (event.getComponent() instanceof TabView) {
            this.activeTabIndex = ((TabView) event.getComponent()).getChildren().indexOf(event.getTab());
        }
        if (this.activeTabIndex == TAB_PREVIEW && this.editing.getSourceKey() != null) {
            preview();
        }
    }

    /** Available sources, plus this report's own source even if an admin has since switched it off. */
    public List<ReportSourceDefinition> getSourceOptions() {
        services();
        List<ReportSourceDefinition> options = new ArrayList<ReportSourceDefinition>(this.reportSourceService.getAvailableSources());
        ReportSourceDefinition current = getSelectedSource();
        if (current != null && !options.contains(current)) {
            options.add(0, current);
        }
        return options;
    }

    public ReportSourceDefinition getSelectedSource() {
        services();
        return this.reportSourceService.getSource(this.editing.getSourceKey());
    }

    /** A different source has different fields, so everything built on the old one is cleared. */
    public void sourceChanged() {
        this.editing.getSelectedColumns().clear();
        this.editing.getColumnAliases().clear();
        this.editing.getFilters().clear();
        this.editing.getSort().clear();
        this.editing.getComputedColumns().clear();
        this.previewResult = null;
        refreshFields();
    }

    // ------------------------------------------------------------ columns

    /** Column picker options: one group per entity, plus calculated columns. */
    public List<SelectItem> getColumnOptions() {
        List<SelectItem> groups = groupedFieldItems(false, false, true);
        if (!this.editing.getComputedColumns().isEmpty()) {
            List<SelectItem> calcs = new ArrayList<SelectItem>();
            for (ReportComputedColumn column : this.editing.getComputedColumns()) {
                calcs.add(new SelectItem(column.getColumnKey(), computedLabel(column)));
            }
            groups.add(group("Calculated columns", calcs));
        }
        return groups;
    }

    public List<String> getVisibleColumns() {
        return new ArrayList<String>(this.editing.getSelectedColumns());
    }

    /**
     * The picker submits ticked keys in option order; the report keeps the
     * user's own column order, so columns already shown keep their place and
     * newly ticked ones are appended.
     */
    public void setVisibleColumns(List<String> chosen) {
        List<String> chosenKeys = chosen != null ? chosen : new ArrayList<String>();
        List<String> merged = new ArrayList<String>();
        for (String key : this.editing.getSelectedColumns()) {
            if (chosenKeys.contains(key)) {
                merged.add(key);
            }
        }
        for (String key : chosenKeys) {
            if (!merged.contains(key)) {
                merged.add(key);
            }
        }
        this.editing.setSelectedColumns(merged);
    }

    public int getVisibleColumnCount() {
        return this.editing.getSelectedColumns().size();
    }

    public void moveColumnUp(String key) {
        int index = this.editing.getSelectedColumns().indexOf(key);
        moveColumn(index, index - 1);
    }

    public void moveColumnDown(String key) {
        int index = this.editing.getSelectedColumns().indexOf(key);
        moveColumn(index, index + 1);
    }

    private void moveColumn(int from, int to) {
        List<String> columns = this.editing.getSelectedColumns();
        if (from == to || from < 0 || to < 0 || from >= columns.size() || to >= columns.size()) {
            return;
        }
        columns.add(to, columns.remove(from));
    }

    public void hideColumn(String key) {
        this.editing.getSelectedColumns().remove(key);
        this.editing.getColumnAliases().remove(key);
    }

    public String columnLabel(String key) {
        ReportComputedColumn computed = computedColumn(key);
        if (computed != null) {
            return computedLabel(computed);
        }
        SourceField field = this.fieldByKey.get(key);
        return field != null ? field.getLabel() : key;
    }

    public String columnTypeLabel(String key) {
        if (ReportComputedColumn.isComputedKey(key)) {
            return "Calculated";
        }
        SourceField field = this.fieldByKey.get(key);
        return field != null ? kindLabel(field.getKind()) : "";
    }

    private String kindLabel(FieldKind kind) {
        if (kind == null) {
            return "";
        }
        switch (kind) {
            case TEXT:
                return "Text";
            case NUMBER:
                return "Number";
            case DATE:
                return "Date";
            case BOOLEAN:
                return "Yes / No";
            case ENUM:
                return "Choice";
            case REFERENCE:
                return "Record";
            default:
                return kind.name();
        }
    }

    // ------------------------------------------------- calculated columns

    public List<ComputedOperation> getComputedOperations() {
        return Arrays.asList(ComputedOperation.values());
    }

    public void addComputedColumn() {
        int next = 1;
        for (ReportComputedColumn column : this.editing.getComputedColumns()) {
            try {
                next = Math.max(next, Integer.parseInt(column.getColumnKey().substring(ReportComputedColumn.KEY_PREFIX.length())) + 1);
            } catch (RuntimeException e) {
                // foreign key format - ignore when numbering
            }
        }
        ReportComputedColumn column = new ReportComputedColumn();
        column.setColumnKey(ReportComputedColumn.KEY_PREFIX + next);
        column.setLabel("Calculated column " + next);
        column.setOperation(ComputedOperation.ADD);
        column.setSavedReport(this.editing);
        this.editing.getComputedColumns().add(column);
        this.editing.getSelectedColumns().add(column.getColumnKey());
    }

    /** Removes a calculated column, and clears it out of any calculation built on it. */
    public void removeComputedColumn(ReportComputedColumn column) {
        this.editing.getComputedColumns().remove(column);
        hideColumn(column.getColumnKey());
        for (ReportComputedColumn other : this.editing.getComputedColumns()) {
            if (column.getColumnKey().equals(other.getLeftOperand())) {
                other.setLeftOperand(null);
            }
            if (column.getColumnKey().equals(other.getRightOperand())) {
                other.setRightOperand(null);
            }
        }
    }

    /** Operands for a calculation: source fields, plus calculated columns defined before it. */
    public List<SelectItem> operandOptions(ReportComputedColumn column, boolean right) {
        List<SelectItem> groups = groupedFieldItems(true, false, false);
        List<SelectItem> earlier = new ArrayList<SelectItem>();
        for (ReportComputedColumn other : this.editing.getComputedColumns()) {
            if (other == column) {
                break;
            }
            earlier.add(new SelectItem(other.getColumnKey(), computedLabel(other)));
        }
        if (!earlier.isEmpty()) {
            groups.add(0, group("Calculated columns", earlier));
        }
        if (right) {
            groups.add(0, new SelectItem(ReportComputedColumn.CONSTANT_CHOICE, "A fixed number"));
        }
        return groups;
    }

    private ReportComputedColumn computedColumn(String key) {
        if (!ReportComputedColumn.isComputedKey(key)) {
            return null;
        }
        for (ReportComputedColumn column : this.editing.getComputedColumns()) {
            if (key.equals(column.getColumnKey())) {
                return column;
            }
        }
        return null;
    }

    private String computedLabel(ReportComputedColumn column) {
        return column.getLabel() != null && !column.getLabel().trim().isEmpty() ? column.getLabel() : "Calculated column";
    }

    // ------------------------------------------------------------ filters

    public List<FilterConnector> getConnectors() {
        return Arrays.asList(FilterConnector.values());
    }

    public List<SelectItem> getFilterFieldOptions() {
        return groupedFieldItems(false, false, false);
    }

    public void addFilter() {
        ReportFilter filter = new ReportFilter();
        filter.setConnector(FilterConnector.AND);
        filter.setSavedReport(this.editing);
        if (!this.fields.isEmpty()) {
            filter.setFieldName(this.fields.get(0).getKey());
            resetFilterForField(filter);
        }
        this.editing.getFilters().add(filter);
    }

    public void removeFilter(ReportFilter filter) {
        this.editing.getFilters().remove(filter);
    }

    public void filterFieldChanged(ReportFilter filter) {
        resetFilterForField(filter);
    }

    private void resetFilterForField(ReportFilter filter) {
        SourceField field = this.fieldByKey.get(filter.getFieldName());
        filter.setOperator(field != null ? field.getKind().operators().get(0) : null);
        filter.setValue(null);
        filter.setValue2(null);
        filter.setValues(new ArrayList<String>());
        filter.setValueMode(ReportFilter.MODE_DATE);
    }

    /** The age and date readings of a value differ, so switching modes clears it. */
    public void filterModeChanged(ReportFilter filter) {
        filter.setValue(null);
        filter.setValue2(null);
    }

    public void filterOperatorChanged(ReportFilter filter) {
        if (filter.getOperator() == null || !filter.getOperator().isMultiValue()) {
            filter.setValues(new ArrayList<String>());
        }
        if (filter.getOperator() == null || !filter.getOperator().needsSecondValue()) {
            filter.setValue2(null);
        }
    }

    public List<SelectItem> operatorOptions(ReportFilter filter) {
        List<SelectItem> items = new ArrayList<SelectItem>();
        SourceField field = this.fieldByKey.get(filter.getFieldName());
        if (field == null) {
            return items;
        }
        for (FilterOperator operator : field.getKind().operators()) {
            items.add(new SelectItem(operator, operator.label(field.getKind(), filter.isAgeMode())));
        }
        return items;
    }

    /**
     * Which value editor a filter row shows: NONE, MULTI (pick from a list),
     * CHIPS (type several values), TEXT, NUMBER, DATE or AGE, the last three
     * with a "_RANGE" suffix for "is between".
     */
    public String valueEditor(ReportFilter filter) {
        SourceField field = this.fieldByKey.get(filter.getFieldName());
        FilterOperator operator = filter.getOperator();
        if (field == null || operator == null || !field.getKind().supports(operator)) {
            return "NONE";
        }
        if (operator.isMultiValue()) {
            return field.getKind() == FieldKind.TEXT ? "CHIPS" : "MULTI";
        }
        if (!operator.needsValue()) {
            return "NONE";
        }
        String base;
        switch (field.getKind()) {
            case DATE:
                base = filter.isAgeMode() ? "AGE" : "DATE";
                break;
            case NUMBER:
                base = "NUMBER";
                break;
            default:
                base = "TEXT";
        }
        return operator.needsSecondValue() ? base + "_RANGE" : base;
    }

    public boolean isDateFilter(ReportFilter filter) {
        SourceField field = this.fieldByKey.get(filter.getFieldName());
        return field != null && field.getKind() == FieldKind.DATE
                && filter.getOperator() != null && filter.getOperator().needsValue();
    }

    /** Pickable values for "is one of": enum constants, or the referenced entity's rows. */
    public List<SelectItem> valueOptions(ReportFilter filter) {
        List<SelectItem> items = new ArrayList<SelectItem>();
        SourceField field = this.fieldByKey.get(filter.getFieldName());
        if (field == null) {
            return items;
        }
        List<FieldOption> options;
        if (field.getKind() == FieldKind.ENUM) {
            options = field.getEnumOptions();
        } else if (field.getKind() == FieldKind.REFERENCE) {
            options = this.referenceOptionCache.get(field.getKey());
            if (options == null) {
                services();
                options = this.queryService.referenceOptions(this.editing.getSourceKey(), field.getKey());
                this.referenceOptionCache.put(field.getKey(), options);
            }
        } else {
            return items;
        }
        for (FieldOption option : options) {
            items.add(new SelectItem(option.getId(), option.getLabel()));
        }
        return items;
    }

    // ------------------------------------------------------------ sorting

    public List<SelectItem> getSortFieldOptions() {
        return groupedFieldItems(false, true, false);
    }

    public void addSort() {
        ReportSort sort = new ReportSort();
        sort.setDirection("ASC");
        sort.setSavedReport(this.editing);
        for (SourceField field : this.fields) {
            if (field.isSortable()) {
                sort.setFieldName(field.getKey());
                break;
            }
        }
        this.editing.getSort().add(sort);
    }

    public void removeSort(ReportSort sort) {
        this.editing.getSort().remove(sort);
    }

    public void moveSortUp(ReportSort sort) {
        List<ReportSort> sorts = this.editing.getSort();
        int index = sorts.indexOf(sort);
        if (index > 0) {
            sorts.add(index - 1, sorts.remove(index));
        }
    }

    // ------------------------------------------------------------ preview

    public ReportResult getPreviewResult() {
        return this.previewResult;
    }

    public int getPreviewPage() {
        return this.previewPage;
    }

    public long getPreviewFrom() {
        return this.previewResult == null || this.previewResult.getTotalCount() == 0 ? 0 : (long) (this.previewPage - 1) * PAGE_SIZE + 1;
    }

    public long getPreviewTo() {
        return this.previewResult == null ? 0 : (long) (this.previewPage - 1) * PAGE_SIZE + this.previewResult.getRows().size();
    }

    public boolean isHasNextPage() {
        return this.previewResult != null && (long) this.previewPage * PAGE_SIZE < this.previewResult.getTotalCount();
    }

    public boolean isHasPreviousPage() {
        return this.previewPage > 1;
    }

    public void preview() {
        this.previewPage = 1;
        runPreview();
    }

    public void nextPage() {
        this.previewPage++;
        runPreview();
    }

    public void previousPage() {
        if (this.previewPage > 1) {
            this.previewPage--;
        }
        runPreview();
    }

    private void runPreview() {
        services();
        if (this.editing.getSourceKey() == null) {
            this.errorMessage = "Choose a report source before previewing.";
            this.previewResult = null;
            return;
        }
        renumber();
        try {
            this.previewResult = this.queryService.execute(this.editing, this.previewPage, PAGE_SIZE);
            this.errorMessage = null;
        } catch (RuntimeException e) {
            this.previewResult = null;
            this.errorMessage = describe(e, "The preview could not be run.");
        }
    }

    public StreamedContent getCsvExport() {
        return this.csvExport;
    }

    public void exportCsv() {
        services();
        this.csvExport = null;
        if (this.editing.getSourceKey() == null) {
            message(FacesMessage.SEVERITY_WARN, "Nothing to export", "Choose a report source first.");
            return;
        }
        renumber();
        try {
            byte[] csv = this.queryService.exportCsv(this.editing);
            this.csvExport = new DefaultStreamedContent(new ByteArrayInputStream(csv), "text/csv", safeFileName(this.editing.getName()) + ".csv");
        } catch (RuntimeException e) {
            message(FacesMessage.SEVERITY_ERROR, "Export failed", describe(e, "Unknown error."));
        }
    }

    // --------------------------------------------------------------- save

    public void save() {
        services();
        if (this.editing.getName() == null || this.editing.getName().trim().isEmpty()) {
            this.errorMessage = "Enter a name for this report.";
            return;
        }
        if (this.editing.getSourceKey() == null) {
            this.errorMessage = "Choose a report source.";
            return;
        }
        if (this.editing.getSelectedColumns().isEmpty()) {
            this.errorMessage = "Choose at least one column to show.";
            return;
        }
        renumber();
        boolean wasNew = !isExisting();
        try {
            SavedReport saved = this.savedReportService.saveReport(this.editing);
            // Reload so the editor holds fresh detached copies of what was stored.
            SavedReport reloaded = this.savedReportService.getMine(saved.getId());
            if (reloaded != null) {
                load(reloaded);
            }
            this.errorMessage = null;
            message(FacesMessage.SEVERITY_INFO, wasNew ? "Report created" : "Report updated",
                    "'" + this.editing.getName() + "' was saved.");
        } catch (ValidationFailedException e) {
            this.errorMessage = e.getMessage();
        } catch (OperationFailedException e) {
            this.errorMessage = e.getMessage();
        } catch (SecurityException e) {
            this.errorMessage = e.getMessage();
        }
    }

    /** List order is evaluation order; store it on the rows the engine and service read. */
    private void renumber() {
        int index = 0;
        for (ReportFilter filter : this.editing.getFilters()) {
            filter.setPosition(index++);
            filter.setSavedReport(this.editing);
        }
        index = 0;
        for (ReportSort sort : this.editing.getSort()) {
            sort.setPriority(index++);
            sort.setSavedReport(this.editing);
        }
        index = 0;
        for (ReportComputedColumn column : this.editing.getComputedColumns()) {
            column.setPosition(index++);
            column.setSavedReport(this.editing);
        }
    }

    // ------------------------------------------------------------ helpers

    /**
     * Source fields as grouped select items ("Staff", "Department", ...).
     * Inside a group, items drop the group prefix ("Name" under
     * "Department") when {@code shortLabels} is set.
     */
    private List<SelectItem> groupedFieldItems(boolean operandsOnly, boolean sortableOnly, boolean shortLabels) {
        Map<String, List<SelectItem>> byGroup = new LinkedHashMap<String, List<SelectItem>>();
        for (SourceField field : this.fields) {
            if (sortableOnly && !field.isSortable()) {
                continue;
            }
            if (operandsOnly && field.getKind() == FieldKind.REFERENCE) {
                continue;
            }
            List<SelectItem> items = byGroup.get(field.getGroup());
            if (items == null) {
                items = new ArrayList<SelectItem>();
                byGroup.put(field.getGroup(), items);
            }
            String label = field.getLabel();
            if (shortLabels && label.startsWith(field.getGroup() + ": ")) {
                label = label.substring(field.getGroup().length() + 2);
            }
            items.add(new SelectItem(field.getKey(), label));
        }
        List<SelectItem> groups = new ArrayList<SelectItem>();
        for (Map.Entry<String, List<SelectItem>> entry : byGroup.entrySet()) {
            groups.add(group(entry.getKey(), entry.getValue()));
        }
        return groups;
    }

    private SelectItemGroup group(String label, List<SelectItem> items) {
        SelectItemGroup group = new SelectItemGroup(label);
        group.setSelectItems(items.toArray(new SelectItem[items.size()]));
        return group;
    }

    private String describe(RuntimeException e, String fallback) {
        Throwable cause = e;
        // IllegalArgumentException messages are written for users; others are wrapped persistence errors.
        while (cause != null) {
            if (cause instanceof IllegalArgumentException && cause.getMessage() != null) {
                return cause.getMessage();
            }
            cause = cause.getCause();
        }
        return e.getMessage() != null ? fallback + " " + e.getMessage() : fallback;
    }

    private String safeFileName(String name) {
        if (name == null || name.trim().isEmpty()) {
            return "report";
        }
        StringBuilder cleaned = new StringBuilder();
        for (char c : name.toCharArray()) {
            if (Character.isLetterOrDigit(c) || c == '-' || c == '_') {
                cleaned.append(c);
            } else if (c == ' ') {
                cleaned.append('_');
            }
        }
        return cleaned.length() > 0 ? cleaned.toString() : "report";
    }

    private void message(FacesMessage.Severity severity, String summary, String detail) {
        FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(severity, summary, detail));
    }
}
