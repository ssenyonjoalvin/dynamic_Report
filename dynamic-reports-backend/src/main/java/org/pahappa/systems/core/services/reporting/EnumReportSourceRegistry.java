package org.pahappa.systems.core.services.reporting;

import java.util.Arrays;
import java.util.List;

/**
 * Registers every constant of a report source enum, in declaration order:
 *
 * <pre>
 * &#64;Service
 * public class MyReportSourceRegistry extends EnumReportSourceRegistry&lt;MyReportSource&gt; {
 *     public MyReportSourceRegistry() {
 *         super(MyReportSource.class);
 *     }
 * }
 * </pre>
 */
public abstract class EnumReportSourceRegistry<E extends Enum<E> & ReportSourceDefinition> implements ReportSourceRegistry {

    private final Class<E> enumType;

    protected EnumReportSourceRegistry(Class<E> enumType) {
        this.enumType = enumType;
    }

    @Override
    public List<? extends ReportSourceDefinition> reportSources() {
        return Arrays.asList(this.enumType.getEnumConstants());
    }
}
