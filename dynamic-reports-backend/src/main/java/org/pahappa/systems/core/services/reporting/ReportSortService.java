package org.pahappa.systems.core.services.reporting;

import org.pahappa.systems.reporting.support.GenericService;
import org.pahappa.systems.models.reporting.ReportSort;
import org.pahappa.systems.models.reporting.SavedReport;

import java.util.List;

public interface ReportSortService extends GenericService<ReportSort> {
    List<ReportSort> getForReport(SavedReport savedReport);
}
