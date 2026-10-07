package org.pahappa.systems.reporting.support;

import com.googlecode.genericdao.search.Search;
import org.sers.webutils.model.BaseEntity;
import org.sers.webutils.model.RecordStatus;
import org.sers.webutils.model.exception.OperationFailedException;
import org.sers.webutils.server.shared.CustomLogger;
import org.sers.webutils.server.shared.CustomLogger.LogSeverity;
import org.sers.webutils.server.shared.SharedAppData;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;

/**
 * Vendored copy of the generic CRUD service base used by the reporting
 * module's own services (see {@link BaseDAOImpl}).
 *
 * @param <T>
 */
@Transactional
public abstract class GenericServiceImpl<T extends BaseEntity> extends BaseDAOImpl<T> implements GenericService<T> {

	@Override
	public void deleteInstance(T instance) throws OperationFailedException {
		if (!isDeletable(instance))
			throw new OperationFailedException("Deletion is not yet supported for this instance");
		changeStatusToDeleted(instance);
	}

	private void changeStatusToDeleted(T instance) {
		CustomLogger.log(getClass(), LogSeverity.LEVEL_DEBUG,
				String.format("Instance is deletable! Now setting the audit trail."));
		instance.setChangedBy(SharedAppData.getLoggedInUser());
		instance.setDateChanged(new Date());
		instance.setRecordStatus(RecordStatus.DELETED);
		super.save(instance);
		CustomLogger.log(getClass(), LogSeverity.LEVEL_DEBUG, String.format("Set record to deleted!"));
	}

	/**
	 * Must be implemented by all classes that extend this abstract class, to
	 * specify whether instances of an entity can be deleted.
	 */
	public abstract boolean isDeletable(T instance) throws OperationFailedException;

	@Override
	public T getInstanceByID(String instance) {
		return super.searchUniqueByPropertyEqual("id", instance);
	}

	@Override
	public int countInstances(Search search) {
		return super.count(search);
	}

	@Override
	public List<T> getInstances(Search search, int offset, int limit) {
		return super.search(search.setFirstResult(offset).setMaxResults(limit));
	}

	@Override
	public List<T> getAllInstances() {
		Search search = new Search();
		search.addFilterEqual("recordStatus", RecordStatus.ACTIVE);
		return super.search(search);
	}

}
