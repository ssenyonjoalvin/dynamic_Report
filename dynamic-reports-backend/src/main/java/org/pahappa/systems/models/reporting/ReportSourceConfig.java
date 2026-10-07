package org.pahappa.systems.models.reporting;

import org.sers.webutils.model.BaseEntity;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Inheritance;
import javax.persistence.InheritanceType;
import javax.persistence.Table;

/**
 * Admin switch for one report source (a host project's report source enum
 * constant, identified by its key). A source with no config row is available.
 */
@Entity
@Table(name = "kpi_report_source_configs")
@Inheritance(strategy = InheritanceType.JOINED)
public class ReportSourceConfig extends BaseEntity {

    private String sourceKey;
    private boolean available = true;

    public ReportSourceConfig() {
    }

    @Column(name = "source_key", nullable = false, unique = true)
    public String getSourceKey() {
        return this.sourceKey;
    }

    @Column(name = "available", nullable = false)
    public boolean isAvailable() {
        return this.available;
    }

    public void setSourceKey(String sourceKey) {
        this.sourceKey = sourceKey;
    }

    public void setAvailable(boolean available) {
        this.available = available;
    }

    @Override
    public String toString() {
        return this.sourceKey;
    }
}
