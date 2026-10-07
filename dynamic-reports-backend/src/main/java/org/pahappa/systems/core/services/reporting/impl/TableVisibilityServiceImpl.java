package org.pahappa.systems.core.services.reporting.impl;

import com.googlecode.genericdao.search.Search;
import org.pahappa.systems.reporting.support.GenericServiceImpl;
import org.pahappa.systems.core.services.reporting.DatabaseTableDiscoveryService;
import org.pahappa.systems.core.services.reporting.TableVisibilityService;
import org.pahappa.systems.models.reporting.TableVisibilityConfig;
import org.pahappa.systems.reporting.support.Validate;
import org.sers.webutils.model.RecordStatus;
import org.sers.webutils.model.exception.OperationFailedException;
import org.sers.webutils.model.exception.ValidationFailedException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service("kpiTableVisibilityService")
@Transactional
public class TableVisibilityServiceImpl extends GenericServiceImpl<TableVisibilityConfig> implements TableVisibilityService {

    @Autowired
    private DatabaseTableDiscoveryService databaseTableDiscoveryService;

    @Override
    public boolean isDeletable(TableVisibilityConfig tableVisibilityConfig) throws OperationFailedException {
        return true;
    }

    @Override
    public TableVisibilityConfig saveInstance(TableVisibilityConfig tableVisibilityConfig) throws ValidationFailedException, OperationFailedException {
        Validate.notNull(tableVisibilityConfig, "Table visibility config cannot be null");
        Validate.hasText(tableVisibilityConfig.getTableName(), "A table name is required");
        return super.save(tableVisibilityConfig);
    }

    @Override
    public List<TableVisibilityConfig> getAll() {
        Search search = new Search();
        search.addFilterEqual("recordStatus", RecordStatus.ACTIVE);
        search.addSortAsc("tableName");
        return super.search(search);
    }

    @Override
    public TableVisibilityConfig get(String tableName) {
        if (tableName == null) {
            return null;
        }
        Search search = new Search();
        search.addFilterEqual("recordStatus", RecordStatus.ACTIVE);
        search.addFilterEqual("tableName", tableName);
        return super.searchUnique(search);
    }

    @Override
    public List<String> getVisibleTables() {
        List<String> visible = new ArrayList<String>();
        for (String tableName : this.databaseTableDiscoveryService.listTables()) {
            TableVisibilityConfig config = get(tableName);
            if (config != null && config.isVisible()) {
                visible.add(tableName);
            }
        }
        return visible;
    }

    @Override
    public List<DatabaseTableDiscoveryService.DiscoveredColumn> getVisibleColumns(String tableName) {
        List<DatabaseTableDiscoveryService.DiscoveredColumn> all = this.databaseTableDiscoveryService.discoverColumns(tableName);
        TableVisibilityConfig config = get(tableName);
        if (config == null || config.getHiddenColumns() == null || config.getHiddenColumns().isEmpty()) {
            return all;
        }
        List<DatabaseTableDiscoveryService.DiscoveredColumn> visible = new ArrayList<DatabaseTableDiscoveryService.DiscoveredColumn>();
        for (DatabaseTableDiscoveryService.DiscoveredColumn column : all) {
            if (!config.getHiddenColumns().contains(column.getColumnName())) {
                visible.add(column);
            }
        }
        return visible;
    }
}