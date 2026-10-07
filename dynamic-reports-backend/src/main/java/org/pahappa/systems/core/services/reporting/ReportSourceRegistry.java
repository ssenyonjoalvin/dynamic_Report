package org.pahappa.systems.core.services.reporting;

import java.util.List;

/**
 * Optional extension point for hand-curated report sources. Without one,
 * every mapped entity is discovered automatically (see
 * {@link DiscoveredReportSource}) and admins switch sources on or off on the
 * Report Sources page. A registered source replaces the discovered one for
 * the same entity, e.g. to give it a better label, description, join depth
 * or field exclusions. Several registries may coexist.
 */
public interface ReportSourceRegistry {

    /** @return the sources this registry contributes, in display order. Never null. */
    List<? extends ReportSourceDefinition> reportSources();
}
