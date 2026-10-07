package org.pahappa.systems.models.reporting;

import org.sers.webutils.model.BaseEntity;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Inheritance;
import javax.persistence.InheritanceType;
import javax.persistence.Table;

/**
 * Admin-configurable friendly name for an approved report entity class,
 * analogous to {@link TableVisibilityConfig#getDisplayName()} for tables.
 */
@Entity
@Table(name = "kpi_report_entity_display_configs")
@Inheritance(strategy = InheritanceType.JOINED)
public class EntityDisplayConfig extends BaseEntity {

    private String entityClassName;
    private String displayName;

    public EntityDisplayConfig() {
    }

    @Column(name = "entity_class_name", nullable = false, unique = true)
    public String getEntityClassName() {
        return this.entityClassName;
    }

    @Column(name = "display_name")
    public String getDisplayName() {
        return this.displayName;
    }

    public void setEntityClassName(String entityClassName) {
        this.entityClassName = entityClassName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    @Override
    public String toString() {
        return this.entityClassName;
    }
}
