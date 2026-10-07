package org.pahappa.systems.core.services.reporting.impl;

import com.googlecode.genericdao.search.Search;
import org.pahappa.systems.reporting.support.GenericServiceImpl;
import org.pahappa.systems.core.services.reporting.ReportDatasetService;
import org.pahappa.systems.models.reporting.ReportDataset;
import org.pahappa.systems.reporting.support.Validate;
import org.sers.webutils.model.RecordStatus;
import org.sers.webutils.model.exception.OperationFailedException;
import org.sers.webutils.model.exception.ValidationFailedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class ReportDatasetServiceImpl extends GenericServiceImpl<ReportDataset> implements ReportDatasetService {

    @Override
    public boolean isDeletable(ReportDataset reportDataset) throws OperationFailedException {
        return true;
    }

    @Override
    public ReportDataset saveInstance(ReportDataset reportDataset) throws ValidationFailedException, OperationFailedException {
        Validate.notNull(reportDataset, "Dataset cannot be null");
        Validate.hasText(reportDataset.getName(), "Dataset name is required");
        if (reportDataset.isTableSourced()) {
            Validate.hasText(reportDataset.getTableName(), "Dataset table is required");
        } else {
            Validate.hasText(reportDataset.getEntityClassName(), "Dataset entity is required");
        }
        return super.save(reportDataset);
    }

    @Override
    public List<ReportDataset> getAll() {
        Search search = new Search();
        search.addFilterEqual("recordStatus", RecordStatus.ACTIVE);
        search.addSortAsc("name");
        return super.search(search);
    }

    @Override
    public List<ReportDataset> getEnabled() {
        Search search = new Search();
        search.addFilterEqual("recordStatus", RecordStatus.ACTIVE);
        search.addFilterEqual("enabled", true);
        search.addSortAsc("name");
        return super.search(search);
    }
}