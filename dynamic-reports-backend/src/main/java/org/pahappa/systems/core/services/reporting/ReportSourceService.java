package org.pahappa.systems.core.services.reporting;

import org.sers.webutils.model.exception.OperationFailedException;
import org.sers.webutils.model.exception.ValidationFailedException;

import java.util.List;
import java.util.Map;

/**
 * The report sources contributed by every {@link ReportSourceRegistry}, plus
 * the admin's choice of which ones users may build new reports on.
 */
public interface ReportSourceService {

    /** Every registered source, available or not, in registry order. */
    List<ReportSourceDefinition> getAllSources();

    /** Sources users may pick when creating a report. */
    List<ReportSourceDefinition> getAvailableSources();

    /** @return the source with this key, available or not; null if unknown. */
    ReportSourceDefinition getSource(String key);

    boolean isAvailable(String key);

    /** Current availability of every registered source, keyed by source key. */
    Map<String, Boolean> getAvailability();

    /** Saves availability for the given source keys; unknown keys are ignored. */
    void saveAvailability(Map<String, Boolean> availabilityByKey) throws ValidationFailedException, OperationFailedException;
}
