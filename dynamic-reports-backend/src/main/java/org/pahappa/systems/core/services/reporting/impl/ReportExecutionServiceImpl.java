package org.pahappa.systems.core.services.reporting.impl;

import org.pahappa.systems.core.services.reporting.DatabaseTableDiscoveryService;
import org.pahappa.systems.core.services.reporting.EntityDiscoveryService;
import org.pahappa.systems.core.services.reporting.ReportDatasetColumnService;
import org.pahappa.systems.core.services.reporting.ReportExecutionService;
import org.pahappa.systems.core.services.reporting.ReportRowEnrichmentStrategy;
import org.pahappa.systems.models.reporting.FilterOperator;
import org.pahappa.systems.models.reporting.ReportDataset;
import org.pahappa.systems.models.reporting.ReportDatasetColumn;
import org.pahappa.systems.models.reporting.ReportFilter;
import org.pahappa.systems.models.reporting.ReportSort;
import org.pahappa.systems.models.reporting.SavedReport;
import org.pahappa.systems.reporting.support.SqlIdentifierQuoter;
import org.sers.webutils.model.BaseEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.Query;
import javax.sql.DataSource;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service("kpiReportExecutionService")
@Transactional
public class ReportExecutionServiceImpl implements ReportExecutionService {

    private static final int DEFAULT_PAGE_SIZE = 50;
    private static final int MAX_PAGE_SIZE = 500;
    private static final int MAX_EXPORT_ROWS = 20000;

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private EntityDiscoveryService entityDiscoveryService;

    @Autowired
    private DatabaseTableDiscoveryService databaseTableDiscoveryService;

    @Autowired
    private ReportDatasetColumnService reportDatasetColumnService;

    @Autowired(required = false)
    private List<ReportRowEnrichmentStrategy> enrichmentStrategies;

    @Override
    public List<ReportField> availableFields(ReportDataset dataset) {
        List<ReportField> fields = new ArrayList<ReportField>();
        if (dataset == null) {
            return fields;
        }
        List<ReportDatasetColumn> columns = this.reportDatasetColumnService.getForDataset(dataset);
        for (ReportDatasetColumn column : columns) {
            if (!column.isSelectable() && !column.isFilterable() && !column.isSortable()) {
                continue;
            }
            ReportField field = new ReportField();
            field.setKey(column.getFieldName());
            field.setLabel(column.getLabel() != null && !column.getLabel().isEmpty() ? column.getLabel() : column.getFieldName());
            field.setType(column.getFieldType());
            field.setSelectable(column.isSelectable());
            field.setFilterable(column.isFilterable());
            field.setSortable(column.isSortable());
            if (!field.isSelectable()) {
                // Defensive: a non-selectable field must never be offered as
                // filterable/sortable, even if older persisted data says otherwise.
                field.setFilterable(false);
                field.setSortable(false);
            }
            Class<?> fieldType = resolveType(column.getFieldType());
            if (fieldType != null && fieldType.isEnum()) {
                field.setEnumType(true);
                field.setEnumValues(enumConstantNames(fieldType));
            } else if (fieldType != null && java.util.Date.class.isAssignableFrom(fieldType)) {
                field.setDateType(true);
            } else if (fieldType != null && BaseEntity.class.isAssignableFrom(fieldType)) {
                field.setReference(true);
                field.setReferenceValues(loadReferenceOptions(fieldType));
            }
            fields.add(field);
        }
        return fields;
    }

    @Override
    public ReportResult execute(SavedReport report, int page, int pageSize) {
        if (report == null || report.getDataset() == null) {
            throw new IllegalArgumentException("Report requires a dataset.");
        }
        ReportDataset dataset = report.getDataset();
        if (dataset.isTableSourced()) {
            return executeTableQuery(dataset, report, page, pageSize);
        }
        return executeEntityQuery(dataset, report, page, pageSize);
    }

    private ReportResult executeEntityQuery(ReportDataset dataset, SavedReport report, int page, int pageSize) {
        Class<?> entityClass = this.entityDiscoveryService.resolveApprovedClass(dataset.getEntityClassName());
        if (entityClass == null) {
            throw new IllegalArgumentException("Dataset entity is not on the approved reporting whitelist.");
        }

        Map<String, ReportDatasetColumn> byField = new HashMap<String, ReportDatasetColumn>();
        for (ReportDatasetColumn column : this.reportDatasetColumnService.getForDataset(dataset)) {
            byField.put(column.getFieldName(), column);
        }

        List<String> selectFields = validSelectFields(report.getSelectedColumns(), byField);
        if (selectFields.isEmpty()) {
            throw new IllegalArgumentException("Report has no selectable columns configured.");
        }

        Set<String> involvedFields = new HashSet<String>(selectFields);
        if (report.getFilters() != null) {
            for (ReportFilter filter : report.getFilters()) {
                if (filter.getFieldName() != null) {
                    involvedFields.add(filter.getFieldName());
                }
            }
        }
        if (report.getSort() != null) {
            for (ReportSort sort : report.getSort()) {
                if (sort.getFieldName() != null) {
                    involvedFields.add(sort.getFieldName());
                }
            }
        }

        Set<String> persistent = persistentProperties(entityClass);
        boolean needsInMemoryProjection = false;
        for (String field : involvedFields) {
            if (!persistent.contains(field)) {
                needsInMemoryProjection = true;
                break;
            }
        }
        // JPQL cannot reference @Transient (non-persistent) properties, so any
        // report that touches such a field must be filtered, sorted and projected
        // in Java where getters (computed values) are available.
        if (needsInMemoryProjection) {
            return executeEntityQueryInMemory(entityClass, report, byField, selectFields, persistent, page, pageSize);
        }

        StringBuilder whereClause = new StringBuilder();
        List<Object> boundValues = new ArrayList<Object>();
        buildWhereClause(report.getFilters(), byField, whereClause, boundValues);

        StringBuilder joinClause = new StringBuilder();
        Map<String, String> lookupAliasByField = buildEntityLookupJoins(involvedFields, byField, joinClause);

        StringBuilder orderClause = new StringBuilder();
        buildOrderClause(report.getSort(), byField, lookupAliasByField, orderClause);

        String entityName = entityClass.getSimpleName();
        StringBuilder selectHql = new StringBuilder("SELECT ");
        for (int i = 0; i < selectFields.size(); i++) {
            if (i > 0) {
                selectHql.append(", ");
            }
            selectHql.append(hqlFieldPath(selectFields.get(i), byField, lookupAliasByField));
        }
        selectHql.append(" FROM ").append(entityName).append(" e").append(joinClause);
        if (whereClause.length() > 0) {
            selectHql.append(" WHERE ").append(whereClause);
        }
        selectHql.append(orderClause);

        Query dataQuery = this.entityManager.createQuery(selectHql.toString());
        bindParameters(dataQuery, boundValues);

        int safePageSize = pageSize < 1 ? DEFAULT_PAGE_SIZE : Math.min(pageSize, MAX_PAGE_SIZE);
        int safePage = page < 1 ? 1 : page;
        dataQuery.setFirstResult((safePage - 1) * safePageSize);
        dataQuery.setMaxResults(safePageSize);

        List<?> rawRows = dataQuery.getResultList();

        StringBuilder countHql = new StringBuilder("SELECT COUNT(e) FROM ").append(entityName).append(" e");
        if (whereClause.length() > 0) {
            countHql.append(" WHERE ").append(whereClause);
        }
        Query countQuery = this.entityManager.createQuery(countHql.toString());
        bindParameters(countQuery, boundValues);
        Number total = (Number) countQuery.getSingleResult();

        ReportResult result = new ReportResult();
        result.setColumnKeys(selectFields);
        Map<String, String> labels = new LinkedHashMap<String, String>();
        java.util.Map<String, String> aliases = report.getColumnAliases();
        for (String field : selectFields) {
            ReportDatasetColumn column = byField.get(field);
            String alias = aliases != null ? aliases.get(field) : null;
            String label = alias != null && !alias.trim().isEmpty() ? alias
                    : (column != null && column.getLabel() != null && !column.getLabel().isEmpty() ? column.getLabel() : field);
            labels.put(field, label);
        }
        result.setColumnLabels(labels);
        result.setRows(toRowMaps(rawRows, selectFields));
        result.setTotalCount(total != null ? total.longValue() : 0L);
        return result;
    }

    private ReportResult executeEntityQueryInMemory(Class<?> entityClass, SavedReport report,
                                                     Map<String, ReportDatasetColumn> byField,
                                                     List<String> selectFields, Set<String> persistent,
                                                     int page, int pageSize) {
        String entityName = entityClass.getSimpleName();

        List<ReportFilter> sqlFilters = new ArrayList<ReportFilter>();
        List<ReportFilter> memoryFilters = new ArrayList<ReportFilter>();
        if (report.getFilters() != null) {
            for (ReportFilter filter : report.getFilters()) {
                ReportDatasetColumn column = byField.get(filter.getFieldName());
                if (column == null || !column.isSelectable() || !column.isFilterable()) {
                    continue;
                }
                if (persistent.contains(filter.getFieldName())) {
                    sqlFilters.add(filter);
                } else {
                    memoryFilters.add(filter);
                }
            }
        }

        StringBuilder whereClause = new StringBuilder();
        List<Object> boundValues = new ArrayList<Object>();
        buildWhereClause(sqlFilters, byField, whereClause, boundValues);

        StringBuilder selectHql = new StringBuilder("SELECT e FROM ").append(entityName).append(" e");
        if (whereClause.length() > 0) {
            selectHql.append(" WHERE ").append(whereClause);
        }
        Query query = this.entityManager.createQuery(selectHql.toString());
        bindParameters(query, boundValues);
        List<?> all = query.getResultList();

        Set<String> selectFieldSet = new HashSet<String>(selectFields);
        hydrateTransientAssociations(entityClass, all, selectFieldSet);

        List<Object> filtered = new ArrayList<Object>();
        if (memoryFilters.isEmpty()) {
            for (Object entity : all) {
                filtered.add(entity);
            }
        } else {
            for (Object entity : all) {
                boolean matches = true;
                for (ReportFilter filter : memoryFilters) {
                    Object fieldValue = propertyValue(entity, filter.getFieldName());
                    if (!matchesInMemory(filter, byField.get(filter.getFieldName()), fieldValue)) {
                        matches = false;
                        break;
                    }
                }
                if (matches) {
                    filtered.add(entity);
                }
            }
        }

        if (report.getSort() != null && !report.getSort().isEmpty()) {
            sortInMemory(filtered, report.getSort());
        }

        long totalCount = filtered.size();
        int safePageSize = pageSize < 1 ? DEFAULT_PAGE_SIZE : Math.min(pageSize, MAX_PAGE_SIZE);
        int safePage = page < 1 ? 1 : page;
        int from = (safePage - 1) * safePageSize;
        int to = Math.min(from + safePageSize, filtered.size());

        List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
        if (from < filtered.size()) {
            for (Object entity : filtered.subList(from, to)) {
                Map<String, Object> row = new LinkedHashMap<String, Object>();
                for (String field : selectFields) {
                    row.put(field, presentValue(propertyValue(entity, field)));
                }
                rows.add(row);
            }
        }

        ReportResult result = new ReportResult();
        result.setColumnKeys(selectFields);
        Map<String, String> labels = new LinkedHashMap<String, String>();
        java.util.Map<String, String> aliases = report.getColumnAliases();
        for (String field : selectFields) {
            ReportDatasetColumn column = byField.get(field);
            String alias = aliases != null ? aliases.get(field) : null;
            String label = alias != null && !alias.trim().isEmpty() ? alias
                    : (column != null && column.getLabel() != null && !column.getLabel().isEmpty() ? column.getLabel() : field);
            labels.put(field, label);
        }
        result.setColumnLabels(labels);
        result.setRows(rows);
        result.setTotalCount(totalCount);
        return result;
    }

    private Set<String> persistentProperties(Class<?> entityClass) {
        Set<String> persistent = new HashSet<String>();
        if (entityClass == null) {
            return persistent;
        }
        try {
            for (PropertyDescriptor descriptor : Introspector.getBeanInfo(entityClass, Object.class).getPropertyDescriptors()) {
                Method readMethod = descriptor.getReadMethod();
                if (readMethod == null || readMethod.isAnnotationPresent(javax.persistence.Transient.class)) {
                    continue;
                }
                persistent.add(descriptor.getName());
            }
        } catch (Exception e) {
            // fall through with an empty set - caller falls back to in-memory projection
        }
        return persistent;
    }

    private Object propertyValue(Object bean, String property) {
        if (bean == null || property == null) {
            return null;
        }
        try {
            for (PropertyDescriptor descriptor : Introspector.getBeanInfo(bean.getClass(), Object.class).getPropertyDescriptors()) {
                if (descriptor.getName().equals(property) && descriptor.getReadMethod() != null) {
                    return descriptor.getReadMethod().invoke(bean);
                }
            }
        } catch (Exception e) {
            return null;
        }
        return null;
    }

    private void hydrateTransientAssociations(Class<?> entityClass, List<?> entities, Set<String> involvedFields) {
        if (entityClass == null || entities == null || entities.isEmpty() || this.enrichmentStrategies == null) {
            return;
        }
        for (ReportRowEnrichmentStrategy strategy : this.enrichmentStrategies) {
            if (strategy.supports(entityClass)) {
                strategy.enrich(entityClass, entities, involvedFields);
            }
        }
    }

    private boolean matchesInMemory(ReportFilter filter, ReportDatasetColumn column, Object fieldValue) {
        if (filter == null) {
            return true;
        }
        FilterOperator op = filter.getOperator() == null ? FilterOperator.EQUALS : filter.getOperator();
        if (op == FilterOperator.IS_NULL) {
            return fieldValue == null;
        }
        if (op == FilterOperator.IS_NOT_NULL) {
            return fieldValue != null;
        }
        Class<?> columnType = column == null ? null : resolveType(column.getFieldType());
        if (columnType != null && BaseEntity.class.isAssignableFrom(columnType) && !columnType.isEnum()) {
            Object fieldRef = fieldValue instanceof BaseEntity ? ((BaseEntity) fieldValue).getId()
                    : (fieldValue == null ? null : String.valueOf(fieldValue));
            switch (op) {
                case EQUALS:
                    return equalsInMemory(fieldRef, filter.getValue());
                case NOT_EQUALS:
                    return !equalsInMemory(fieldRef, filter.getValue());
                case IN:
                    for (String part : splitCsv(filter.getValue())) {
                        if (equalsInMemory(fieldRef, part.trim())) {
                            return true;
                        }
                    }
                    return false;
                default:
                    return true;
            }
        }
        Object converted = column == null ? filter.getValue() : typedFilterValue(column, filter.getValue());
        switch (op) {
            case EQUALS:
                return compareInMemory(fieldValue, converted) == 0;
            case NOT_EQUALS:
                return compareInMemory(fieldValue, converted) != 0;
            case CONTAINS:
                return fieldValue != null && String.valueOf(fieldValue).toLowerCase()
                        .contains(safeString(converted).toLowerCase());
            case STARTS_WITH:
                return fieldValue != null && String.valueOf(fieldValue).toLowerCase()
                        .startsWith(safeString(converted).toLowerCase());
            case GREATER_THAN:
                return compareInMemory(fieldValue, converted) > 0;
            case GREATER_OR_EQUAL:
                return compareInMemory(fieldValue, converted) >= 0;
            case LESS_THAN:
                return compareInMemory(fieldValue, converted) < 0;
            case LESS_OR_EQUAL:
                return compareInMemory(fieldValue, converted) <= 0;
            case BETWEEN:
                Object converted2 = column == null ? filter.getValue2() : typedFilterValue(column, filter.getValue2());
                return compareInMemory(fieldValue, converted) >= 0 && compareInMemory(fieldValue, converted2) <= 0;
            case IN:
                for (String part : splitCsv(filter.getValue())) {
                    if (compareInMemory(fieldValue, part.trim()) == 0) {
                        return true;
                    }
                }
                return false;
            default:
                return true;
        }
    }

    private boolean equalsInMemory(Object a, Object b) {
        if (a == null && b == null) {
            return true;
        }
        if (a == null || b == null) {
            return false;
        }
        return String.valueOf(a).equals(String.valueOf(b));
    }

    private String safeString(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    @SuppressWarnings("unchecked")
    private int compareInMemory(Object a, Object b) {
        Object comparableA = comparableOf(a);
        Object comparableB = comparableOf(b);
        if (comparableA == null && comparableB == null) {
            return 0;
        }
        if (comparableA == null) {
            return 1;
        }
        if (comparableB == null) {
            return -1;
        }
        // Compare using the values' own natural ordering whenever possible -
        // falling back to lexicographic string comparison (as before) only
        // when the two sides aren't naturally comparable to each other. A
        // pure string comparison is wrong for numbers ("9" sorts after "10")
        // and was silently breaking every ordering operator on numeric and
        // date fields.
        if (comparableA instanceof Number && comparableB instanceof Number) {
            return Double.compare(((Number) comparableA).doubleValue(), ((Number) comparableB).doubleValue());
        }
        if (comparableA instanceof Date && comparableB instanceof Date) {
            return ((Date) comparableA).compareTo((Date) comparableB);
        }
        if (comparableA instanceof Boolean && comparableB instanceof Boolean) {
            return Boolean.compare((Boolean) comparableA, (Boolean) comparableB);
        }
        if (comparableA instanceof Comparable && comparableA.getClass().isInstance(comparableB)) {
            return ((Comparable<Object>) comparableA).compareTo(comparableB);
        }
        return String.valueOf(comparableA).compareToIgnoreCase(String.valueOf(comparableB));
    }

    private Object comparableOf(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Enum) {
            return ((Enum) value).name();
        }
        if (value instanceof BaseEntity) {
            return entityDisplayValue(value);
        }
        // Dates, numbers, booleans and strings are left as their native,
        // naturally-Comparable type so compareInMemory can compare them
        // correctly instead of falling back to string comparison.
        return value;
    }

    private void sortInMemory(List<Object> rows, List<ReportSort> sortList) {
        List<ReportSort> sorted = new ArrayList<ReportSort>(sortList);
        Collections.sort(sorted, new Comparator<ReportSort>() {
            @Override
            public int compare(ReportSort a, ReportSort b) {
                return Integer.compare(a.getPriority(), b.getPriority());
            }
        });
        for (int i = sorted.size() - 1; i >= 0; i--) {
            final ReportSort sort = sorted.get(i);
            final boolean descending = "DESC".equalsIgnoreCase(sort.getDirection());
            Collections.sort(rows, new Comparator<Object>() {
                @Override
                public int compare(Object a, Object b) {
                    Object valueA = propertyValue(a, sort.getFieldName());
                    Object valueB = propertyValue(b, sort.getFieldName());
                    int comparison = compareInMemory(valueA, valueB);
                    return descending ? -comparison : comparison;
                }
            });
        }
    }

    private ReportResult executeTableQuery(ReportDataset dataset, SavedReport report, int page, int pageSize) {
        String tableName = dataset.getTableName();
        if (tableName == null || tableName.trim().isEmpty()) {
            throw new IllegalArgumentException("Dataset has no table configured.");
        }

        Map<String, ReportDatasetColumn> byField = new HashMap<String, ReportDatasetColumn>();
        for (ReportDatasetColumn column : this.reportDatasetColumnService.getForDataset(dataset)) {
            byField.put(column.getFieldName(), column);
        }

        Set<String> realColumns = new HashSet<String>();
        Map<String, DatabaseTableDiscoveryService.DiscoveredColumn> columnMeta = new HashMap<String, DatabaseTableDiscoveryService.DiscoveredColumn>();
        boolean hasRecordStatus = false;
        for (DatabaseTableDiscoveryService.DiscoveredColumn column : this.databaseTableDiscoveryService.discoverColumns(tableName)) {
            realColumns.add(column.getColumnName());
            columnMeta.put(column.getColumnName(), column);
            if ("record_status".equalsIgnoreCase(column.getColumnName())) {
                hasRecordStatus = true;
            }
        }

        List<String> selectFields = new ArrayList<String>();
        for (String field : validSelectFields(report.getSelectedColumns(), byField)) {
            if (realColumns.contains(field)) {
                selectFields.add(field);
            }
        }
        if (selectFields.isEmpty()) {
            throw new IllegalArgumentException("Report has no selectable columns configured.");
        }

        Set<String> fieldsInPlay = new HashSet<String>(selectFields);
        if (report.getFilters() != null) {
            for (ReportFilter filter : report.getFilters()) {
                fieldsInPlay.add(filter.getFieldName());
            }
        }
        if (report.getSort() != null) {
            for (ReportSort sort : report.getSort()) {
                fieldsInPlay.add(sort.getFieldName());
            }
        }

        StringBuilder joinClause = new StringBuilder();
        Map<String, String> lookupAliasByField = buildLookupJoins(fieldsInPlay, byField, columnMeta, joinClause);

        StringBuilder whereClause = new StringBuilder();
        List<Object> boundValues = new ArrayList<Object>();
        buildWhereClauseSql(report.getFilters(), byField, realColumns, lookupAliasByField, whereClause, boundValues);
        if (hasRecordStatus) {
            if (whereClause.length() > 0) {
                whereClause.append(" AND ");
            }
            whereClause.append("t.").append(SqlIdentifierQuoter.quoteIdentifier("record_status")).append(" = 0");
        }

        StringBuilder orderClause = new StringBuilder();
        buildOrderClauseSql(report.getSort(), byField, realColumns, lookupAliasByField, orderClause);

        StringBuilder fromClause = new StringBuilder(" FROM ").append(SqlIdentifierQuoter.quoteIdentifier(tableName))
                .append(" AS t").append(joinClause);

        StringBuilder selectSql = new StringBuilder("SELECT ");
        for (int i = 0; i < selectFields.size(); i++) {
            if (i > 0) {
                selectSql.append(", ");
            }
            selectSql.append(sqlFieldPath(selectFields.get(i), byField, lookupAliasByField));
        }
        selectSql.append(fromClause);
        if (whereClause.length() > 0) {
            selectSql.append(" WHERE ").append(whereClause);
        }
        selectSql.append(orderClause);
        selectSql.append(" LIMIT ? OFFSET ?");

        int safePageSize = pageSize < 1 ? DEFAULT_PAGE_SIZE : Math.min(pageSize, MAX_PAGE_SIZE);
        int safePage = page < 1 ? 1 : page;

        List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
        long total = 0L;
        try (Connection connection = this.dataSource.getConnection()) {
            try (PreparedStatement ps = connection.prepareStatement(selectSql.toString())) {
                int paramIndex = 1;
                for (Object value : boundValues) {
                    ps.setObject(paramIndex++, value);
                }
                ps.setInt(paramIndex++, safePageSize);
                ps.setInt(paramIndex, (safePage - 1) * safePageSize);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> row = new LinkedHashMap<String, Object>();
                        for (int i = 0; i < selectFields.size(); i++) {
                            row.put(selectFields.get(i), rs.getObject(i + 1));
                        }
                        rows.add(row);
                    }
                }
            }

            StringBuilder countSql = new StringBuilder("SELECT COUNT(*)").append(fromClause);
            if (whereClause.length() > 0) {
                countSql.append(" WHERE ").append(whereClause);
            }
            try (PreparedStatement ps = connection.prepareStatement(countSql.toString())) {
                int paramIndex = 1;
                for (Object value : boundValues) {
                    ps.setObject(paramIndex++, value);
                }
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        total = rs.getLong(1);
                    }
                }
            }
        } catch (java.sql.SQLException e) {
            throw new RuntimeException("Failed to execute report query: " + e.getMessage(), e);
        }

        ReportResult result = new ReportResult();
        result.setColumnKeys(selectFields);
        Map<String, String> labels = new LinkedHashMap<String, String>();
        java.util.Map<String, String> aliases = report.getColumnAliases();
        for (String field : selectFields) {
            ReportDatasetColumn column = byField.get(field);
            String alias = aliases != null ? aliases.get(field) : null;
            String label = alias != null && !alias.trim().isEmpty() ? alias
                    : (column != null && column.getLabel() != null && !column.getLabel().isEmpty() ? column.getLabel() : field);
            labels.put(field, label);
        }
        result.setColumnLabels(labels);
        result.setRows(rows);
        result.setTotalCount(total);
        return result;
    }

    private Map<String, String> buildLookupJoins(Set<String> fieldsInPlay, Map<String, ReportDatasetColumn> byField,
                                                   Map<String, DatabaseTableDiscoveryService.DiscoveredColumn> columnMeta,
                                                   StringBuilder joinClause) {
        Map<String, String> aliasByField = new HashMap<String, String>();
        for (String field : fieldsInPlay) {
            ReportDatasetColumn column = byField.get(field);
            DatabaseTableDiscoveryService.DiscoveredColumn meta = columnMeta.get(field);
            if (column == null || meta == null || !column.isLookupEnabled()) {
                continue;
            }
            ReportDataset target = column.getLookupTargetDataset();
            String displayField = column.getLookupDisplayField();
            if (target == null || !target.isTableSourced() || target.getTableName() == null
                    || target.getTableName().trim().isEmpty()
                    || displayField == null || displayField.trim().isEmpty()) {
                continue;
            }
            String referencedColumn = meta.getReferencedColumn() != null && !meta.getReferencedColumn().trim().isEmpty()
                    ? meta.getReferencedColumn() : "id";
            String alias = "lk_" + field;
            joinClause.append(" LEFT JOIN ").append(SqlIdentifierQuoter.quoteIdentifier(target.getTableName()))
                    .append(" AS ").append(alias)
                    .append(" ON ").append(alias).append(".").append(SqlIdentifierQuoter.quoteIdentifier(referencedColumn))
                    .append(" = t.").append(SqlIdentifierQuoter.quoteIdentifier(field));
            aliasByField.put(field, alias);
        }
        return aliasByField;
    }

    private String sqlFieldPath(String field, Map<String, ReportDatasetColumn> byField, Map<String, String> lookupAliasByField) {
        String alias = lookupAliasByField.get(field);
        ReportDatasetColumn column = byField.get(field);
        if (alias != null && column != null && column.getLookupDisplayField() != null) {
            return alias + "." + SqlIdentifierQuoter.quoteIdentifier(column.getLookupDisplayField().trim());
        }
        return "t." + SqlIdentifierQuoter.quoteIdentifier(field);
    }

    @Override
    public byte[] exportCsv(SavedReport report) {
        StringBuilder csv = new StringBuilder();
        ReportResult firstPage = execute(report, 1, MAX_PAGE_SIZE);
        List<String> columnKeys = firstPage.getColumnKeys();
        Map<String, String> labels = firstPage.getColumnLabels();
        for (int i = 0; i < columnKeys.size(); i++) {
            if (i > 0) {
                csv.append(',');
            }
            String key = columnKeys.get(i);
            String label = labels.get(key) != null ? labels.get(key) : key;
            csv.append(csvField(label));
        }
        csv.append("\r\n");

        long totalCount = firstPage.getTotalCount();
        long rowsWritten = 0;
        int page = 1;
        ReportResult current = firstPage;
        while (true) {
            for (Map<String, Object> row : current.getRows()) {
                if (rowsWritten >= MAX_EXPORT_ROWS) {
                    break;
                }
                for (int i = 0; i < columnKeys.size(); i++) {
                    if (i > 0) {
                        csv.append(',');
                    }
                    Object value = row.get(columnKeys.get(i));
                    csv.append(csvField(value != null ? value.toString() : ""));
                }
                csv.append("\r\n");
                rowsWritten++;
            }
            long rowsSoFar = (long) (page - 1) * MAX_PAGE_SIZE + current.getRows().size();
            if (rowsWritten >= MAX_EXPORT_ROWS || rowsSoFar >= totalCount || current.getRows().isEmpty()) {
                break;
            }
            page++;
            current = execute(report, page, MAX_PAGE_SIZE);
        }

        try {
            return csv.toString().getBytes("UTF-8");
        } catch (java.io.UnsupportedEncodingException e) {
            return csv.toString().getBytes();
        }
    }

    private String csvField(String value) {
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    private List<String> validSelectFields(List<String> requested, Map<String, ReportDatasetColumn> byField) {
        List<String> valid = new ArrayList<String>();
        if (requested == null) {
            return valid;
        }
        for (String field : requested) {
            ReportDatasetColumn column = byField.get(field);
            if (column != null && column.isSelectable()) {
                valid.add(field);
            }
        }
        return valid;
    }

    private static final Map<String, Class<?>> PRIMITIVE_TYPES_BY_NAME = buildPrimitiveTypesByName();

    private static Map<String, Class<?>> buildPrimitiveTypesByName() {
        Map<String, Class<?>> map = new HashMap<String, Class<?>>();
        map.put("int", Integer.TYPE);
        map.put("long", Long.TYPE);
        map.put("short", Short.TYPE);
        map.put("byte", Byte.TYPE);
        map.put("double", Double.TYPE);
        map.put("float", Float.TYPE);
        map.put("boolean", Boolean.TYPE);
        map.put("char", Character.TYPE);
        return map;
    }

    private Class<?> resolveType(String className) {
        if (className == null || className.trim().isEmpty()) {
            return null;
        }
        String trimmed = className.trim();
        // Class.forName() cannot resolve primitive type names (e.g. "int",
        // "boolean") - a getter with a primitive return type reports its type
        // this way, so without this check every primitive-typed field would
        // silently fail every type-dependent feature below (filter value
        // coercion, enum/date detection, association detection, ...).
        Class<?> primitive = PRIMITIVE_TYPES_BY_NAME.get(trimmed);
        if (primitive != null) {
            return primitive;
        }
        try {
            return Class.forName(trimmed);
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    private List<String> enumConstantNames(Class<?> enumClass) {
        List<String> names = new ArrayList<String>();
        if (enumClass == null || !enumClass.isEnum()) {
            return names;
        }
        Object[] constants = enumClass.getEnumConstants();
        if (constants != null) {
            for (Object constant : constants) {
                names.add(((Enum<?>) constant).name());
            }
        }
        return names;
    }

    private List<FieldOption> loadReferenceOptions(Class<?> entityClass) {
        List<FieldOption> options = new ArrayList<FieldOption>();
        if (entityClass == null || !BaseEntity.class.isAssignableFrom(entityClass)) {
            return options;
        }
        try {
            Query query = this.entityManager.createQuery("FROM " + entityClass.getSimpleName());
            for (Object instance : query.getResultList()) {
                if (!(instance instanceof BaseEntity)) {
                    continue;
                }
                FieldOption option = new FieldOption();
                option.setId(((BaseEntity) instance).getId());
                option.setLabel(entityDisplayValue(instance));
                options.add(option);
            }
        } catch (RuntimeException e) {
            return new ArrayList<FieldOption>();
        }
        return options;
    }

    private Object typedFilterValue(ReportDatasetColumn column, Object value) {
        if (value == null || column == null) {
            return value;
        }
        Class<?> type = resolveType(column.getFieldType());
        if (type == null) {
            return value;
        }
        if (type.isEnum()) {
            try {
                return Enum.valueOf((Class<? extends Enum>) type, String.valueOf(value));
            } catch (IllegalArgumentException e) {
                return value;
            }
        }
        if (java.util.Date.class.isAssignableFrom(type)) {
            java.util.Date parsed = parseDateValue(String.valueOf(value));
            if (parsed != null) {
                return parsed;
            }
            return value;
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            // Leave blank as-is rather than throwing a NumberFormatException;
            // an empty filter value on a numeric field is a user input error,
            // not something we should crash the report over.
            return value;
        }
        // Equality binds happened to "work" for numeric/boolean columns before
        // this method handled them, because some JDBC drivers coerce a String
        // parameter for "=" comparisons leniently. Ordering comparisons
        // (>, <, BETWEEN, ...) and even "=" on stricter drivers do not get
        // that same leniency, so the bound parameter's Java type must actually
        // match the column's type.
        try {
            if (type == Integer.class || type == Integer.TYPE) {
                return Integer.valueOf(text);
            }
            if (type == Long.class || type == Long.TYPE) {
                return Long.valueOf(text);
            }
            if (type == Short.class || type == Short.TYPE) {
                return Short.valueOf(text);
            }
            if (type == Byte.class || type == Byte.TYPE) {
                return Byte.valueOf(text);
            }
            if (type == Double.class || type == Double.TYPE) {
                return Double.valueOf(text);
            }
            if (type == Float.class || type == Float.TYPE) {
                return Float.valueOf(text);
            }
            if (type == java.math.BigDecimal.class) {
                return new java.math.BigDecimal(text);
            }
            if (type == java.math.BigInteger.class) {
                return new java.math.BigInteger(text);
            }
            if (type == Boolean.class || type == Boolean.TYPE) {
                return Boolean.valueOf(text);
            }
        } catch (NumberFormatException e) {
            return value;
        }
        return value;
    }

    private java.util.Date parseDateValue(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        String[] patterns = {"yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd'T'HH:mm:ss", "yyyy-MM-dd", "dd/MM/yyyy"};
        for (String pattern : patterns) {
            try {
                return new java.text.SimpleDateFormat(pattern).parse(value.trim());
            } catch (java.text.ParseException e) {
                // try next pattern
            }
        }
        return null;
    }

    private void buildWhereClause(List<ReportFilter> filters, Map<String, ReportDatasetColumn> byField,
                                   StringBuilder whereClause, List<Object> boundValues) {
        if (filters == null) {
            return;
        }
        int paramIndex = 1;
        for (ReportFilter filter : filters) {
            ReportDatasetColumn column = byField.get(filter.getFieldName());
            if (column == null || !column.isSelectable() || !column.isFilterable()) {
                continue;
            }
            String path = hqlFilterPath(filter.getFieldName(), byField);
            if (whereClause.length() > 0) {
                whereClause.append(" AND ");
            }
            switch (filter.getOperator()) {
                case EQUALS:
                    whereClause.append(path).append(" = ?").append(paramIndex);
                    boundValues.add(typedFilterValue(column, filter.getValue()));
                    paramIndex++;
                    break;
                case NOT_EQUALS:
                    whereClause.append(path).append(" != ?").append(paramIndex);
                    boundValues.add(typedFilterValue(column, filter.getValue()));
                    paramIndex++;
                    break;
                case CONTAINS:
                    whereClause.append(path).append(" LIKE ?").append(paramIndex).append(" ESCAPE '\\'");
                    boundValues.add("%" + escapeLike(filter.getValue()) + "%");
                    paramIndex++;
                    break;
                case STARTS_WITH:
                    whereClause.append(path).append(" LIKE ?").append(paramIndex).append(" ESCAPE '\\'");
                    boundValues.add(escapeLike(filter.getValue()) + "%");
                    paramIndex++;
                    break;
                case GREATER_THAN:
                    whereClause.append(path).append(" > ?").append(paramIndex);
                    boundValues.add(typedFilterValue(column, filter.getValue()));
                    paramIndex++;
                    break;
                case GREATER_OR_EQUAL:
                    whereClause.append(path).append(" >= ?").append(paramIndex);
                    boundValues.add(typedFilterValue(column, filter.getValue()));
                    paramIndex++;
                    break;
                case LESS_THAN:
                    whereClause.append(path).append(" < ?").append(paramIndex);
                    boundValues.add(typedFilterValue(column, filter.getValue()));
                    paramIndex++;
                    break;
                case LESS_OR_EQUAL:
                    whereClause.append(path).append(" <= ?").append(paramIndex);
                    boundValues.add(typedFilterValue(column, filter.getValue()));
                    paramIndex++;
                    break;
                case BETWEEN:
                    whereClause.append(path).append(" BETWEEN ?").append(paramIndex).append(" AND ?").append(paramIndex + 1);
                    boundValues.add(typedFilterValue(column, filter.getValue()));
                    boundValues.add(typedFilterValue(column, filter.getValue2()));
                    paramIndex += 2;
                    break;
                case IN:
                    Class<?> inType = resolveType(column.getFieldType());
                    whereClause.append(path).append(" IN (").append(inPlaceholders(filter.getValue(), paramIndex)).append(")");
                    if (inType != null && inType.isEnum()) {
                        paramIndex = addInEnumValues(filter.getValue(), (Class<? extends Enum>) inType, boundValues, paramIndex);
                    } else {
                        paramIndex = addInValues(filter.getValue(), boundValues, paramIndex);
                    }
                    break;
                case IS_NULL:
                    whereClause.append(path).append(" IS NULL");
                    break;
                case IS_NOT_NULL:
                    whereClause.append(path).append(" IS NOT NULL");
                    break;
                default:
                    whereClause.append("1 = 1");
                    break;
            }
        }
    }

    private String escapeLike(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private String inPlaceholders(String csvValue, int startIndex) {
        String[] parts = splitCsv(csvValue);
        StringBuilder placeholders = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                placeholders.append(", ");
            }
            placeholders.append("?").append(startIndex + i);
        }
        return placeholders.toString();
    }

    private int addInValues(String csvValue, List<Object> boundValues, int startIndex) {
        String[] parts = splitCsv(csvValue);
        for (String part : parts) {
            boundValues.add(part.trim());
        }
        return startIndex + parts.length;
    }

    private int addInEnumValues(String csvValue, Class<? extends Enum> enumClass, List<Object> boundValues, int startIndex) {
        String[] parts = splitCsv(csvValue);
        for (String part : parts) {
            try {
                boundValues.add(Enum.valueOf(enumClass, part.trim()));
            } catch (IllegalArgumentException e) {
                boundValues.add(part.trim());
            }
        }
        return startIndex + parts.length;
    }

    private String[] splitCsv(String value) {
        if (value == null || value.trim().isEmpty()) {
            return new String[]{""};
        }
        return value.split(",");
    }

    private void buildOrderClause(List<ReportSort> sortList, Map<String, ReportDatasetColumn> byField,
                                   Map<String, String> lookupAliasByField, StringBuilder orderClause) {
        if (sortList == null || sortList.isEmpty()) {
            return;
        }
        List<ReportSort> sorted = new ArrayList<ReportSort>(sortList);
        java.util.Collections.sort(sorted, new java.util.Comparator<ReportSort>() {
            @Override
            public int compare(ReportSort a, ReportSort b) {
                return Integer.compare(a.getPriority(), b.getPriority());
            }
        });
        for (ReportSort sort : sorted) {
            ReportDatasetColumn column = byField.get(sort.getFieldName());
            if (column == null || !column.isSelectable() || !column.isSortable()) {
                continue;
            }
            if (orderClause.length() == 0) {
                orderClause.append(" ORDER BY ");
            } else {
                orderClause.append(", ");
            }
            String direction = "DESC".equalsIgnoreCase(sort.getDirection()) ? "DESC" : "ASC";
            orderClause.append(hqlFieldPath(sort.getFieldName(), byField, lookupAliasByField)).append(" ").append(direction);
        }
    }

    /**
     * Builds explicit LEFT JOINs for every lookup-enabled association actually
     * in play (selected, filtered, or sorted on). HQL's implicit dot-path
     * navigation (e.g. "e.department.name") compiles to an INNER join, which
     * silently drops any row whose association is null - inconsistent with a
     * separate COUNT(e) query that never touches the association at all, and
     * simply wrong for a "show a friendly name for this FK" feature. An
     * explicit LEFT JOIN keeps every row, with a null display value when the
     * association itself is null.
     */
    private Map<String, String> buildEntityLookupJoins(Set<String> fieldsInPlay, Map<String, ReportDatasetColumn> byField,
                                                         StringBuilder joinClause) {
        Map<String, String> aliasByField = new HashMap<String, String>();
        for (String field : fieldsInPlay) {
            ReportDatasetColumn column = byField.get(field);
            if (column == null) {
                continue;
            }
            // Every association-typed field gets an explicit LEFT JOIN, whether or
            // not "Lookup" is configured for it: HQL's implicit dot-path navigation
            // (e.g. "e.department" or "e.department.name") is not guaranteed to be
            // translated as a LEFT join by every Hibernate version, and a row whose
            // association is null can be silently dropped from the projected result
            // set entirely while a separate COUNT(e) query - which never touches the
            // association - still counts it. An explicit LEFT JOIN removes that risk
            // regardless of whether the column has a display field configured.
            Class<?> type = resolveType(column.getFieldType());
            boolean isAssociation = type != null && BaseEntity.class.isAssignableFrom(type) && !type.isEnum();
            if (!isAssociation) {
                continue;
            }
            String alias = "lk_" + field;
            joinClause.append(" LEFT JOIN e.").append(field).append(" ").append(alias);
            aliasByField.put(field, alias);
        }
        return aliasByField;
    }

    private String hqlFieldPath(String field, Map<String, ReportDatasetColumn> byField, Map<String, String> lookupAliasByField) {
        String alias = lookupAliasByField.get(field);
        if (alias == null) {
            return "e." + field;
        }
        ReportDatasetColumn column = byField.get(field);
        if (column != null && column.isLookupEnabled() && column.getLookupDisplayField() != null
                && !column.getLookupDisplayField().trim().isEmpty()) {
            return alias + "." + column.getLookupDisplayField().trim();
        }
        // Association field without a configured display field: select the
        // joined entity reference itself (Hibernate orders/compares it by its
        // identifier), same as selecting "e.field" would, but via the LEFT
        // JOIN alias instead of a bare implicit path.
        return alias;
    }

    private String hqlFilterPath(String field, Map<String, ReportDatasetColumn> byField) {
        ReportDatasetColumn column = byField.get(field);
        if (column != null) {
            Class<?> type = resolveType(column.getFieldType());
            if (type != null && BaseEntity.class.isAssignableFrom(type) && !type.isEnum()) {
                return "e." + field + ".id";
            }
        }
        // Lookup is only ever enabled on association-typed columns (handled above via
        // ".id", which needs no join), so a lookup alias is never relevant here.
        return hqlFieldPath(field, byField, java.util.Collections.<String, String>emptyMap());
    }

    private void buildWhereClauseSql(List<ReportFilter> filters, Map<String, ReportDatasetColumn> byField,
                                      Set<String> realColumns, Map<String, String> lookupAliasByField,
                                      StringBuilder whereClause, List<Object> boundValues) {
        if (filters == null) {
            return;
        }
        for (ReportFilter filter : filters) {
            ReportDatasetColumn column = byField.get(filter.getFieldName());
            if (column == null || !column.isSelectable() || !column.isFilterable() || !realColumns.contains(filter.getFieldName())) {
                continue;
            }
            String path = sqlFieldPath(filter.getFieldName(), byField, lookupAliasByField);
            if (whereClause.length() > 0) {
                whereClause.append(" AND ");
            }
            switch (filter.getOperator()) {
                case EQUALS:
                    whereClause.append(path).append(" = ?");
                    boundValues.add(filter.getValue());
                    break;
                case NOT_EQUALS:
                    whereClause.append(path).append(" != ?");
                    boundValues.add(filter.getValue());
                    break;
                case CONTAINS:
                    whereClause.append(path).append(" LIKE ? ESCAPE '\\'");
                    boundValues.add("%" + SqlIdentifierQuoter.escapeLikeValue(filter.getValue()) + "%");
                    break;
                case STARTS_WITH:
                    whereClause.append(path).append(" LIKE ? ESCAPE '\\'");
                    boundValues.add(SqlIdentifierQuoter.escapeLikeValue(filter.getValue()) + "%");
                    break;
                case GREATER_THAN:
                    whereClause.append(path).append(" > ?");
                    boundValues.add(filter.getValue());
                    break;
                case GREATER_OR_EQUAL:
                    whereClause.append(path).append(" >= ?");
                    boundValues.add(filter.getValue());
                    break;
                case LESS_THAN:
                    whereClause.append(path).append(" < ?");
                    boundValues.add(filter.getValue());
                    break;
                case LESS_OR_EQUAL:
                    whereClause.append(path).append(" <= ?");
                    boundValues.add(filter.getValue());
                    break;
                case BETWEEN:
                    whereClause.append(path).append(" BETWEEN ? AND ?");
                    boundValues.add(filter.getValue());
                    boundValues.add(filter.getValue2());
                    break;
                case IN:
                    String[] parts = splitCsv(filter.getValue());
                    StringBuilder placeholders = new StringBuilder();
                    for (int i = 0; i < parts.length; i++) {
                        if (i > 0) {
                            placeholders.append(", ");
                        }
                        placeholders.append("?");
                        boundValues.add(parts[i].trim());
                    }
                    whereClause.append(path).append(" IN (").append(placeholders).append(")");
                    break;
                case IS_NULL:
                    whereClause.append(path).append(" IS NULL");
                    break;
                case IS_NOT_NULL:
                    whereClause.append(path).append(" IS NOT NULL");
                    break;
                default:
                    whereClause.append("1 = 1");
                    break;
            }
        }
    }

    private void buildOrderClauseSql(List<ReportSort> sortList, Map<String, ReportDatasetColumn> byField,
                                      Set<String> realColumns, Map<String, String> lookupAliasByField, StringBuilder orderClause) {
        if (sortList == null || sortList.isEmpty()) {
            return;
        }
        List<ReportSort> sorted = new ArrayList<ReportSort>(sortList);
        java.util.Collections.sort(sorted, new java.util.Comparator<ReportSort>() {
            @Override
            public int compare(ReportSort a, ReportSort b) {
                return Integer.compare(a.getPriority(), b.getPriority());
            }
        });
        for (ReportSort sort : sorted) {
            ReportDatasetColumn column = byField.get(sort.getFieldName());
            if (column == null || !column.isSelectable() || !column.isSortable() || !realColumns.contains(sort.getFieldName())) {
                continue;
            }
            if (orderClause.length() == 0) {
                orderClause.append(" ORDER BY ");
            } else {
                orderClause.append(", ");
            }
            String direction = "DESC".equalsIgnoreCase(sort.getDirection()) ? "DESC" : "ASC";
            orderClause.append(sqlFieldPath(sort.getFieldName(), byField, lookupAliasByField)).append(" ").append(direction);
        }
    }

    private void bindParameters(Query query, List<Object> boundValues) {
        for (int i = 0; i < boundValues.size(); i++) {
            query.setParameter(i + 1, boundValues.get(i));
        }
    }

    private List<Map<String, Object>> toRowMaps(List<?> rawRows, List<String> selectFields) {
        List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
        for (Object rawRow : rawRows) {
            Map<String, Object> row = new LinkedHashMap<String, Object>();
            if (selectFields.size() == 1) {
                row.put(selectFields.get(0), presentValue(rawRow));
            } else {
                Object[] values = (Object[]) rawRow;
                for (int i = 0; i < selectFields.size(); i++) {
                    row.put(selectFields.get(i), presentValue(values[i]));
                }
            }
            rows.add(row);
        }
        return rows;
    }

    /**
     * Selecting an association column without a configured lookup display
     * field puts the raw related entity into the row rather than a scalar
     * value. Rendering that entity means calling its toString(), but several
     * host entity classes never override BaseEntity's own toString() - which,
     * in the shared webutils library, is a stub that unconditionally returns
     * the literal string "Equals not implemented" rather than a real
     * representation. Route such values through a safer fallback instead of
     * ever letting that literal leak into a report.
     */
    private Object presentValue(Object rawValue) {
        if (rawValue instanceof BaseEntity) {
            return entityDisplayValue(rawValue);
        }
        return rawValue;
    }

    private String entityDisplayValue(Object entity) {
        if (entity == null) {
            return null;
        }
        String direct = String.valueOf(entity);
        if (!"Equals not implemented".equals(direct)) {
            return direct;
        }
        String guessed = firstNonBlankDisplayProperty(entity);
        if (guessed != null) {
            return guessed;
        }
        return entity.getClass().getSimpleName() + " #" + ((BaseEntity) entity).getId();
    }

    private String firstNonBlankDisplayProperty(Object entity) {
        String[] candidateGetters = {"getName", "getTitle", "getDisplayName", "getLabel", "getFullName", "getDescription"};
        for (String getterName : candidateGetters) {
            try {
                Method method = entity.getClass().getMethod(getterName);
                Object result = method.invoke(entity);
                if (result instanceof String && !((String) result).trim().isEmpty()) {
                    return (String) result;
                }
            } catch (Exception e) {
                // getter doesn't exist or failed - try the next candidate
            }
        }
        return null;
    }
}