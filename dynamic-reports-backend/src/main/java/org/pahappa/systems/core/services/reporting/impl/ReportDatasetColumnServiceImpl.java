package org.pahappa.systems.core.services.reporting.impl;

import com.googlecode.genericdao.search.Search;
import org.pahappa.systems.reporting.support.GenericServiceImpl;
import org.pahappa.systems.core.services.reporting.ReportDatasetColumnService;
import org.pahappa.systems.models.reporting.ReportDataset;
import org.pahappa.systems.models.reporting.ReportDatasetColumn;
import org.pahappa.systems.reporting.support.Validate;
import org.sers.webutils.model.RecordStatus;
import org.sers.webutils.model.exception.OperationFailedException;
import org.sers.webutils.model.exception.ValidationFailedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@Transactional
public class ReportDatasetColumnServiceImpl extends GenericServiceImpl<ReportDatasetColumn> implements ReportDatasetColumnService {

    @Override
    public boolean isDeletable(ReportDatasetColumn reportDatasetColumn) throws OperationFailedException {
        return true;
    }

    @Override
    public ReportDatasetColumn saveInstance(ReportDatasetColumn reportDatasetColumn) throws ValidationFailedException, OperationFailedException {
        Validate.notNull(reportDatasetColumn, "Dataset column cannot be null");
        Validate.notNull(reportDatasetColumn.getDataset(), "A dataset is required");
        Validate.hasText(reportDatasetColumn.getFieldName(), "A field name is required");
        if (!reportDatasetColumn.isSelectable()) {
            // A field that isn't selectable shouldn't be offered as filterable/sortable
            // either - enforced here so no save path (UI, API, legacy data) can create
            // that inconsistent combination.
            reportDatasetColumn.setFilterable(false);
            reportDatasetColumn.setSortable(false);
        }
        return super.save(reportDatasetColumn);
    }

    @Override
    public List<ReportDatasetColumn> getForDataset(ReportDataset dataset) {
        if (dataset == null) {
            return new ArrayList<ReportDatasetColumn>();
        }
        Search search = new Search();
        search.addFilterEqual("recordStatus", RecordStatus.ACTIVE);
        search.addFilterEqual("dataset", dataset);
        search.addSortAsc("displayOrder");
        return super.search(search);
    }

    @Override
    public List<ReportDatasetColumn> getSelectable(ReportDataset dataset) {
        return filterFlag(dataset, "selectable");
    }

    @Override
    public List<ReportDatasetColumn> getFilterable(ReportDataset dataset) {
        return filterFlag(dataset, "filterable");
    }

    @Override
    public List<ReportDatasetColumn> getSortable(ReportDataset dataset) {
        return filterFlag(dataset, "sortable");
    }

    private List<ReportDatasetColumn> filterFlag(ReportDataset dataset, String flagProperty) {
        if (dataset == null) {
            return new ArrayList<ReportDatasetColumn>();
        }
        Search search = new Search();
        search.addFilterEqual("recordStatus", RecordStatus.ACTIVE);
        search.addFilterEqual("dataset", dataset);
        search.addFilterEqual(flagProperty, true);
        search.addSortAsc("displayOrder");
        return super.search(search);
    }
}