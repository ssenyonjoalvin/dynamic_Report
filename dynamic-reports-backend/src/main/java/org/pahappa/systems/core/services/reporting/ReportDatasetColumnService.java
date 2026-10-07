package org.pahappa.systems.core.services.reporting;

import org.pahappa.systems.reporting.support.GenericService;
import org.pahappa.systems.models.reporting.ReportDataset;
import org.pahappa.systems.models.reporting.ReportDatasetColumn;

import java.util.List;

public interface ReportDatasetColumnService extends GenericService<ReportDatasetColumn> {

    List<ReportDatasetColumn> getForDataset(ReportDataset dataset);

    List<ReportDatasetColumn> getSelectable(ReportDataset dataset);

    List<ReportDatasetColumn> getFilterable(ReportDataset dataset);

    List<ReportDatasetColumn> getSortable(ReportDataset dataset);
}
