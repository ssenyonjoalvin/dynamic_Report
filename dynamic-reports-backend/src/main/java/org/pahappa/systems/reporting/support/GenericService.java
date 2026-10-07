package org.pahappa.systems.reporting.support;

import com.googlecode.genericdao.search.Search;
import org.sers.webutils.model.exception.OperationFailedException;
import org.sers.webutils.model.exception.ValidationFailedException;

import java.util.List;

/**
 * Generic CRUD interface used by the reporting module's own services.
 * Vendored here (rather than depending on a host project's own copy) so that
 * dynamic-reports-backend has no compile-time dependency on any specific
 * project's business/service layer.
 *
 * @param <T>
 */
public interface GenericService<T> {

	T saveInstance(T entityInstance) throws ValidationFailedException, OperationFailedException;

	List<T> getInstances(Search search, int offset, int limit);

	T getInstanceByID(String id);

	int countInstances(Search search);

	void deleteInstance(T instance) throws OperationFailedException;

	List<T> getAllInstances();

}
