package org.pahappa.systems.views.reporting;

import org.sers.webutils.model.security.User;
import org.sers.webutils.server.shared.SharedAppData;

import javax.annotation.PostConstruct;
import javax.faces.bean.ManagedBean;
import javax.faces.bean.SessionScoped;
import java.io.Serializable;

/**
 * Self-contained admin check backing the reporting admin composites
 * (reportDatasetAdmin.xhtml, tableVisibilityAdmin.xhtml). Kept local to this
 * module so dynamic-reports-frontend does not need a host project's own
 * component-renderer bean to gate its admin screens.
 */
@ManagedBean(name = "reportingAccessBean")
@SessionScoped
public class ReportingAccessBean implements Serializable {

    private static final long serialVersionUID = 1L;

    private boolean administrator = false;

    @PostConstruct
    public void init() {
        User loggedInUser = SharedAppData.getLoggedInUser();
        if (loggedInUser != null) {
            this.administrator = loggedInUser.hasAdministrativePrivileges();
        }
    }

    public boolean isAdministrator() {
        return administrator;
    }
}
