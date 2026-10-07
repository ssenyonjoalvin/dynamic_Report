package org.pahappa.systems.core.services.reporting;

import org.pahappa.systems.models.reporting.SavedReport;
import org.sers.webutils.model.exception.OperationFailedException;
import org.sers.webutils.model.exception.ValidationFailedException;

import java.util.List;

public interface SavedReportService {

    List<SavedReport> getMine();

    SavedReport getMine(String id);

    SavedReport saveReport(SavedReport report) throws ValidationFailedException, OperationFailedException;

    void delete(String id) throws OperationFailedException;
}
