package org.pahappa.systems.core.services.reporting;

import java.util.List;
import java.util.Set;

/**
 * Extension point that lets a host project enrich in-memory entity report
 * rows with fields that aren't persistent JPA properties (e.g. derived
 * associations resolved through a separate join-table service). The
 * reporting module invokes every registered strategy that
 * {@link #supports(Class)} the entity class being reported on; if none are
 * registered, rows are returned exactly as loaded.
 */
public interface ReportRowEnrichmentStrategy {

	/**
	 * @return true if this strategy knows how to enrich instances of the
	 *         given entity class.
	 */
	boolean supports(Class<?> entityClass);

	/**
	 * Mutates the given entities in place to populate any transient fields
	 * that {@code involvedFields} (the set of fields the report actually
	 * selects, filters, or sorts on) requires.
	 */
	void enrich(Class<?> entityClass, List<?> entities, Set<String> involvedFields);

}
