package org.pahappa.systems.core.services.reporting.impl;

import com.googlecode.genericdao.search.Search;
import org.pahappa.systems.core.services.reporting.ReportComputedColumnService;
import org.pahappa.systems.models.reporting.ReportComputedColumn;
import org.pahappa.systems.models.reporting.SavedReport;
import org.pahappa.systems.reporting.support.GenericServiceImpl;
import org.sers.webutils.model.RecordStatus;
import org.sers.webutils.model.exception.OperationFailedException;
import org.sers.webutils.model.exception.ValidationFailedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@Transactional
public class ReportComputedColumnServiceImpl extends GenericServiceImpl<ReportComputedColumn> implements ReportComputedColumnService {

    @Override
    public boolean isDeletable(ReportComputedColumn instance) throws OperationFailedException {
        return true;
    }

    @Override
    public ReportComputedColumn saveInstance(ReportComputedColumn instance) throws ValidationFailedException, OperationFailedException {
        return super.save(instance);
    }

    @Override
    public List<ReportComputedColumn> getForReport(SavedReport savedReport) {
        if (savedReport == null || savedReport.getId() == null) {
            return new ArrayList<ReportComputedColumn>();
        }
        Search search = new Search();
        search.addFilterEqual("recordStatus", RecordStatus.ACTIVE);
        search.addFilterEqual("savedReport", savedReport);
        search.addSortAsc("position");
        return super.search(search);
    }
}
