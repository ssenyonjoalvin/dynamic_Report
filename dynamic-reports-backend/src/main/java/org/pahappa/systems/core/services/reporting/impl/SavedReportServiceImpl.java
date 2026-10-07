package org.pahappa.systems.core.services.reporting.impl;

import com.googlecode.genericdao.search.Search;
import org.pahappa.systems.reporting.support.GenericServiceImpl;
import org.pahappa.systems.core.services.reporting.ReportComputedColumnService;
import org.pahappa.systems.core.services.reporting.ReportFilterService;
import org.pahappa.systems.core.services.reporting.ReportSortService;
import org.pahappa.systems.core.services.reporting.SavedReportService;
import org.pahappa.systems.models.reporting.ReportComputedColumn;
import org.pahappa.systems.models.reporting.ReportFilter;
import org.pahappa.systems.models.reporting.ReportSort;
import org.pahappa.systems.models.reporting.SavedReport;
import org.pahappa.systems.reporting.support.Validate;
import org.sers.webutils.model.RecordStatus;
import org.sers.webutils.model.exception.OperationFailedException;
import org.sers.webutils.model.exception.ValidationFailedException;
import org.sers.webutils.model.security.User;
import org.sers.webutils.server.shared.SharedAppData;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service("kpiSavedReportService")
@Transactional
public class SavedReportServiceImpl extends GenericServiceImpl<SavedReport> implements SavedReportService {

    @Autowired
    private ReportFilterService reportFilterService;

    @Autowired
    private ReportSortService reportSortService;

    @Autowired
    private ReportComputedColumnService reportComputedColumnService;

    @Override
    public boolean isDeletable(SavedReport savedReport) throws OperationFailedException {
        return true;
    }

    @Override
    public SavedReport saveInstance(SavedReport savedReport) throws ValidationFailedException, OperationFailedException {
        return saveReport(savedReport);
    }

    private String currentUserId() {
        User user = SharedAppData.getLoggedInUser();
        return user != null ? user.getId() : null;
    }

    @Override
    public List<SavedReport> getMine() {
        String ownerId = currentUserId();
        if (ownerId == null) {
            return new ArrayList<SavedReport>();
        }
        Search search = new Search();
        search.addFilterEqual("recordStatus", RecordStatus.ACTIVE);
        search.addFilterEqual("ownerId", ownerId);
        return super.search(search);
    }

    @Override
    public SavedReport getMine(String id) {
        String ownerId = currentUserId();
        if (ownerId == null || id == null) {
            return null;
        }
        SavedReport report = super.getInstanceByID(id);
        if (report == null || report.getOwnerId() == null || !report.getOwnerId().equals(ownerId)) {
            return null;
        }
        return report;
    }

    @Override
    public SavedReport saveReport(SavedReport report) throws ValidationFailedException, OperationFailedException {
        Validate.notNull(report, "Report cannot be null");
        Validate.hasText(report.getName(), "Report name is required");
        String ownerId = currentUserId();
        if (ownerId == null) {
            throw new OperationFailedException("No logged-in user - a report must have an owner.");
        }
        if (report.getId() != null) {
            SavedReport existing = getMine(report.getId());
            if (existing == null) {
                throw new SecurityException("This report does not exist or you do not own it.");
            }
        }
        report.setOwnerId(ownerId);
        List<ReportFilter> filtersToSave = new ArrayList<ReportFilter>(report.getFilters());
        List<ReportSort> sortsToSave = new ArrayList<ReportSort>(report.getSort());
        List<ReportComputedColumn> computedToSave = new ArrayList<ReportComputedColumn>(report.getComputedColumns());
        SavedReport saved = super.save(report);
        replaceFilters(saved, filtersToSave);
        replaceSort(saved, sortsToSave);
        replaceComputedColumns(saved, computedToSave);
        return saved;
    }

    private void replaceFilters(SavedReport report, List<ReportFilter> newFilters) throws OperationFailedException, ValidationFailedException {
        List<ReportFilter> existing = this.reportFilterService.getForReport(report);
        for (ReportFilter filter : existing) {
            this.reportFilterService.deleteInstance(filter);
        }
        int position = 0;
        for (ReportFilter filter : newFilters) {
            filter.setSavedReport(report);
            filter.setPosition(position++);
            this.reportFilterService.saveInstance(filter);
        }
    }

    private void replaceSort(SavedReport report, List<ReportSort> newSorts) throws OperationFailedException, ValidationFailedException {
        List<ReportSort> existing = this.reportSortService.getForReport(report);
        for (ReportSort sort : existing) {
            this.reportSortService.deleteInstance(sort);
        }
        int priority = 0;
        for (ReportSort sort : newSorts) {
            sort.setSavedReport(report);
            sort.setPriority(priority++);
            this.reportSortService.saveInstance(sort);
        }
    }

    private void replaceComputedColumns(SavedReport report, List<ReportComputedColumn> newColumns) throws OperationFailedException, ValidationFailedException {
        for (ReportComputedColumn column : this.reportComputedColumnService.getForReport(report)) {
            this.reportComputedColumnService.deleteInstance(column);
        }
        int position = 0;
        for (ReportComputedColumn column : newColumns) {
            column.setSavedReport(report);
            column.setPosition(position++);
            this.reportComputedColumnService.saveInstance(column);
        }
    }

    @Override
    public void delete(String id) throws OperationFailedException {
        SavedReport report = getMine(id);
        if (report == null) {
            return;
        }
        for (ReportFilter filter : this.reportFilterService.getForReport(report)) {
            this.reportFilterService.deleteInstance(filter);
        }
        for (ReportSort sort : this.reportSortService.getForReport(report)) {
            this.reportSortService.deleteInstance(sort);
        }
        for (ReportComputedColumn column : this.reportComputedColumnService.getForReport(report)) {
            this.reportComputedColumnService.deleteInstance(column);
        }
        super.deleteInstance(report);
    }
}