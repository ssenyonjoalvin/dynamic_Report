package org.pahappa.systems.core.services.reporting;

import org.pahappa.systems.reporting.support.GenericService;
import org.pahappa.systems.models.reporting.TableVisibilityConfig;

import java.util.List;

public interface TableVisibilityService extends GenericService<TableVisibilityConfig> {

    List<TableVisibilityConfig> getAll();

    TableVisibilityConfig get(String tableName);

    List<String> getVisibleTables();

    List<DatabaseTableDiscoveryService.DiscoveredColumn> getVisibleColumns(String tableName);
}
