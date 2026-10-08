package org.pahappa.systems.reporting.support;

import org.pahappa.systems.core.services.reporting.ReportableEntityRegistry;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;

import javax.persistence.Entity;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * Generic {@link ReportableEntityRegistry} that discovers reportable entities
 * by classpath annotation scanning instead of a hand-maintained class list:
 * every class annotated {@code @Entity} under the given base package(s) -
 * including ones only ever referenced as a foreign-key/association target -
 * becomes reportable, with zero per-entity maintenance as the domain model
 * grows.
 * <p>
 * A host normally needs no subclass: listing its model package(s) under
 * {@code dynamic.reports.model.packages} in {@code dynamic-reports.properties}
 * (see {@link ReportingProperties}) makes the plugin scan them itself. A
 * subclass is only needed for packages that must be added in code, e.g.:
 * <pre>
 * {@literal @}Service
 * public class MyProjectReportableEntityRegistry extends AnnotationScanningReportableEntityRegistry {
 *     public MyProjectReportableEntityRegistry() {
 *         super("com.example.myproject.models");
 *     }
 * }
 * </pre>
 */
public class AnnotationScanningReportableEntityRegistry implements ReportableEntityRegistry {

    private final List<String> basePackages;
    private volatile List<Class<?>> cachedClasses;

    public AnnotationScanningReportableEntityRegistry(String... basePackages) {
        this.basePackages = Arrays.asList(basePackages);
    }

    @Override
    public List<Class<?>> approvedEntityClasses() {
        if (this.cachedClasses == null) {
            this.cachedClasses = scan();
        }
        return this.cachedClasses;
    }

    private List<Class<?>> scan() {
        List<Class<?>> found = new ArrayList<Class<?>>();
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Entity.class));
        for (String basePackage : this.basePackages) {
            Set<BeanDefinition> candidates = scanner.findCandidateComponents(basePackage);
            for (BeanDefinition candidate : candidates) {
                try {
                    found.add(Class.forName(candidate.getBeanClassName()));
                } catch (ClassNotFoundException e) {
                    // the scanner found it on the classpath, so this shouldn't happen; skip defensively
                }
            }
        }
        return found;
    }
}
