package org.pahappa.systems.reporting.support;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

/**
 * Host-supplied configuration, read from {@value #FILE_NAME} at the root of
 * the host application's classpath (e.g. {@code src/main/resources}), so a
 * host configures the plugin without writing any Java:
 * <pre>
 * # Comma-separated packages holding the host's own JPA entities. Only
 * # entities under these packages become report sources; entities from other
 * # plugins and libraries on the classpath are ignored.
 * dynamic.reports.model.packages=com.example.myproject.models
 * </pre>
 * A JVM system property of the same name overrides the file.
 */
public final class ReportingProperties {

    public static final String FILE_NAME = "dynamic-reports.properties";
    public static final String MODEL_PACKAGES = "dynamic.reports.model.packages";

    private static volatile List<String> modelPackages;

    private ReportingProperties() {
    }

    /**
     * @return the configured model packages, or an empty list when none are
     *         configured (every mapped entity is then eligible). Never null.
     */
    public static List<String> modelPackages() {
        List<String> cached = modelPackages;
        if (cached == null) {
            cached = parsePackages(System.getProperty(MODEL_PACKAGES, load().getProperty(MODEL_PACKAGES)));
            modelPackages = cached;
        }
        return cached;
    }

    /** @return whether {@code type} lies in a configured model package, or true when none are configured. */
    public static boolean isInModelPackages(Class<?> type) {
        List<String> packages = modelPackages();
        if (packages.isEmpty()) {
            return true;
        }
        String name = type.getName();
        for (String basePackage : packages) {
            if (name.startsWith(basePackage + ".")) {
                return true;
            }
        }
        return false;
    }

    private static Properties load() {
        Properties properties = new Properties();
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        if (loader == null) {
            loader = ReportingProperties.class.getClassLoader();
        }
        InputStream in = loader.getResourceAsStream(FILE_NAME);
        if (in == null) {
            return properties;
        }
        try {
            properties.load(in);
        } catch (IOException e) {
            // unreadable file - behave as if it were absent
        } finally {
            try {
                in.close();
            } catch (IOException e) {
                // ignore
            }
        }
        return properties;
    }

    private static List<String> parsePackages(String value) {
        if (value == null) {
            return Collections.emptyList();
        }
        List<String> packages = new ArrayList<String>();
        for (String part : value.split("[,;\\s]+")) {
            String trimmed = part.trim();
            while (trimmed.endsWith(".") || trimmed.endsWith("*")) {
                trimmed = trimmed.substring(0, trimmed.length() - 1);
            }
            if (!trimmed.isEmpty()) {
                packages.add(trimmed);
            }
        }
        return Collections.unmodifiableList(packages);
    }
}
