package org.pahappa.systems.core.services.reporting;

import org.pahappa.systems.reporting.support.GenericService;
import org.pahappa.systems.models.reporting.ReportFilter;
import org.pahappa.systems.models.reporting.SavedReport;

import java.util.List;

public interface ReportFilterService extends GenericService<ReportFilter> {
    List<ReportFilter> getForReport(SavedReport savedReport);
}
