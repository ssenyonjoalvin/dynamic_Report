package org.pahappa.systems.core.services.reporting;

import org.pahappa.systems.models.reporting.ReportComputedColumn;
import org.pahappa.systems.models.reporting.SavedReport;
import org.pahappa.systems.reporting.support.GenericService;

import java.util.List;

public interface ReportComputedColumnService extends GenericService<ReportComputedColumn> {
    List<ReportComputedColumn> getForReport(SavedReport savedReport);
}
