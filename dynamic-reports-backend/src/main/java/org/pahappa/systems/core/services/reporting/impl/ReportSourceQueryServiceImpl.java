package org.pahappa.systems.core.services.reporting.impl;

import org.pahappa.systems.core.services.reporting.ReportExecutionService.FieldOption;
import org.pahappa.systems.core.services.reporting.ReportExecutionService.ReportResult;
import org.pahappa.systems.core.services.reporting.ReportRowEnrichmentStrategy;
import org.pahappa.systems.core.services.reporting.ReportSourceDefinition;
import org.pahappa.systems.core.services.reporting.ReportSourceQueryService;
import org.pahappa.systems.core.services.reporting.ReportSourceService;
import org.pahappa.systems.models.reporting.FieldKind;
import org.pahappa.systems.models.reporting.FilterConnector;
import org.pahappa.systems.models.reporting.FilterOperator;
import org.pahappa.systems.models.reporting.ReportComputedColumn;
import org.pahappa.systems.models.reporting.ReportFilter;
import org.pahappa.systems.models.reporting.ReportSort;
import org.pahappa.systems.models.reporting.SavedReport;
import org.pahappa.systems.reporting.support.ComputedColumnEvaluator;
import org.pahappa.systems.reporting.support.ReportValues;
import org.sers.webutils.model.BaseEntity;
import org.sers.webutils.model.RecordStatus;
import org.sers.webutils.model.security.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.Query;
import javax.persistence.Transient;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Runs reports built on {@link ReportSourceDefinition report sources}.
 *
 * <p>When every field a report touches is a mapped JPA property the whole
 * report - joins, filters, sorting and paging - runs as one HQL query. If any
 * field is a computed (@Transient) getter, the matching entities are loaded
 * and filtered, sorted and paged in Java instead (after running any
 * {@link ReportRowEnrichmentStrategy}). Filters are compiled once into a
 * small predicate tree that both paths evaluate, so their results agree.
 * Calculated columns are always worked out in Java, per row.
 */
@Service("kpiReportSourceQueryService")
@Transactional
public class ReportSourceQueryServiceImpl implements ReportSourceQueryService {

    private static final int DEFAULT_PAGE_SIZE = 50;
    private static final int MAX_PAGE_SIZE = 500;
    private static final int MAX_EXPORT_ROWS = 20000;
    private static final int MAX_IN_MEMORY_ROWS = 50000;
    private static final int MAX_REFERENCE_OPTIONS = 1000;
    private static final char LIKE_ESCAPE = '!';

    /** Inherited BaseEntity properties worth reporting on; the rest are plumbing. */
    private static final Set<String> BASE_ENTITY_PROPERTIES = new HashSet<String>(Arrays.asList("dateCreated", "dateChanged"));

    /** Never offered, whatever entity they appear on. */
    private static final String[] SENSITIVE_NAME_PARTS = {"password", "salt", "secret", "token", "otp"};

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    private ReportSourceService reportSourceService;

    @Autowired(required = false)
    private List<ReportRowEnrichmentStrategy> enrichmentStrategies;

    private final Map<String, Catalog> catalogs = new ConcurrentHashMap<String, Catalog>();
    private final Map<Class<?>, Map<String, Method>> gettersByClass = new ConcurrentHashMap<Class<?>, Map<String, Method>>();

    // ------------------------------------------------------------------ fields

    @Override
    public List<SourceField> fields(String sourceKey) {
        Catalog catalog = catalog(sourceKey);
        return catalog == null ? new ArrayList<SourceField>() : new ArrayList<SourceField>(catalog.fields);
    }

    @Override
    public List<FieldOption> referenceOptions(String sourceKey, String fieldKey) {
        List<FieldOption> options = new ArrayList<FieldOption>();
        Catalog catalog = catalog(sourceKey);
        FieldMeta meta = catalog == null ? null : catalog.byKey.get(fieldKey);
        if (meta == null || meta.field.getKind() != FieldKind.REFERENCE) {
            return options;
        }
        try {
            StringBuilder hql = new StringBuilder("SELECT x FROM ").append(meta.type.getName()).append(" x");
            boolean baseEntity = BaseEntity.class.isAssignableFrom(meta.type);
            if (baseEntity) {
                hql.append(" WHERE x.recordStatus = ?1");
            }
            Query query = this.entityManager.createQuery(hql.toString());
            if (baseEntity) {
                query.setParameter(1, RecordStatus.ACTIVE);
            }
            query.setMaxResults(MAX_REFERENCE_OPTIONS);
            for (Object row : query.getResultList()) {
                if (row instanceof BaseEntity) {
                    FieldOption option = new FieldOption();
                    option.setId(((BaseEntity) row).getId());
                    option.setLabel(ReportValues.entityLabel(row));
                    options.add(option);
                }
            }
        } catch (RuntimeException e) {
            return new ArrayList<FieldOption>();
        }
        Collections.sort(options, new Comparator<FieldOption>() {
            @Override
            public int compare(FieldOption a, FieldOption b) {
                return String.valueOf(a.getLabel()).compareToIgnoreCase(String.valueOf(b.getLabel()));
            }
        });
        return options;
    }

    private Catalog catalog(String sourceKey) {
        if (sourceKey == null) {
            return null;
        }
        Catalog cached = this.catalogs.get(sourceKey);
        if (cached != null) {
            return cached;
        }
        ReportSourceDefinition source = this.reportSourceService.getSource(sourceKey);
        if (source == null) {
            return null;
        }
        Catalog built = buildCatalog(source);
        this.catalogs.put(sourceKey, built);
        return built;
    }

    /**
     * Breadth-first walk: the source entity's own properties form the first
     * group; each referenced entity (up to the source's join depth) then gets
     * its own group, labelled after the property that leads to it.
     */
    private Catalog buildCatalog(ReportSourceDefinition source) {
        Catalog catalog = new Catalog();
        catalog.source = source;
        catalog.entityClass = source.getEntityClass();
        Set<String> excluded = source.getExcludedFields() != null ? source.getExcludedFields() : Collections.<String>emptySet();
        int depth = Math.max(0, source.getJoinDepth());

        LinkedList<Expansion> queue = new LinkedList<Expansion>();
        queue.add(new Expansion(source.getEntityClass(), "", "", source.getLabel(), true, depth, new HashSet<Class<?>>()));
        while (!queue.isEmpty()) {
            Expansion expansion = queue.removeFirst();
            for (PropertyInfo property : properties(expansion.type)) {
                String path = expansion.pathPrefix + property.name;
                if (isExcluded(path, excluded)) {
                    continue;
                }
                String propertyLabel = ReportValues.humanize(property.name);
                SourceField field = new SourceField();
                field.setKey(path);
                field.setLabel(expansion.labelPrefix.isEmpty() ? propertyLabel : expansion.labelPrefix + ": " + propertyLabel);
                field.setGroup(expansion.group);
                field.setKind(property.kind);
                field.setJavaType(property.type.getName());
                field.setPersistent(expansion.persistent && property.persistent);
                if (property.kind == FieldKind.ENUM) {
                    field.setEnumOptions(enumOptions(property.type));
                }
                catalog.add(new FieldMeta(field, property.type));

                if (property.kind == FieldKind.REFERENCE && expansion.depthLeft > 0
                        && !expansion.visited.contains(property.type)) {
                    String label = expansion.labelPrefix.isEmpty() ? propertyLabel : expansion.labelPrefix + " › " + propertyLabel;
                    Set<Class<?>> visited = new HashSet<Class<?>>(expansion.visited);
                    visited.add(expansion.type);
                    queue.add(new Expansion(property.type, path + ".", label, label,
                            expansion.persistent && property.persistent, expansion.depthLeft - 1, visited));
                }
            }
        }
        return catalog;
    }

    private boolean isExcluded(String path, Set<String> excluded) {
        String lower = path.toLowerCase();
        for (String part : SENSITIVE_NAME_PARTS) {
            if (lower.contains(part)) {
                return true;
            }
        }
        // "userAccount" excludes the field wherever it appears, and everything under it.
        String dotted = "." + path + ".";
        for (String exclusion : excluded) {
            if (dotted.contains("." + exclusion + ".")) {
                return true;
            }
        }
        return false;
    }

    private List<FieldOption> enumOptions(Class<?> enumType) {
        List<FieldOption> options = new ArrayList<FieldOption>();
        Object[] constants = enumType.getEnumConstants();
        if (constants != null) {
            for (Object constant : constants) {
                FieldOption option = new FieldOption();
                option.setId(((Enum<?>) constant).name());
                option.setLabel(ReportValues.enumLabel((Enum<?>) constant));
                options.add(option);
            }
        }
        return options;
    }

    /**
     * Reportable properties of a class, in field-declaration order (the
     * order the entity's author wrote them), most-derived class first.
     */
    private List<PropertyInfo> properties(Class<?> type) {
        Map<String, Method> getters = getters(type);
        List<String> order = new ArrayList<String>();
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field declared : c.getDeclaredFields()) {
                if (getters.containsKey(declared.getName()) && !order.contains(declared.getName())) {
                    order.add(declared.getName());
                }
            }
        }
        List<String> rest = new ArrayList<String>(getters.keySet());
        rest.removeAll(order);
        Collections.sort(rest);
        order.addAll(rest);

        List<PropertyInfo> properties = new ArrayList<PropertyInfo>();
        for (String name : order) {
            Method getter = getters.get(name);
            if (getter.getDeclaringClass() == BaseEntity.class && !BASE_ENTITY_PROPERTIES.contains(name)) {
                continue;
            }
            if ("id".equals(name) || "class".equals(name)) {
                continue;
            }
            Class<?> returnType = getter.getReturnType();
            if (User.class.isAssignableFrom(returnType)) {
                // Login accounts hold credentials, never report data.
                continue;
            }
            FieldKind kind = ReportValues.kindOf(returnType);
            if (kind == null) {
                continue;
            }
            properties.add(new PropertyInfo(name, returnType, kind, !getter.isAnnotationPresent(Transient.class)));
        }
        return properties;
    }

    /** Readable properties of a class, including Boolean-wrapper "isX()" getters the Introspector skips. */
    private Map<String, Method> getters(Class<?> type) {
        Map<String, Method> cached = this.gettersByClass.get(type);
        if (cached != null) {
            return cached;
        }
        Map<String, Method> getters = new LinkedHashMap<String, Method>();
        try {
            for (PropertyDescriptor descriptor : Introspector.getBeanInfo(type, Object.class).getPropertyDescriptors()) {
                Method read = descriptor.getReadMethod();
                if (read == null) {
                    String capitalized = Character.toUpperCase(descriptor.getName().charAt(0)) + descriptor.getName().substring(1);
                    try {
                        Method candidate = type.getMethod("is" + capitalized);
                        if (candidate.getReturnType() == Boolean.class) {
                            read = candidate;
                        }
                    } catch (NoSuchMethodException e) {
                        // no read method at all
                    }
                }
                if (read != null && read.getParameterTypes().length == 0) {
                    getters.put(descriptor.getName(), read);
                }
            }
        } catch (Exception e) {
            // unreadable class - report it as having no properties
        }
        this.gettersByClass.put(type, getters);
        return getters;
    }

    // --------------------------------------------------------------- execution

    @Override
    public ReportResult execute(SavedReport report, int page, int pageSize) {
        Plan plan = plan(report);
        int size = pageSize < 1 ? DEFAULT_PAGE_SIZE : Math.min(pageSize, MAX_PAGE_SIZE);
        int offset = (Math.max(1, page) - 1) * size;
        return run(plan, offset, size);
    }

    @Override
    public byte[] exportCsv(SavedReport report) {
        Plan plan = plan(report);
        ReportResult result = run(plan, 0, MAX_EXPORT_ROWS);
        StringBuilder csv = new StringBuilder();
        List<String> keys = result.getColumnKeys();
        for (int i = 0; i < keys.size(); i++) {
            if (i > 0) {
                csv.append(',');
            }
            csv.append(csvField(result.getColumnLabels().get(keys.get(i))));
        }
        csv.append("\r\n");
        for (Map<String, Object> row : result.getRows()) {
            for (int i = 0; i < keys.size(); i++) {
                if (i > 0) {
                    csv.append(',');
                }
                Object value = row.get(keys.get(i));
                csv.append(csvField(value == null ? "" : String.valueOf(value)));
            }
            csv.append("\r\n");
        }
        try {
            return csv.toString().getBytes("UTF-8");
        } catch (java.io.UnsupportedEncodingException e) {
            return csv.toString().getBytes();
        }
    }

    private String csvField(String value) {
        String text = value == null ? "" : value;
        // Neutralise spreadsheet formulas in exported cells.
        if (!text.isEmpty() && "=+-@".indexOf(text.charAt(0)) >= 0 && !looksNumeric(text)) {
            text = "'" + text;
        }
        return "\"" + text.replace("\"", "\"\"") + "\"";
    }

    private boolean looksNumeric(String text) {
        try {
            Double.parseDouble(text);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private ReportResult run(Plan plan, int offset, int limit) {
        return plan.allPersistent ? runHql(plan, offset, limit) : runInMemory(plan, offset, limit);
    }

    /** Validates a report against its source and works out everything a run needs. */
    private Plan plan(SavedReport report) {
        if (report == null || report.getSourceKey() == null) {
            throw new IllegalArgumentException("Choose a report source.");
        }
        Catalog catalog = catalog(report.getSourceKey());
        if (catalog == null) {
            throw new IllegalArgumentException("The report source '" + report.getSourceKey() + "' no longer exists.");
        }
        Plan plan = new Plan();
        plan.catalog = catalog;
        plan.aliases = report.getColumnAliases();

        Map<String, ReportComputedColumn> computedByKey = new LinkedHashMap<String, ReportComputedColumn>();
        for (ReportComputedColumn column : nonNull(report.getComputedColumns())) {
            if (column.getColumnKey() != null && column.getOperation() != null && column.getLeftOperand() != null) {
                computedByKey.put(column.getColumnKey(), column);
            }
        }
        plan.computed = new ArrayList<ReportComputedColumn>(computedByKey.values());

        for (String key : nonNull(report.getSelectedColumns())) {
            if ((catalog.byKey.containsKey(key) || computedByKey.containsKey(key)) && !plan.outputKeys.contains(key)) {
                plan.outputKeys.add(key);
            }
        }
        if (plan.outputKeys.isEmpty()) {
            throw new IllegalArgumentException("Choose at least one column to show.");
        }

        for (String key : plan.outputKeys) {
            if (catalog.byKey.containsKey(key)) {
                plan.fetch.add(catalog.byKey.get(key));
            }
        }
        for (ReportComputedColumn column : plan.computed) {
            for (String operand : new String[]{column.getLeftOperand(), column.isRightIsConstant() ? null : column.getRightOperand()}) {
                if (operand != null && catalog.byKey.containsKey(operand)) {
                    plan.fetch.add(catalog.byKey.get(operand));
                }
            }
        }

        plan.predicate = compileFilters(report.getFilters(), catalog, plan.filterFields);

        List<ReportSort> sorts = new ArrayList<ReportSort>(nonNull(report.getSort()));
        Collections.sort(sorts, new Comparator<ReportSort>() {
            @Override
            public int compare(ReportSort a, ReportSort b) {
                return Integer.compare(a.getPriority(), b.getPriority());
            }
        });
        for (ReportSort sort : sorts) {
            FieldMeta meta = catalog.byKey.get(sort.getFieldName());
            if (meta != null && meta.field.isSortable()) {
                plan.sorts.add(new SortSpec(meta, "DESC".equalsIgnoreCase(sort.getDirection())));
            }
        }

        boolean allPersistent = true;
        Set<FieldMeta> involved = new LinkedHashSet<FieldMeta>(plan.fetch);
        involved.addAll(plan.filterFields);
        for (SortSpec sort : plan.sorts) {
            involved.add(sort.meta);
        }
        for (FieldMeta meta : involved) {
            allPersistent &= meta.field.isPersistent();
            plan.involvedRoots.add(meta.segments[0]);
        }
        plan.allPersistent = allPersistent;
        plan.filtersPersistent = true;
        for (FieldMeta meta : plan.filterFields) {
            plan.filtersPersistent &= meta.field.isPersistent();
        }
        return plan;
    }

    private static <T> List<T> nonNull(List<T> list) {
        return list != null ? list : Collections.<T>emptyList();
    }

    private ReportResult runHql(Plan plan, int offset, int limit) {
        HqlBuilder hql = new HqlBuilder();
        List<FieldMeta> fetch = new ArrayList<FieldMeta>(plan.fetch);
        StringBuilder select = new StringBuilder("SELECT ");
        if (fetch.isEmpty()) {
            // Only calculated columns on constants - rows still need counting.
            select.append("e");
        }
        for (int i = 0; i < fetch.size(); i++) {
            select.append(i > 0 ? ", " : "").append(hql.valuePath(fetch.get(i)));
        }
        String where = hql.where(plan);
        StringBuilder order = new StringBuilder();
        for (SortSpec sort : plan.sorts) {
            order.append(order.length() == 0 ? " ORDER BY " : ", ")
                    .append(hql.valuePath(sort.meta)).append(sort.descending ? " DESC" : " ASC");
        }
        String from = " FROM " + plan.catalog.entityClass.getName() + " e" + hql.joins;

        Query dataQuery = this.entityManager.createQuery(select + from + where + order);
        hql.bind(dataQuery);
        dataQuery.setFirstResult(offset);
        dataQuery.setMaxResults(limit);
        List<?> raw = dataQuery.getResultList();

        Query countQuery = this.entityManager.createQuery("SELECT COUNT(e)" + from + where);
        hql.bind(countQuery);
        Number total = (Number) countQuery.getSingleResult();

        List<Map<String, Object>> rawRows = new ArrayList<Map<String, Object>>();
        for (Object item : raw) {
            Object[] values = fetch.size() > 1 ? (Object[]) item : new Object[]{item};
            Map<String, Object> row = new HashMap<String, Object>();
            for (int i = 0; i < fetch.size(); i++) {
                row.put(fetch.get(i).field.getKey(), values[i]);
            }
            rawRows.add(row);
        }
        return toResult(plan, rawRows, total == null ? 0L : total.longValue());
    }

    private ReportResult runInMemory(Plan plan, int offset, int limit) {
        HqlBuilder hql = new HqlBuilder();
        Plan loadPlan = plan;
        if (!plan.filtersPersistent) {
            // A computed field takes part in the filters, and with OR the
            // filters can't be split into a database half and a Java half -
            // so only the active-record restriction goes to the database.
            loadPlan = new Plan();
            loadPlan.catalog = plan.catalog;
        }
        String where = hql.where(loadPlan);
        Query query = this.entityManager.createQuery("SELECT e FROM " + plan.catalog.entityClass.getName() + " e" + hql.joins + where);
        hql.bind(query);
        query.setMaxResults(MAX_IN_MEMORY_ROWS + 1);
        List<Object> entities = new ArrayList<Object>(query.getResultList());
        if (entities.size() > MAX_IN_MEMORY_ROWS) {
            throw new IllegalArgumentException("This report uses calculated fields and matches more than "
                    + MAX_IN_MEMORY_ROWS + " records. Add filters on regular fields to narrow it down.");
        }

        if (this.enrichmentStrategies != null && !entities.isEmpty()) {
            for (ReportRowEnrichmentStrategy strategy : this.enrichmentStrategies) {
                if (strategy.supports(plan.catalog.entityClass)) {
                    strategy.enrich(plan.catalog.entityClass, entities, plan.involvedRoots);
                }
            }
        }

        if (!plan.filtersPersistent && plan.predicate != null) {
            List<Object> matching = new ArrayList<Object>();
            for (Object entity : entities) {
                if (matches(plan.predicate, entity, plan.catalog)) {
                    matching.add(entity);
                }
            }
            entities = matching;
        }

        if (!plan.sorts.isEmpty()) {
            final List<SortSpec> sorts = plan.sorts;
            Collections.sort(entities, new Comparator<Object>() {
                @Override
                public int compare(Object a, Object b) {
                    for (SortSpec sort : sorts) {
                        int comparison = compareForSort(resolve(a, sort.meta), resolve(b, sort.meta));
                        if (comparison != 0) {
                            return sort.descending ? -comparison : comparison;
                        }
                    }
                    return 0;
                }
            });
        }

        long total = entities.size();
        List<Map<String, Object>> rawRows = new ArrayList<Map<String, Object>>();
        for (int i = offset; i < entities.size() && i < offset + limit; i++) {
            Map<String, Object> row = new HashMap<String, Object>();
            for (FieldMeta meta : plan.fetch) {
                row.put(meta.field.getKey(), resolve(entities.get(i), meta));
            }
            rawRows.add(row);
        }
        return toResult(plan, rawRows, total);
    }

    private ReportResult toResult(Plan plan, List<Map<String, Object>> rawRows, long total) {
        ReportResult result = new ReportResult();
        result.setColumnKeys(new ArrayList<String>(plan.outputKeys));
        Map<String, String> labels = new LinkedHashMap<String, String>();
        Map<String, ReportComputedColumn> computedByKey = new HashMap<String, ReportComputedColumn>();
        for (ReportComputedColumn column : plan.computed) {
            computedByKey.put(column.getColumnKey(), column);
        }
        for (String key : plan.outputKeys) {
            String alias = plan.aliases != null ? plan.aliases.get(key) : null;
            String label;
            if (alias != null && !alias.trim().isEmpty()) {
                label = alias.trim();
            } else if (computedByKey.containsKey(key)) {
                ReportComputedColumn column = computedByKey.get(key);
                label = column.getLabel() != null && !column.getLabel().trim().isEmpty() ? column.getLabel() : "Calculated";
            } else {
                label = plan.catalog.byKey.get(key).field.getLabel();
            }
            labels.put(key, label);
        }
        result.setColumnLabels(labels);

        List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
        for (Map<String, Object> raw : rawRows) {
            ComputedColumnEvaluator.evaluateAll(plan.computed, raw);
            Map<String, Object> row = new LinkedHashMap<String, Object>();
            for (String key : plan.outputKeys) {
                row.put(key, ReportValues.present(raw.get(key)));
            }
            rows.add(row);
        }
        result.setRows(rows);
        result.setTotalCount(total);
        return result;
    }

    // ----------------------------------------------------------------- filters

    /**
     * Compiles the report's filters into one predicate. Filters are split into
     * runs at every OR; each run is AND-ed together and the runs are OR-ed,
     * giving AND its usual higher precedence. Filters that aren't complete
     * yet (no field, no value picked) are left out; a filter whose value
     * can't be read (a non-number in a number filter) is an error.
     */
    private Pred compileFilters(List<ReportFilter> filters, Catalog catalog, Set<FieldMeta> usedFields) {
        if (filters == null || filters.isEmpty()) {
            return null;
        }
        List<Pred> orGroups = new ArrayList<Pred>();
        List<Pred> currentAnd = new ArrayList<Pred>();
        int number = 0;
        for (ReportFilter filter : filters) {
            number++;
            FieldMeta meta = catalog.byKey.get(filter.getFieldName());
            if (meta == null) {
                continue;
            }
            Pred pred = compileFilter(filter, meta, number);
            if (pred == null) {
                continue;
            }
            usedFields.add(meta);
            if (filter.getConnector() == FilterConnector.OR && !currentAnd.isEmpty()) {
                orGroups.add(And.of(currentAnd));
                currentAnd = new ArrayList<Pred>();
            }
            currentAnd.add(pred);
        }
        if (!currentAnd.isEmpty()) {
            orGroups.add(And.of(currentAnd));
        }
        if (orGroups.isEmpty()) {
            return null;
        }
        return orGroups.size() == 1 ? orGroups.get(0) : new Or(orGroups);
    }

    private Pred compileFilter(ReportFilter filter, FieldMeta meta, int number) {
        FieldKind kind = meta.field.getKind();
        FilterOperator op = filter.getOperator();
        if (!kind.supports(op)) {
            return null;
        }
        String key = meta.field.getKey();
        String which = "Filter " + number + " (" + meta.field.getLabel() + "): ";

        switch (op) {
            case IS_NULL:
                return new IsNull(key);
            case IS_NOT_NULL:
                return new Not(new IsNull(key));
            case IS_EMPTY:
                return new Or(Arrays.<Pred>asList(new IsNull(key), new Compare(key, "=", "")));
            case IS_NOT_EMPTY:
                return new Not(new Or(Arrays.<Pred>asList(new IsNull(key), new Compare(key, "=", ""))));
            case IS_TRUE:
                return new Compare(key, "=", Boolean.TRUE);
            case IS_FALSE:
                return new Compare(key, "=", Boolean.FALSE);
            case IN:
            case NOT_IN: {
                List<Object> values = new ArrayList<Object>();
                for (String raw : filter.getValues()) {
                    Object value = kind == FieldKind.REFERENCE || kind == FieldKind.TEXT ? raw : ReportValues.coerce(raw, meta.type);
                    if (value != null && !String.valueOf(value).isEmpty()) {
                        values.add(value);
                    }
                }
                if (values.isEmpty()) {
                    return null;
                }
                Pred in = new InList(key, values);
                return op == FilterOperator.IN ? in : negateIncludingMissing(key, in);
            }
            default:
                break;
        }

        if (kind == FieldKind.DATE) {
            return filter.isAgeMode() ? compileAge(filter, key, op, which) : compileDate(filter, key, op, which);
        }

        if (isBlank(filter.getValue()) || (op.needsSecondValue() && isBlank(filter.getValue2()))) {
            return null;
        }
        if (kind == FieldKind.TEXT) {
            String text = filter.getValue();
            switch (op) {
                case EQUALS:
                    return new Compare(key, "=", text);
                case NOT_EQUALS:
                    return negateIncludingMissing(key, new Compare(key, "=", text));
                case CONTAINS:
                    return new Like(key, Like.CONTAINS, text);
                case NOT_CONTAINS:
                    return negateIncludingMissing(key, new Like(key, Like.CONTAINS, text));
                case STARTS_WITH:
                    return new Like(key, Like.STARTS, text);
                case ENDS_WITH:
                    return new Like(key, Like.ENDS, text);
                default:
                    return null;
            }
        }

        Object value = ReportValues.coerce(filter.getValue(), meta.type);
        if (value == null) {
            throw new IllegalArgumentException(which + "'" + filter.getValue() + "' is not a valid number.");
        }
        switch (op) {
            case EQUALS:
                return new Compare(key, "=", value);
            case NOT_EQUALS:
                return negateIncludingMissing(key, new Compare(key, "=", value));
            case GREATER_THAN:
                return new Compare(key, ">", value);
            case GREATER_OR_EQUAL:
                return new Compare(key, ">=", value);
            case LESS_THAN:
                return new Compare(key, "<", value);
            case LESS_OR_EQUAL:
                return new Compare(key, "<=", value);
            case BETWEEN: {
                Object second = ReportValues.coerce(filter.getValue2(), meta.type);
                if (second == null) {
                    throw new IllegalArgumentException(which + "'" + filter.getValue2() + "' is not a valid number.");
                }
                return And.of(Arrays.<Pred>asList(new Compare(key, ">=", value), new Compare(key, "<=", second)));
            }
            default:
                return null;
        }
    }

    /** Calendar-day comparisons: "is on 5 Oct" covers the whole of 5 Oct, whatever the time. */
    private Pred compileDate(ReportFilter filter, String key, FilterOperator op, String which) {
        if (isBlank(filter.getValue()) || (op.needsSecondValue() && isBlank(filter.getValue2()))) {
            return null;
        }
        Date first = ReportValues.parseDate(filter.getValue());
        if (first == null) {
            throw new IllegalArgumentException(which + "'" + filter.getValue() + "' is not a valid date.");
        }
        Date dayStart = ReportValues.startOfDay(first);
        Date nextDay = ReportValues.addDays(dayStart, 1);
        switch (op) {
            case EQUALS:
                return dayRange(key, dayStart, nextDay);
            case NOT_EQUALS:
                return negateIncludingMissing(key, dayRange(key, dayStart, nextDay));
            case LESS_THAN:
                return new Compare(key, "<", dayStart);
            case LESS_OR_EQUAL:
                return new Compare(key, "<", nextDay);
            case GREATER_THAN:
                return new Compare(key, ">=", nextDay);
            case GREATER_OR_EQUAL:
                return new Compare(key, ">=", dayStart);
            case BETWEEN: {
                Date second = ReportValues.parseDate(filter.getValue2());
                if (second == null) {
                    throw new IllegalArgumentException(which + "'" + filter.getValue2() + "' is not a valid date.");
                }
                Date from = ReportValues.startOfDay(first.before(second) ? first : second);
                Date to = ReportValues.addDays(ReportValues.startOfDay(first.before(second) ? second : first), 1);
                return dayRange(key, from, to);
            }
            default:
                return null;
        }
    }

    /**
     * Age comparisons, worked out from today's date each time the report
     * runs. Someone is at least N years old when they were born on or before
     * the date N years ago, i.e. before {@code bornBy(N)}.
     */
    private Pred compileAge(ReportFilter filter, String key, FilterOperator op, String which) {
        if (isBlank(filter.getValue()) || (op.needsSecondValue() && isBlank(filter.getValue2()))) {
            return null;
        }
        int years = parseYears(filter.getValue(), which);
        switch (op) {
            case GREATER_OR_EQUAL:
                return new Compare(key, "<", bornBy(years));
            case LESS_THAN:
                return new Compare(key, ">=", bornBy(years));
            case GREATER_THAN:
                return new Compare(key, "<", bornBy(years + 1));
            case LESS_OR_EQUAL:
                return new Compare(key, ">=", bornBy(years + 1));
            case EQUALS:
                return dayRange(key, bornBy(years + 1), bornBy(years));
            case NOT_EQUALS:
                return negateIncludingMissing(key, dayRange(key, bornBy(years + 1), bornBy(years)));
            case BETWEEN: {
                int second = parseYears(filter.getValue2(), which);
                int youngest = Math.min(years, second);
                int oldest = Math.max(years, second);
                return dayRange(key, bornBy(oldest + 1), bornBy(youngest));
            }
            default:
                return null;
        }
    }

    /** Exclusive upper bound of birth dates for people at least {@code years} old today. */
    private Date bornBy(int years) {
        return ReportValues.addDays(ReportValues.addYears(ReportValues.startOfDay(new Date()), -years), 1);
    }

    private int parseYears(String value, String which) {
        try {
            int years = (int) Math.floor(Double.parseDouble(value.trim()));
            if (years < 0) {
                throw new NumberFormatException();
            }
            return years;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(which + "'" + value + "' is not a valid age in years.");
        }
    }

    private Pred dayRange(String key, Date fromInclusive, Date toExclusive) {
        return And.of(Arrays.<Pred>asList(new Compare(key, ">=", fromInclusive), new Compare(key, "<", toExclusive)));
    }

    private Pred negateIncludingMissing(String key, Pred pred) {
        return new Or(Arrays.<Pred>asList(new IsNull(key), new Not(pred)));
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    // ------------------------------------------------------ in-memory matching

    private boolean matches(Pred pred, Object entity, Catalog catalog) {
        if (pred instanceof And) {
            for (Pred part : ((And) pred).parts) {
                if (!matches(part, entity, catalog)) {
                    return false;
                }
            }
            return true;
        }
        if (pred instanceof Or) {
            for (Pred part : ((Or) pred).parts) {
                if (matches(part, entity, catalog)) {
                    return true;
                }
            }
            return false;
        }
        if (pred instanceof Not) {
            return !matches(((Not) pred).inner, entity, catalog);
        }
        FieldMeta meta = catalog.byKey.get(((Leaf) pred).field);
        Object actual = comparable(resolve(entity, meta));
        if (pred instanceof IsNull) {
            return actual == null;
        }
        if (actual == null) {
            return false;
        }
        if (pred instanceof InList) {
            for (Object candidate : ((InList) pred).values) {
                if (compareValues(actual, comparable(candidate)) == 0) {
                    return true;
                }
            }
            return false;
        }
        if (pred instanceof Like) {
            Like like = (Like) pred;
            String text = String.valueOf(actual).toLowerCase();
            String needle = like.text.toLowerCase();
            return Like.STARTS.equals(like.mode) ? text.startsWith(needle)
                    : Like.ENDS.equals(like.mode) ? text.endsWith(needle) : text.contains(needle);
        }
        Compare compare = (Compare) pred;
        int comparison = compareValues(actual, comparable(compare.value));
        if ("=".equals(compare.op)) {
            return comparison == 0;
        }
        if (">".equals(compare.op)) {
            return comparison > 0;
        }
        if (">=".equals(compare.op)) {
            return comparison >= 0;
        }
        if ("<".equals(compare.op)) {
            return comparison < 0;
        }
        return comparison <= 0;
    }

    /** References compare by id and enums by constant name, matching how filter values are stored. */
    private Object comparable(Object value) {
        if (value instanceof BaseEntity) {
            return ((BaseEntity) value).getId();
        }
        if (value instanceof Enum) {
            return ((Enum<?>) value).name();
        }
        return value;
    }

    @SuppressWarnings("unchecked")
    private int compareValues(Object a, Object b) {
        if (a instanceof Number && b instanceof Number) {
            return Double.compare(((Number) a).doubleValue(), ((Number) b).doubleValue());
        }
        if (a instanceof Date && b instanceof Date) {
            return ((Date) a).compareTo((Date) b);
        }
        if (a instanceof Boolean && b instanceof Boolean) {
            return Boolean.compare((Boolean) a, (Boolean) b);
        }
        if (a instanceof Comparable && a.getClass().isInstance(b) && !(a instanceof String)) {
            return ((Comparable<Object>) a).compareTo(b);
        }
        return String.valueOf(a).compareToIgnoreCase(String.valueOf(b));
    }

    /** Sort order matching MySQL's: empty values first when ascending. */
    private int compareForSort(Object a, Object b) {
        Object left = a instanceof BaseEntity ? ReportValues.entityLabel(a) : (a instanceof Enum ? ReportValues.enumLabel((Enum<?>) a) : a);
        Object right = b instanceof BaseEntity ? ReportValues.entityLabel(b) : (b instanceof Enum ? ReportValues.enumLabel((Enum<?>) b) : b);
        if (left == null || right == null) {
            return left == null ? (right == null ? 0 : -1) : 1;
        }
        return compareValues(left, right);
    }

    /** Walks a dotted path through getters; null as soon as any step is null or fails. */
    private Object resolve(Object entity, FieldMeta meta) {
        Object current = entity;
        for (String segment : meta.segments) {
            if (current == null) {
                return null;
            }
            Method getter = getters(current.getClass()).get(segment);
            if (getter == null) {
                return null;
            }
            try {
                current = getter.invoke(current);
            } catch (Exception e) {
                return null;
            }
        }
        return current;
    }

    // ---------------------------------------------------------- HQL rendering

    /** Accumulates LEFT JOINs and positional parameters while rendering one query. */
    private final class HqlBuilder {
        private final Map<String, String> aliasByPath = new HashMap<String, String>();
        private final StringBuilder joins = new StringBuilder();
        private final List<Object> params = new ArrayList<Object>();

        /**
         * Explicit LEFT JOIN per association path: an implicit "e.a.b" path
         * becomes an inner join and would drop rows whose association is empty.
         */
        String alias(String associationPath) {
            String existing = this.aliasByPath.get(associationPath);
            if (existing != null) {
                return existing;
            }
            int dot = associationPath.lastIndexOf('.');
            String parent = dot < 0 ? "e" : alias(associationPath.substring(0, dot));
            String alias = "j" + (this.aliasByPath.size() + 1);
            this.joins.append(" LEFT JOIN ").append(parent).append('.').append(associationPath.substring(dot + 1))
                    .append(' ').append(alias);
            this.aliasByPath.put(associationPath, alias);
            return alias;
        }

        String valuePath(FieldMeta meta) {
            String key = meta.field.getKey();
            if (meta.field.getKind() == FieldKind.REFERENCE) {
                return alias(key);
            }
            int dot = key.lastIndexOf('.');
            return (dot < 0 ? "e" : alias(key.substring(0, dot))) + "." + key.substring(dot + 1);
        }

        String comparePath(FieldMeta meta) {
            return meta.field.getKind() == FieldKind.REFERENCE ? alias(meta.field.getKey()) + ".id" : valuePath(meta);
        }

        String param(Object value) {
            this.params.add(value);
            return "?" + this.params.size();
        }

        String where(Plan plan) {
            List<String> parts = new ArrayList<String>();
            if (BaseEntity.class.isAssignableFrom(plan.catalog.entityClass)) {
                parts.add("e.recordStatus = " + param(RecordStatus.ACTIVE));
            }
            if (plan.predicate != null) {
                parts.add(render(plan.predicate, plan.catalog));
            }
            if (parts.isEmpty()) {
                return "";
            }
            StringBuilder where = new StringBuilder(" WHERE ");
            for (int i = 0; i < parts.size(); i++) {
                where.append(i > 0 ? " AND " : "").append(parts.get(i));
            }
            return where.toString();
        }

        String render(Pred pred, Catalog catalog) {
            if (pred instanceof And || pred instanceof Or) {
                List<Pred> parts = pred instanceof And ? ((And) pred).parts : ((Or) pred).parts;
                StringBuilder sb = new StringBuilder("(");
                for (int i = 0; i < parts.size(); i++) {
                    sb.append(i > 0 ? (pred instanceof And ? " AND " : " OR ") : "").append(render(parts.get(i), catalog));
                }
                return sb.append(")").toString();
            }
            if (pred instanceof Not) {
                return "NOT " + render(((Not) pred).inner, catalog);
            }
            FieldMeta meta = catalog.byKey.get(((Leaf) pred).field);
            String path = comparePath(meta);
            if (pred instanceof IsNull) {
                return "(" + path + " IS NULL)";
            }
            if (pred instanceof InList) {
                StringBuilder sb = new StringBuilder("(").append(path).append(" IN (");
                List<Object> values = ((InList) pred).values;
                for (int i = 0; i < values.size(); i++) {
                    sb.append(i > 0 ? ", " : "").append(param(values.get(i)));
                }
                return sb.append("))").toString();
            }
            if (pred instanceof Like) {
                Like like = (Like) pred;
                String escaped = escapeLike(like.text.toLowerCase());
                String pattern = Like.STARTS.equals(like.mode) ? escaped + "%"
                        : Like.ENDS.equals(like.mode) ? "%" + escaped : "%" + escaped + "%";
                return "(lower(" + path + ") LIKE " + param(pattern) + " ESCAPE '" + LIKE_ESCAPE + "')";
            }
            Compare compare = (Compare) pred;
            return "(" + path + " " + compare.op + " " + param(compare.value) + ")";
        }

        void bind(Query query) {
            for (int i = 0; i < this.params.size(); i++) {
                query.setParameter(i + 1, this.params.get(i));
            }
        }
    }

    private static String escapeLike(String text) {
        StringBuilder sb = new StringBuilder();
        for (char c : text.toCharArray()) {
            if (c == LIKE_ESCAPE || c == '%' || c == '_') {
                sb.append(LIKE_ESCAPE);
            }
            sb.append(c);
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------ model

    private static final class Catalog {
        ReportSourceDefinition source;
        Class<?> entityClass;
        final List<SourceField> fields = new ArrayList<SourceField>();
        final Map<String, FieldMeta> byKey = new HashMap<String, FieldMeta>();

        void add(FieldMeta meta) {
            this.fields.add(meta.field);
            this.byKey.put(meta.field.getKey(), meta);
        }
    }

    private static final class FieldMeta {
        final SourceField field;
        final Class<?> type;
        final String[] segments;

        FieldMeta(SourceField field, Class<?> type) {
            this.field = field;
            this.type = type;
            this.segments = field.getKey().split("\\.");
        }
    }

    private static final class PropertyInfo {
        final String name;
        final Class<?> type;
        final FieldKind kind;
        final boolean persistent;

        PropertyInfo(String name, Class<?> type, FieldKind kind, boolean persistent) {
            this.name = name;
            this.type = type;
            this.kind = kind;
            this.persistent = persistent;
        }
    }

    private static final class Expansion {
        final Class<?> type;
        final String pathPrefix;
        final String labelPrefix;
        final String group;
        final boolean persistent;
        final int depthLeft;
        final Set<Class<?>> visited;

        Expansion(Class<?> type, String pathPrefix, String labelPrefix, String group, boolean persistent,
                  int depthLeft, Set<Class<?>> visited) {
            this.type = type;
            this.pathPrefix = pathPrefix;
            this.labelPrefix = labelPrefix;
            this.group = group;
            this.persistent = persistent;
            this.depthLeft = depthLeft;
            this.visited = visited;
        }
    }

    private static final class SortSpec {
        final FieldMeta meta;
        final boolean descending;

        SortSpec(FieldMeta meta, boolean descending) {
            this.meta = meta;
            this.descending = descending;
        }
    }

    private static final class Plan {
        Catalog catalog;
        Map<String, String> aliases;
        final List<String> outputKeys = new ArrayList<String>();
        final Set<FieldMeta> fetch = new LinkedHashSet<FieldMeta>();
        final Set<FieldMeta> filterFields = new LinkedHashSet<FieldMeta>();
        final List<SortSpec> sorts = new ArrayList<SortSpec>();
        final Set<String> involvedRoots = new HashSet<String>();
        List<ReportComputedColumn> computed = new ArrayList<ReportComputedColumn>();
        Pred predicate;
        boolean allPersistent;
        boolean filtersPersistent = true;
    }

    private abstract static class Pred {
    }

    private abstract static class Leaf extends Pred {
        final String field;

        Leaf(String field) {
            this.field = field;
        }
    }

    private static final class Compare extends Leaf {
        final String op;
        final Object value;

        Compare(String field, String op, Object value) {
            super(field);
            this.op = op;
            this.value = value;
        }
    }

    private static final class Like extends Leaf {
        static final String CONTAINS = "contains";
        static final String STARTS = "starts";
        static final String ENDS = "ends";
        final String mode;
        final String text;

        Like(String field, String mode, String text) {
            super(field);
            this.mode = mode;
            this.text = text;
        }
    }

    private static final class InList extends Leaf {
        final List<Object> values;

        InList(String field, List<Object> values) {
            super(field);
            this.values = values;
        }
    }

    private static final class IsNull extends Leaf {
        IsNull(String field) {
            super(field);
        }
    }

    private static final class Not extends Pred {
        final Pred inner;

        Not(Pred inner) {
            this.inner = inner;
        }
    }

    private static final class And extends Pred {
        final List<Pred> parts;

        private And(List<Pred> parts) {
            this.parts = parts;
        }

        static Pred of(List<Pred> parts) {
            return parts.size() == 1 ? parts.get(0) : new And(new ArrayList<Pred>(parts));
        }
    }

    private static final class Or extends Pred {
        final List<Pred> parts;

        Or(List<Pred> parts) {
            this.parts = new ArrayList<Pred>(parts);
        }
    }
}
