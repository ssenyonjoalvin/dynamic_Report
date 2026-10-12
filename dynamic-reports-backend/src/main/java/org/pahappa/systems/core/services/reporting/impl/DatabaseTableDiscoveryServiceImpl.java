package org.pahappa.systems.core.services.reporting.impl;

import org.pahappa.systems.core.services.reporting.DatabaseTableDiscoveryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service("kpiDatabaseTableDiscoveryService")
public class DatabaseTableDiscoveryServiceImpl implements DatabaseTableDiscoveryService {

    /** This plugin's own tables all start with this prefix and are never offered as report tables. */
    private static final String OWN_TABLE_PREFIX = "dyn_report_";

    /** Other plugins' internal tables (dynamic-dashboards) that are not business data either. */
    private static final Set<String> EXCLUDED_TABLES = new HashSet<String>();

    static {
        EXCLUDED_TABLES.add("saved_reports");
        EXCLUDED_TABLES.add("table_display_config");
    }

    @Autowired
    private DataSource dataSource;

    @Override
    public List<String> listTables() {
        List<String> tables = new ArrayList<String>();
        try (Connection connection = this.dataSource.getConnection()) {
            DatabaseMetaData meta = connection.getMetaData();
            try (ResultSet rs = meta.getTables(connection.getCatalog(), null, "%", new String[]{"TABLE"})) {
                while (rs.next()) {
                    String tableName = rs.getString("TABLE_NAME");
                    if (tableName != null && !tableName.toLowerCase().startsWith(OWN_TABLE_PREFIX)
                            && !EXCLUDED_TABLES.contains(tableName.toLowerCase())) {
                        tables.add(tableName);
                    }
                }
            }
        } catch (Exception e) {
            return new ArrayList<String>();
        }
        java.util.Collections.sort(tables);
        return tables;
    }

    @Override
    public List<DiscoveredColumn> discoverColumns(String tableName) {
        List<DiscoveredColumn> columns = new ArrayList<DiscoveredColumn>();
        if (tableName == null || tableName.trim().isEmpty()) {
            return columns;
        }
        try (Connection connection = this.dataSource.getConnection()) {
            DatabaseMetaData meta = connection.getMetaData();
            String catalog = connection.getCatalog();

            java.util.Map<String, String[]> foreignKeys = new java.util.HashMap<String, String[]>();
            try (ResultSet fk = meta.getImportedKeys(catalog, null, tableName)) {
                while (fk.next()) {
                    String fkColumn = fk.getString("FKCOLUMN_NAME");
                    String targetTable = fk.getString("PKTABLE_NAME");
                    String targetColumn = fk.getString("PKCOLUMN_NAME");
                    foreignKeys.put(fkColumn, new String[]{targetTable, targetColumn});
                }
            }

            try (ResultSet rs = meta.getColumns(catalog, null, tableName, "%")) {
                while (rs.next()) {
                    String columnName = rs.getString("COLUMN_NAME");
                    String typeName = rs.getString("TYPE_NAME");
                    String[] fkTarget = foreignKeys.get(columnName);
                    if (fkTarget != null) {
                        columns.add(new DiscoveredColumn(columnName, typeName, true, fkTarget[0], fkTarget[1]));
                    } else {
                        columns.add(new DiscoveredColumn(columnName, typeName, false, null, null));
                    }
                }
            }
        } catch (Exception e) {
            return new ArrayList<DiscoveredColumn>();
        }
        return columns;
    }
}