package org.pahappa.systems.models.reporting;

import org.sers.webutils.model.BaseEntity;

import javax.persistence.*;

@Entity
@Table(name = "report_sorts")
@Inheritance(strategy = InheritanceType.JOINED)
public class ReportSort extends BaseEntity {
    private SavedReport savedReport;
    private String fieldName;
    private String direction = "ASC";
    private int priority;

    public ReportSort() {
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

    @Column(name = "direction")
    public String getDirection() {
        return this.direction;
    }

    @Column(name = "priority")
    public int getPriority() {
        return this.priority;
    }

    public void setSavedReport(SavedReport savedReport) {
        this.savedReport = savedReport;
    }

    public void setFieldName(String fieldName) {
        this.fieldName = fieldName;
    }

    public void setDirection(String direction) {
        this.direction = direction;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }
}
