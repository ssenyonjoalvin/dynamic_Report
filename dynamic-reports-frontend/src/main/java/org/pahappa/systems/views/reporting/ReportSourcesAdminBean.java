package org.pahappa.systems.views.reporting;

import org.pahappa.systems.core.services.reporting.ReportSourceDefinition;
import org.pahappa.systems.core.services.reporting.ReportSourceService;
import org.sers.webutils.model.exception.OperationFailedException;
import org.sers.webutils.model.exception.ValidationFailedException;
import org.sers.webutils.server.core.utils.ApplicationContextProvider;

import javax.annotation.PostConstruct;
import javax.faces.application.FacesMessage;
import javax.faces.bean.ManagedBean;
import javax.faces.bean.ViewScoped;
import javax.faces.context.FacesContext;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Backs the report sources admin page (reportSourcesAdmin.xhtml): which
 * registered sources users may build new reports on. Toggles are held
 * here until Save, so an admin can review or discard them.
 */
@ManagedBean(name = "reportSourcesAdminBean")
@ViewScoped
public class ReportSourcesAdminBean implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final String SHOW_ALL = "ALL";
    public static final String SHOW_AVAILABLE = "AVAILABLE";
    public static final String SHOW_UNAVAILABLE = "UNAVAILABLE";

    private transient ReportSourceService reportSourceService;

    private List<SourceRow> rows = new ArrayList<SourceRow>();
    private List<SourceRow> visibleRows = new ArrayList<SourceRow>();
    private String search;
    private String show = SHOW_ALL;

    @PostConstruct
    public void init() {
        load();
    }

    private ReportSourceService service() {
        if (this.reportSourceService == null) {
            this.reportSourceService = ApplicationContextProvider.getBean(ReportSourceService.class);
        }
        return this.reportSourceService;
    }

    private void load() {
        Map<String, Boolean> availability = service().getAvailability();
        List<SourceRow> loaded = new ArrayList<SourceRow>();
        for (ReportSourceDefinition source : service().getAllSources()) {
            loaded.add(new SourceRow(source.getKey(), source.getLabel(), source.getDescription(),
                    Boolean.TRUE.equals(availability.get(source.getKey()))));
        }
        this.rows = loaded;
        applyFilter();
    }

    public void applyFilter() {
        String needle = this.search != null ? this.search.trim().toLowerCase() : "";
        List<SourceRow> matching = new ArrayList<SourceRow>();
        for (SourceRow row : this.rows) {
            boolean textMatches = needle.isEmpty() || row.getLabel().toLowerCase().contains(needle)
                    || (row.getDescription() != null && row.getDescription().toLowerCase().contains(needle));
            boolean showMatches = SHOW_ALL.equals(this.show)
                    || (SHOW_AVAILABLE.equals(this.show) && row.isAvailable())
                    || (SHOW_UNAVAILABLE.equals(this.show) && !row.isAvailable());
            if (textMatches && showMatches) {
                matching.add(row);
            }
        }
        this.visibleRows = matching;
    }

    public void save() {
        Map<String, Boolean> changes = new LinkedHashMap<String, Boolean>();
        for (SourceRow row : this.rows) {
            if (row.isChanged()) {
                changes.put(row.getKey(), row.isAvailable());
            }
        }
        if (changes.isEmpty()) {
            return;
        }
        try {
            service().saveAvailability(changes);
            load();
            message(FacesMessage.SEVERITY_INFO, "Report sources saved",
                    changes.size() + (changes.size() == 1 ? " source was" : " sources were") + " updated.");
        } catch (ValidationFailedException e) {
            message(FacesMessage.SEVERITY_ERROR, "Could not save", e.getMessage());
        } catch (OperationFailedException e) {
            message(FacesMessage.SEVERITY_ERROR, "Could not save", e.getMessage());
        }
    }

    public void discard() {
        for (SourceRow row : this.rows) {
            row.setAvailable(row.isOriginallyAvailable());
        }
        applyFilter();
    }

    public List<SourceRow> getVisibleRows() {
        return this.visibleRows;
    }

    public int getTotalCount() {
        return this.rows.size();
    }

    public int getAvailableCount() {
        int count = 0;
        for (SourceRow row : this.rows) {
            if (row.isAvailable()) {
                count++;
            }
        }
        return count;
    }

    public int getChangeCount() {
        int count = 0;
        for (SourceRow row : this.rows) {
            if (row.isChanged()) {
                count++;
            }
        }
        return count;
    }

    public String getSearch() {
        return this.search;
    }

    public void setSearch(String search) {
        this.search = search;
    }

    public String getShow() {
        return this.show;
    }

    public void setShow(String show) {
        this.show = show != null ? show : SHOW_ALL;
    }

    private void message(FacesMessage.Severity severity, String summary, String detail) {
        FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(severity, summary, detail));
    }

    public static class SourceRow implements Serializable {
        private static final long serialVersionUID = 1L;
        private final String key;
        private final String label;
        private final String description;
        private final boolean originallyAvailable;
        private boolean available;

        public SourceRow(String key, String label, String description, boolean available) {
            this.key = key;
            this.label = label;
            this.description = description;
            this.originallyAvailable = available;
            this.available = available;
        }

        public String getKey() {
            return this.key;
        }

        public String getLabel() {
            return this.label;
        }

        public String getDescription() {
            return this.description;
        }

        public boolean isOriginallyAvailable() {
            return this.originallyAvailable;
        }

        public boolean isAvailable() {
            return this.available;
        }

        public void setAvailable(boolean available) {
            this.available = available;
        }

        public boolean isChanged() {
            return this.available != this.originallyAvailable;
        }
    }
}
