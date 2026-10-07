package org.pahappa.systems.core.services.reporting.impl;

import com.googlecode.genericdao.search.Search;
import org.pahappa.systems.reporting.support.GenericServiceImpl;
import org.pahappa.systems.core.services.reporting.ReportSortService;
import org.pahappa.systems.models.reporting.ReportSort;
import org.pahappa.systems.models.reporting.SavedReport;
import org.sers.webutils.model.RecordStatus;
import org.sers.webutils.model.exception.OperationFailedException;
import org.sers.webutils.model.exception.ValidationFailedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@Transactional
public class ReportSortServiceImpl extends GenericServiceImpl<ReportSort> implements ReportSortService {

    @Override
    public boolean isDeletable(ReportSort reportSort) throws OperationFailedException {
        return true;
    }

    @Override
    public ReportSort saveInstance(ReportSort reportSort) throws ValidationFailedException, OperationFailedException {
        return super.save(reportSort);
    }

    @Override
    public List<ReportSort> getForReport(SavedReport savedReport) {
        if (savedReport == null) {
            return new ArrayList<ReportSort>();
        }
        Search search = new Search();
        search.addFilterEqual("recordStatus", RecordStatus.ACTIVE);
        search.addFilterEqual("savedReport", savedReport);
        search.addSortAsc("priority");
        return super.search(search);
    }
}