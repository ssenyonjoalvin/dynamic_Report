package org.pahappa.systems.views.reporting;

import org.pahappa.systems.core.services.reporting.ReportSourceDefinition;
import org.pahappa.systems.core.services.reporting.ReportSourceService;
import org.pahappa.systems.core.services.reporting.SavedReportService;
import org.pahappa.systems.models.reporting.SavedReport;
import org.sers.webutils.model.exception.OperationFailedException;
import org.sers.webutils.server.core.utils.ApplicationContextProvider;

import javax.annotation.PostConstruct;
import javax.faces.application.FacesMessage;
import javax.faces.bean.ManagedBean;
import javax.faces.bean.ViewScoped;
import javax.faces.context.FacesContext;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

/** Backs the saved reports list page (savedReports.xhtml). Editing happens on the report editor page. */
@ManagedBean(name = "savedReportsBean")
@ViewScoped
public class SavedReportsBean implements Serializable {

    private static final long serialVersionUID = 1L;

    private transient SavedReportService savedReportService;
    private transient ReportSourceService reportSourceService;

    private List<SavedReport> reports = new ArrayList<SavedReport>();
    private List<SavedReport> visibleReports = new ArrayList<SavedReport>();
    private String search;

    @PostConstruct
    public void init() {
        refresh();
    }

    private void services() {
        if (this.savedReportService == null) {
            this.savedReportService = ApplicationContextProvider.getBean(SavedReportService.class);
            this.reportSourceService = ApplicationContextProvider.getBean(ReportSourceService.class);
        }
    }

    public void refresh() {
        services();
        this.reports = new ArrayList<SavedReport>(this.savedReportService.getMine());
        Collections.sort(this.reports, new Comparator<SavedReport>() {
            @Override
            public int compare(SavedReport a, SavedReport b) {
                Date left = lastUpdated(a);
                Date right = lastUpdated(b);
                if (left == null || right == null) {
                    return left == null ? (right == null ? 0 : 1) : -1;
                }
                return right.compareTo(left);
            }
        });
        applySearch();
    }

    public void applySearch() {
        String needle = this.search != null ? this.search.trim().toLowerCase() : "";
        List<SavedReport> matching = new ArrayList<SavedReport>();
        for (SavedReport report : this.reports) {
            if (needle.isEmpty() || contains(report.getName(), needle) || contains(report.getDescription(), needle)
                    || contains(sourceLabel(report), needle)) {
                matching.add(report);
            }
        }
        this.visibleReports = matching;
    }

    private boolean contains(String text, String needle) {
        return text != null && text.toLowerCase().contains(needle);
    }

    public List<SavedReport> getVisibleReports() {
        return this.visibleReports;
    }

    public int getTotalCount() {
        return this.reports.size();
    }

    public String getSearch() {
        return this.search;
    }

    public void setSearch(String search) {
        this.search = search;
    }

    public String sourceLabel(SavedReport report) {
        services();
        ReportSourceDefinition source = this.reportSourceService.getSource(report.getSourceKey());
        if (source != null) {
            return source.getLabel();
        }
        if (report.getDataset() != null) {
            return report.getDataset().getName() + " (legacy dataset)";
        }
        return "—";
    }

    public Date lastUpdated(SavedReport report) {
        return report.getDateChanged() != null ? report.getDateChanged() : report.getDateCreated();
    }

    public void delete(SavedReport report) {
        services();
        try {
            this.savedReportService.delete(report.getId());
            refresh();
            FacesContext.getCurrentInstance().addMessage(null,
                    new FacesMessage(FacesMessage.SEVERITY_INFO, "Report deleted", "'" + report.getName() + "' was removed."));
        } catch (OperationFailedException e) {
            FacesContext.getCurrentInstance().addMessage(null,
                    new FacesMessage(FacesMessage.SEVERITY_ERROR, "Could not delete", e.getMessage()));
        }
    }
}
