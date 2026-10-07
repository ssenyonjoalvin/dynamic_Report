package org.pahappa.systems.core.services.reporting;

import java.io.Serializable;

/**
 * A report source found automatically among the host application's mapped
 * JPA entities - no host code needed. Its key is the entity's fully
 * qualified class name, which stays stable across restarts.
 */
public final class DiscoveredReportSource implements ReportSourceDefinition, Serializable {

    private static final long serialVersionUID = 1L;

    private final Class<?> entityClass;
    private final String label;
    private final String description;

    public DiscoveredReportSource(Class<?> entityClass, String label, String description) {
        this.entityClass = entityClass;
        this.label = label;
        this.description = description;
    }

    @Override
    public String getKey() {
        return this.entityClass.getName();
    }

    @Override
    public String getLabel() {
        return this.label;
    }

    @Override
    public String getDescription() {
        return this.description;
    }

    @Override
    public Class<?> getEntityClass() {
        return this.entityClass;
    }
}
