package org.pahappa.systems.core.services.reporting;

import java.util.List;

/**
 * Extension point that lets a host project register its own JPA entity
 * classes as reportable via "Approved Entity" datasets. The reporting module
 * ships no entities of its own - it aggregates whatever Spring beans of this
 * type the host application registers, and reports nothing if none are
 * registered (table-based datasets are unaffected).
 */
public interface ReportableEntityRegistry {

	/**
	 * @return the entity classes this registry contributes to the approved
	 *         reporting whitelist. Never returns null.
	 */
	List<Class<?>> approvedEntityClasses();

}
