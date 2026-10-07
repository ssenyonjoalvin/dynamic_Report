package org.pahappa.systems.models.reporting;

import org.sers.webutils.model.BaseEntity;

import javax.persistence.*;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Entity
@Table(name = "kpi_report_filters")
@Inheritance(strategy = InheritanceType.JOINED)
public class ReportFilter extends BaseEntity {

    public static final String MODE_DATE = "DATE";
    public static final String MODE_AGE = "AGE";

    private SavedReport savedReport;
    private FilterConnector connector = FilterConnector.AND;
    private List<String> values = new ArrayList<String>();
    private String valueMode = MODE_DATE;
    private Integer position = 0;
    private String fieldName;
    private FilterOperator operator = FilterOperator.EQUALS;
    private String value;
    private String value2;
    private transient Date filterDateValue;
    private transient Date filterDateValue2;

    public ReportFilter() {
    }

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "saved_report", nullable = false)
    public SavedReport getSavedReport() {
        return this.savedReport;
    }

    @Column(name = "field_name")
    public String getFieldName() {
        return this.fieldName;
    }

    @Enumerated(EnumType.STRING)
    @Column(name = "operator")
    public FilterOperator getOperator() {
        return this.operator;
    }

    @Column(name = "value")
    public String getValue() {
        return this.value;
    }

    @Column(name = "value2")
    public String getValue2() {
        return this.value2;
    }

    /** How this filter joins onto the one before it; ignored on the first filter. */
    @Enumerated(EnumType.STRING)
    @Column(name = "connector")
    public FilterConnector getConnector() {
        return this.connector;
    }

    /** The picked values for multi-value operators ("is one of", "is not one of"). */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "kpi_report_filter_values", joinColumns = @JoinColumn(name = "report_filter_id"))
    @OrderColumn(name = "value_order")
    @Column(name = "filter_value", length = 1024)
    public List<String> getValues() {
        return this.values;
    }

    /**
     * For date fields: {@link #MODE_DATE} compares against calendar dates,
     * {@link #MODE_AGE} compares the whole years elapsed since the date
     * (e.g. date of birth "less than 30" years ago), worked out when the
     * report runs.
     */
    @Column(name = "value_mode")
    public String getValueMode() {
        return this.valueMode;
    }

    @Transient
    public boolean isAgeMode() {
        return MODE_AGE.equals(this.valueMode);
    }

    /** Order of this filter in the report; AND/OR evaluation depends on it. */
    @Column(name = "position")
    public Integer getPosition() {
        return this.position;
    }

    public void setConnector(FilterConnector connector) {
        this.connector = connector;
    }

    public void setValues(List<String> values) {
        this.values = values != null ? values : new ArrayList<String>();
    }

    public void setValueMode(String valueMode) {
        this.valueMode = valueMode;
    }

    public void setPosition(Integer position) {
        this.position = position;
    }

    @Transient
    public Date getFilterDateValue() {
        return parseDate(this.value);
    }

    @Transient
    public void setFilterDateValue(Date filterDateValue) {
        this.value = formatDate(filterDateValue);
    }

    @Transient
    public Date getFilterDateValue2() {
        return parseDate(this.value2);
    }

    @Transient
    public void setFilterDateValue2(Date filterDateValue2) {
        this.value2 = formatDate(filterDateValue2);
    }

    public void setSavedReport(SavedReport savedReport) {
        this.savedReport = savedReport;
    }

    public void setFieldName(String fieldName) {
        this.fieldName = fieldName;
    }

    public void setOperator(FilterOperator operator) {
        this.operator = operator;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public void setValue2(String value2) {
        this.value2 = value2;
    }

    private Date parseDate(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        String[] patterns = {"yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd'T'HH:mm:ss", "yyyy-MM-dd", "dd/MM/yyyy"};
        for (String pattern : patterns) {
            try {
                return new SimpleDateFormat(pattern).parse(value.trim());
            } catch (ParseException e) {
                // try next pattern
            }
        }
        return null;
    }

    private String formatDate(Date date) {
        if (date == null) {
            return null;
        }
        java.util.Calendar calendar = java.util.Calendar.getInstance();
        calendar.setTime(date);
        boolean dateOnly = calendar.get(java.util.Calendar.HOUR_OF_DAY) == 0
                && calendar.get(java.util.Calendar.MINUTE) == 0
                && calendar.get(java.util.Calendar.SECOND) == 0
                && calendar.get(java.util.Calendar.MILLISECOND) == 0;
        return new SimpleDateFormat(dateOnly ? "yyyy-MM-dd" : "yyyy-MM-dd HH:mm:ss").format(date);
    }
}
