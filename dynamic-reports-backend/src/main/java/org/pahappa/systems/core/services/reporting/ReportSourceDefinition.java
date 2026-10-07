package org.pahappa.systems.core.services.reporting;

import java.util.Collections;
import java.util.Set;

/**
 * One thing users can build a report on - normally a constant of a host
 * project's own report source enum, e.g.
 *
 * <pre>
 * public enum MyReportSource implements ReportSourceDefinition {
 *     STAFF("Staff", "Staff master data with department and team.", Staff.class),
 *     DEPARTMENT("Department", "Departments and their heads.", Department.class);
 *     ...
 *     public String getKey() { return name(); }
 * }
 * </pre>
 *
 * The source's fields are discovered from {@link #getEntityClass()}: its own
 * properties plus, up to {@link #getJoinDepth()} levels deep, the properties
 * of every entity it references ("Department: Name").
 */
public interface ReportSourceDefinition {

    /** Stable identifier saved on reports. Use the enum constant's name(). */
    String getKey();

    String getLabel();

    /** One-line summary of what the source includes, shown under the picker. */
    String getDescription();

    /** The JPA entity the source reads. */
    Class<?> getEntityClass();

    /**
     * How many levels of referenced entities to expand into columns. 1 offers
     * "Department: Name" on Staff; 2 also offers "Department: Head: Email".
     */
    default int getJoinDepth() {
        return 1;
    }

    /**
     * Field paths to leave out of the source, e.g. "password" or
     * "userAccount" (which also drops "userAccount.*").
     */
    default Set<String> getExcludedFields() {
        return Collections.emptySet();
    }
}
