package org.pahappa.systems.core.services.reporting.impl;

import com.googlecode.genericdao.search.Search;
import org.pahappa.systems.core.services.reporting.DiscoveredReportSource;
import org.pahappa.systems.core.services.reporting.ReportSourceDefinition;
import org.pahappa.systems.core.services.reporting.ReportSourceRegistry;
import org.pahappa.systems.core.services.reporting.ReportSourceService;
import org.pahappa.systems.models.reporting.ReportSourceConfig;
import org.pahappa.systems.models.reporting.SavedReport;
import org.pahappa.systems.reporting.support.GenericServiceImpl;
import org.pahappa.systems.reporting.support.ReportValues;
import org.sers.webutils.model.BaseEntity;
import org.sers.webutils.model.RecordStatus;
import org.sers.webutils.model.security.User;
import org.sers.webutils.model.exception.OperationFailedException;
import org.sers.webutils.model.exception.ValidationFailedException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.metamodel.EntityType;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service("kpiReportSourceService")
@Transactional
public class ReportSourceServiceImpl extends GenericServiceImpl<ReportSourceConfig> implements ReportSourceService {

    /** Entity names containing these never become sources (OTP, password-reset and token tables). */
    private static final String[] SENSITIVE_NAME_PARTS = {"password", "otp", "token", "secret", "salt"};

    /** Optional: hand-curated sources. When none are registered, every mapped entity is discovered. */
    @Autowired(required = false)
    private List<ReportSourceRegistry> registries;

    @PersistenceContext
    private EntityManager discoveryEntityManager;

    private volatile List<ReportSourceDefinition> discovered;

    @Override
    public boolean isDeletable(ReportSourceConfig instance) throws OperationFailedException {
        return false;
    }

    @Override
    public ReportSourceConfig saveInstance(ReportSourceConfig instance) throws ValidationFailedException, OperationFailedException {
        return super.save(instance);
    }

    /**
     * Sources from any registered {@link ReportSourceRegistry} first, then
     * every other mapped entity, discovered automatically.
     */
    @Override
    public List<ReportSourceDefinition> getAllSources() {
        List<ReportSourceDefinition> sources = registeredSources();
        Set<Class<?>> covered = new HashSet<Class<?>>();
        for (ReportSourceDefinition source : sources) {
            covered.add(source.getEntityClass());
        }
        for (ReportSourceDefinition source : discoveredSources()) {
            if (!covered.contains(source.getEntityClass())) {
                sources.add(source);
            }
        }
        return sources;
    }

    private List<ReportSourceDefinition> registeredSources() {
        List<ReportSourceDefinition> sources = new ArrayList<ReportSourceDefinition>();
        if (this.registries == null) {
            return sources;
        }
        Map<String, Boolean> seen = new HashMap<String, Boolean>();
        for (ReportSourceRegistry registry : this.registries) {
            List<? extends ReportSourceDefinition> contributed = registry.reportSources();
            if (contributed == null) {
                continue;
            }
            for (ReportSourceDefinition source : contributed) {
                if (source != null && source.getKey() != null && source.getEntityClass() != null
                        && seen.put(source.getKey(), Boolean.TRUE) == null) {
                    sources.add(source);
                }
            }
        }
        return sources;
    }

    /**
     * Every entity in the persistence unit's metamodel that extends
     * BaseEntity and is concrete, except framework (webutils) entities, this
     * plugin's own tables and credential-like tables. Computed once: the set
     * of mapped entities only changes on redeploy.
     */
    private List<ReportSourceDefinition> discoveredSources() {
        List<ReportSourceDefinition> cached = this.discovered;
        if (cached != null) {
            return cached;
        }
        List<ReportSourceDefinition> found = new ArrayList<ReportSourceDefinition>();
        try {
            String ownPackage = SavedReport.class.getPackage().getName();
            for (EntityType<?> entity : this.discoveryEntityManager.getMetamodel().getEntities()) {
                Class<?> type = entity.getJavaType();
                if (type == null || !BaseEntity.class.isAssignableFrom(type) || Modifier.isAbstract(type.getModifiers())
                        || type.getName().startsWith("org.sers.webutils.") || type.getName().startsWith(ownPackage + ".")
                        || isSensitive(type.getSimpleName())) {
                    continue;
                }
                String label = ReportValues.humanize(type.getSimpleName());
                found.add(new DiscoveredReportSource(type, label, describe(type, label)));
            }
        } catch (RuntimeException e) {
            // no metamodel available - offer registered sources only
            return Collections.emptyList();
        }
        Collections.sort(found, new Comparator<ReportSourceDefinition>() {
            @Override
            public int compare(ReportSourceDefinition a, ReportSourceDefinition b) {
                return a.getLabel().compareToIgnoreCase(b.getLabel());
            }
        });
        this.discovered = found;
        return found;
    }

    private boolean isSensitive(String name) {
        String lower = name.toLowerCase();
        for (String part : SENSITIVE_NAME_PARTS) {
            if (lower.contains(part)) {
                return true;
            }
        }
        return false;
    }

    /** "Requisition records, linked to Annual Budget and Requisition Category." */
    private String describe(Class<?> type, String label) {
        List<String> links = new ArrayList<String>();
        try {
            for (PropertyDescriptor descriptor : Introspector.getBeanInfo(type, BaseEntity.class).getPropertyDescriptors()) {
                Class<?> propertyType = descriptor.getPropertyType();
                if (descriptor.getReadMethod() != null && propertyType != null && BaseEntity.class.isAssignableFrom(propertyType)
                        && !User.class.isAssignableFrom(propertyType)) {
                    links.add(ReportValues.humanize(descriptor.getName()));
                }
            }
        } catch (Exception e) {
            // describe without links
        }
        StringBuilder description = new StringBuilder(label).append(" records");
        if (!links.isEmpty()) {
            int shown = Math.min(links.size(), 4);
            description.append(", linked to ");
            for (int i = 0; i < shown; i++) {
                description.append(i == 0 ? "" : (i == shown - 1 && links.size() <= 4 ? " and " : ", ")).append(links.get(i));
            }
            if (links.size() > 4) {
                description.append(" and ").append(links.size() - 4).append(" more");
            }
        }
        return description.append('.').toString();
    }

    @Override
    public List<ReportSourceDefinition> getAvailableSources() {
        Map<String, Boolean> availability = getAvailability();
        List<ReportSourceDefinition> available = new ArrayList<ReportSourceDefinition>();
        for (ReportSourceDefinition source : getAllSources()) {
            if (Boolean.TRUE.equals(availability.get(source.getKey()))) {
                available.add(source);
            }
        }
        return available;
    }

    @Override
    public ReportSourceDefinition getSource(String key) {
        if (key == null) {
            return null;
        }
        for (ReportSourceDefinition source : getAllSources()) {
            if (key.equals(source.getKey())) {
                return source;
            }
        }
        return null;
    }

    @Override
    public boolean isAvailable(String key) {
        return Boolean.TRUE.equals(getAvailability().get(key));
    }

    @Override
    public Map<String, Boolean> getAvailability() {
        Map<String, ReportSourceConfig> configs = configsByKey();
        Map<String, Boolean> availability = new LinkedHashMap<String, Boolean>();
        for (ReportSourceDefinition source : getAllSources()) {
            ReportSourceConfig config = configs.get(source.getKey());
            availability.put(source.getKey(), config == null || config.isAvailable());
        }
        return availability;
    }

    @Override
    public void saveAvailability(Map<String, Boolean> availabilityByKey) throws ValidationFailedException, OperationFailedException {
        if (availabilityByKey == null) {
            return;
        }
        Map<String, ReportSourceConfig> configs = configsByKey();
        for (ReportSourceDefinition source : getAllSources()) {
            Boolean available = availabilityByKey.get(source.getKey());
            if (available == null) {
                continue;
            }
            ReportSourceConfig config = configs.get(source.getKey());
            if (config == null) {
                config = new ReportSourceConfig();
                config.setSourceKey(source.getKey());
            } else if (config.isAvailable() == available) {
                continue;
            }
            config.setAvailable(available);
            saveInstance(config);
        }
    }

    private Map<String, ReportSourceConfig> configsByKey() {
        Search search = new Search();
        search.addFilterEqual("recordStatus", RecordStatus.ACTIVE);
        Map<String, ReportSourceConfig> byKey = new HashMap<String, ReportSourceConfig>();
        List<ReportSourceConfig> configs = super.search(search);
        for (ReportSourceConfig config : configs) {
            byKey.put(config.getSourceKey(), config);
        }
        return byKey;
    }
}
