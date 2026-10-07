package org.pahappa.systems.core.services.reporting.impl;

import com.googlecode.genericdao.search.Search;
import org.pahappa.systems.reporting.support.GenericServiceImpl;
import org.pahappa.systems.core.services.reporting.ReportFilterService;
import org.pahappa.systems.models.reporting.ReportFilter;
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
public class ReportFilterServiceImpl extends GenericServiceImpl<ReportFilter> implements ReportFilterService {

    @Override
    public boolean isDeletable(ReportFilter reportFilter) throws OperationFailedException {
        return true;
    }

    @Override
    public ReportFilter saveInstance(ReportFilter reportFilter) throws ValidationFailedException, OperationFailedException {
        return super.save(reportFilter);
    }

    @Override
    public List<ReportFilter> getForReport(SavedReport savedReport) {
        if (savedReport == null) {
            return new ArrayList<ReportFilter>();
        }
        Search search = new Search();
        search.addFilterEqual("recordStatus", RecordStatus.ACTIVE);
        search.addFilterEqual("savedReport", savedReport);
        search.addSortAsc("position");
        return super.search(search);
    }
}