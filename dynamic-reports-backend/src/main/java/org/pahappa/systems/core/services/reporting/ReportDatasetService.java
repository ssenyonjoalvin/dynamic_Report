package org.pahappa.systems.core.services.reporting;

import org.pahappa.systems.reporting.support.GenericService;
import org.pahappa.systems.models.reporting.ReportDataset;

import java.util.List;

public interface ReportDatasetService extends GenericService<ReportDataset> {

    List<ReportDataset> getAll();

    List<ReportDataset> getEnabled();
}
