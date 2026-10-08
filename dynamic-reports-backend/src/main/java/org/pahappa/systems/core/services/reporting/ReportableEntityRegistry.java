package org.pahappa.systems.core.services.reporting;

import java.util.List;

/**
 * Optional extension point that lets a host project register JPA entity
 * classes as reportable via "Approved Entity" datasets in code. Most hosts
 * don't need it: the entities under the packages listed in
 * {@code dynamic-reports.properties} are registered automatically. Beans of
 * this type add to that list (table-based datasets are unaffected).
 */
public interface ReportableEntityRegistry {

	/**
	 * @return the entity classes this registry contributes to the approved
	 *         reporting whitelist. Never returns null.
	 */
	List<Class<?>> approvedEntityClasses();

}
