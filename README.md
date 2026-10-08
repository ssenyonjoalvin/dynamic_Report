# Dynamic Reports

A drop-in report builder for Pahappa / webutils-based JSF + Spring + Hibernate systems.
End users build their own tabular reports from the host system's data: they pick a source,
choose and order the columns, then add filters, sorting and calculated columns. They can preview
the report, save it and export it to CSV, with no developer work per report.

## In short, for integrators

1. Add the two Maven dependencies (one for the backend, one for the frontend).
2. Add `dynamic-reports.properties` with the package(s) holding your JPA entities.
3. Create a few XHTML pages in your own template that drop in the `rpt:` composite components.

That is the whole integration: **poms, one properties file and XHTML pages.** The host writes no
Java (no registries, controllers or navigation beans). The plugin finds your entities itself,
creates its own tables and checks admin access itself.

## What's inside

| Module | Artifact | Contains |
|---|---|---|
| `dynamic-reports-backend` | `org.pahappa.systems:dynamic-reports-backend:1.0-SNAPSHOT` | JPA entities for saved reports and their config (`org.pahappa.systems.models.reporting`), Spring services for entity discovery, query building and execution (`org.pahappa.systems.core.services.reporting`), and support classes (`org.pahappa.systems.reporting.support`) |
| `dynamic-reports-frontend` | `org.pahappa.systems:dynamic-reports-frontend:1.0-SNAPSHOT` | JSF managed beans (`org.pahappa.systems.views.reporting`), the composite components and the stylesheet under `META-INF/resources/reporting/` |

### Features
- **Report sources.** Every concrete `BaseEntity` in your configured model packages becomes a
  source automatically. Admins switch sources on or off on the Report Sources page. These are always
  excluded: webutils framework entities, the plugin's own tables, and entities whose names look
  like credentials (`password`, `otp`, `token`, `secret`, `salt`).
- **Columns.** Pick fields from the source and from the entities it links to. Drag (or use the
  arrows) to set the left-to-right order, and optionally give a column a custom header.
- **Filters.** Joined with AND/OR, using operators suited to each field's type (text, number,
  date, boolean, enum, reference): equals, contains, starts/ends with, ranges and `BETWEEN`,
  `IN`/`NOT IN`, null/empty checks.
- **Sorting** on multiple fields.
- **Calculated columns** from two operands: add, subtract, absolute difference, multiply, divide,
  percentage, percent change, average, min, max, modulo, power, days between two dates, and
  concatenate.
- **Preview** with paging, **save** for later, and **CSV export**.

## Requirements

The plugin is built against, and expects the host to provide:

- Java 8
- JSF 2.2 (Mojarra) and PrimeFaces 7.0
- Spring 3.1 and Hibernate 3.5 (JPA)
- webutils (`org.sers.webutils`), for `BaseEntity`, `User` and the logged-in user
- genericdao (`com.googlecode.genericdao`)

## Integration

### 1. Build and install the plugin

```bash
cd dynamic-reports-backend  && mvn install
cd ../dynamic-reports-frontend && mvn install
```

(Or deploy both artifacts to your Maven repository.)

### 2. Add the dependencies

In the host's **backend/core** module `pom.xml`:

```xml
<dependency>
    <groupId>org.pahappa.systems</groupId>
    <artifactId>dynamic-reports-backend</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

In the host's **web/frontend** module `pom.xml`:

```xml
<!-- Dynamic Reports (composite components + backing beans) -->
<dependency>
    <groupId>org.pahappa.systems</groupId>
    <artifactId>dynamic-reports-frontend</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

The plugin's services, entities and beans all live under `org.pahappa.systems`. A webutils-based
host's Spring component scan and JPA entity scan normally already cover that package, so they are
picked up with no extra configuration. If your host scans narrower packages, add
`org.pahappa.systems.core.services.reporting`, `org.pahappa.systems.reporting.support` (Spring) and
`org.pahappa.systems.models.reporting` (JPA) to those scans. The plugin's tables are all prefixed
`kpi_` (e.g. `kpi_saved_reports`) and are created by Hibernate's schema update like any other entity.

### 3. Tell the plugin where your models are

Create `dynamic-reports.properties` at the root of the classpath, e.g. `src/main/resources/` of
the web module:

```properties
# Comma-separated packages holding this system's own JPA entities. Only entities
# under these packages are offered as report sources; entities from other plugins
# on the classpath (dynamic dashboards, webutils, ...) are ignored.
dynamic.reports.model.packages=org.pahappa.systems.mnerequisition.core.models
```

- Sub-packages are included, and you can list several packages separated by commas.
- A JVM system property with the same name (`-Ddynamic.reports.model.packages=...`) overrides the file.
- If the property is missing, every mapped entity in the persistence unit becomes a source,
  including other plugins' entities. Always set it.

### 4. Add the pages

Declare the component namespace and place the components inside your own template. These are
the pages from M&E Requisition:

**Saved reports list**, e.g. `/requisition/reporting/CustomReportsView.xhtml`:

```xml
<ui:composition xmlns="http://www.w3.org/1999/xhtml"
                xmlns:ui="http://java.sun.com/jsf/facelets"
                xmlns:rpt="http://xmlns.jcp.org/jsf/composite/reporting"
                template="/requisition/template/template.xhtml">
    <ui:define name="content">
        <rpt:savedReports id="rptList" editorPage="/requisition/reporting/ReportEditorView" />
    </ui:define>
</ui:composition>
```

**Report editor**, e.g. `/requisition/reporting/ReportEditorView.xhtml`:

```xml
<rpt:reportEditor id="rptEditor" listPage="/requisition/reporting/CustomReportsView" />
```

**Report sources (admin)**, e.g. `/requisition/reporting/ReportSourcesAdminView.xhtml`:

```xml
<rpt:reportSourcesAdmin id="rptSources" />
```

Then link to these pages from your menus with plain outcomes or URLs, for example:

```xml
<p:menuitem value="Reports" url="/requisition/reporting/CustomReportsView.xhtml" icon="fa fa-chart-bar" />

<p:commandLink value="Report Sources"
               action="/requisition/reporting/ReportSourcesAdminView.xhtml?faces-redirect=true"
               rendered="#{reportingAccessBean.administrator}" />
```

### Component reference

| Component | Attributes | Purpose |
|---|---|---|
| `rpt:savedReports` | `editorPage`: outcome of your editor page (default `ReportEditor`) | The current user's saved reports: open, run, create and delete. Opens the editor with `?reportId=…` (plus `&mode=run` to land on the preview). |
| `rpt:reportEditor` | `listPage`: outcome of your saved-reports page (default `SavedReports`) | Builds, previews, saves and exports a report. With no `reportId` parameter it starts a new report. |
| `rpt:reportSourcesAdmin` | none | Admin-only: switch discovered report sources on or off. |
| `rpt:reportDatasetAdmin`, `rpt:tableVisibilityAdmin` | none | Older dataset- and table-based configuration screens, kept for existing setups. Admin-only. |

`editorPage` and `listPage` must match the view IDs of the pages you created, without `.xhtml`.

### Access control

`reportingAccessBean.administrator` (session-scoped and provided by the plugin) is `true` when
the logged-in webutils `User` has administrative privileges. The admin components check it
themselves. Use it in your own menus to hide admin links from other users.

## Optional: extending in code

None of this is needed for a normal integration:

- **`ReportSourceRegistry`** / **`EnumReportSourceRegistry`**: register hand-curated sources
  (better labels, descriptions or field exclusions). A registered source replaces the discovered
  one for the same entity.
- **`ReportableEntityRegistry`** / **`AnnotationScanningReportableEntityRegistry`**: add
  entity classes to the "Approved Entity" whitelist used by the older dataset screens, on top of
  those found in `dynamic.reports.model.packages`.

Expose an implementation as a Spring `@Service` in a package your component scan covers.

## Troubleshooting

- **Entities from other plugins show up as sources:** `dynamic.reports.model.packages` is missing,
  misspelled, or the file isn't on the classpath root. Check that `dynamic-reports.properties`
  ends up in `WEB-INF/classes` or at the root of your web jar. The list is read once per
  deployment, so redeploy after changing it.
- **"You do not have permission to view this page":** the logged-in user lacks administrative
  privileges. This is expected for the admin components.
- **Unknown tag `rpt:...`:** the frontend jar is missing from the deployed WAR, or the namespace
  isn't exactly `http://xmlns.jcp.org/jsf/composite/reporting`.
