package org.pahappa.systems.core.services.reporting.impl;

import org.pahappa.systems.core.services.reporting.EntityDiscoveryService;
import org.pahappa.systems.core.services.reporting.ReportableEntityRegistry;
import org.pahappa.systems.reporting.support.AnnotationScanningReportableEntityRegistry;
import org.pahappa.systems.reporting.support.ReportingProperties;
import org.sers.webutils.model.BaseEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service("kpiEntityDiscoveryService")
public class EntityDiscoveryServiceImpl implements EntityDiscoveryService {

    private static final Set<String> TECHNICAL_GETTERS = new HashSet<String>();

    static {
        TECHNICAL_GETTERS.add("getClass");
        TECHNICAL_GETTERS.add("getUuid");
        TECHNICAL_GETTERS.add("getCustomPropOne");
        TECHNICAL_GETTERS.add("getCustomPropTwo");
        TECHNICAL_GETTERS.add("getCustomPropThree");
        TECHNICAL_GETTERS.add("getUserFriendlyAuditTrail");
    }

    @Autowired(required = false)
    private List<ReportableEntityRegistry> registries;

    /** Scans the model packages the host lists in dynamic-reports.properties (none listed: contributes nothing). */
    private volatile ReportableEntityRegistry configuredRegistry;

    @Override
    public List<Class<?>> approvedEntityClasses() {
        Set<Class<?>> approved = new LinkedHashSet<Class<?>>();
        approved.addAll(configuredRegistry().approvedEntityClasses());
        if (this.registries != null) {
            for (ReportableEntityRegistry registry : this.registries) {
                List<Class<?>> contributed = registry.approvedEntityClasses();
                if (contributed != null) {
                    approved.addAll(contributed);
                }
            }
        }
        return new ArrayList<Class<?>>(approved);
    }

    private ReportableEntityRegistry configuredRegistry() {
        ReportableEntityRegistry registry = this.configuredRegistry;
        if (registry == null) {
            List<String> packages = ReportingProperties.modelPackages();
            registry = new AnnotationScanningReportableEntityRegistry(packages.toArray(new String[packages.size()]));
            this.configuredRegistry = registry;
        }
        return registry;
    }

    @Override
    public Class<?> resolveApprovedClass(String className) {
        if (className == null) {
            return null;
        }
        List<Class<?>> approved = approvedEntityClasses();
        for (Class<?> candidate : approved) {
            if (candidate.getName().equals(className)) {
                return candidate;
            }
        }
        return null;
    }

    @Override
    public List<DiscoveredField> discoverFields(Class<?> entityClass) {
        if (entityClass == null || resolveApprovedClass(entityClass.getName()) == null) {
            throw new IllegalArgumentException("Entity class is not on the approved reporting whitelist: " + entityClass);
        }
        List<DiscoveredField> fields = new ArrayList<DiscoveredField>();
        Set<String> seen = new HashSet<String>();
        Method[] methods = entityClass.getMethods();
        for (Method method : methods) {
            String methodName = method.getName();
            if (!isGetter(method) || TECHNICAL_GETTERS.contains(methodName)) {
                continue;
            }
            Class<?> returnType = method.getReturnType();
            if (java.util.Collection.class.isAssignableFrom(returnType) || java.util.Map.class.isAssignableFrom(returnType)) {
                continue;
            }
            String fieldName = toFieldName(methodName);
            if (fieldName.isEmpty() || !seen.add(fieldName)) {
                continue;
            }
            boolean association = BaseEntity.class.isAssignableFrom(returnType);
            boolean enumType = returnType.isEnum();
            fields.add(new DiscoveredField(fieldName, humanize(fieldName), returnType, association, enumType));
        }
        return fields;
    }

    private boolean isGetter(Method method) {
        if (method.getParameterTypes().length != 0) {
            return false;
        }
        String name = method.getName();
        if (name.equals("getClass")) {
            return false;
        }
        if (name.startsWith("get") && name.length() > 3) {
            return !method.getReturnType().equals(void.class);
        }
        if (name.startsWith("is") && name.length() > 2) {
            return method.getReturnType().equals(boolean.class) || method.getReturnType().equals(Boolean.class);
        }
        return false;
    }

    private String toFieldName(String methodName) {
        String stripped;
        if (methodName.startsWith("get")) {
            stripped = methodName.substring(3);
        } else if (methodName.startsWith("is")) {
            stripped = methodName.substring(2);
        } else {
            return "";
        }
        if (stripped.isEmpty()) {
            return "";
        }
        return Character.toLowerCase(stripped.charAt(0)) + stripped.substring(1);
    }

    private String humanize(String fieldName) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < fieldName.length(); i++) {
            char c = fieldName.charAt(i);
            if (i == 0) {
                result.append(Character.toUpperCase(c));
            } else if (Character.isUpperCase(c)) {
                result.append(' ').append(c);
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }
}