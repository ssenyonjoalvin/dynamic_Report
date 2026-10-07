package org.pahappa.systems.core.services.reporting;

import java.io.Serializable;
import java.util.List;

public interface EntityDiscoveryService {

    List<Class<?>> approvedEntityClasses();

    List<DiscoveredField> discoverFields(Class<?> entityClass);

    Class<?> resolveApprovedClass(String className);

    class DiscoveredField implements Serializable {
        private final String fieldName;
        private final String label;
        private final Class<?> javaType;
        private final boolean association;
        private final boolean enumType;

        public DiscoveredField(String fieldName, String label, Class<?> javaType, boolean association, boolean enumType) {
            this.fieldName = fieldName;
            this.label = label;
            this.javaType = javaType;
            this.association = association;
            this.enumType = enumType;
        }

        public String getFieldName() {
            return this.fieldName;
        }

        public String getLabel() {
            return this.label;
        }

        public Class<?> getJavaType() {
            return this.javaType;
        }

        public boolean isAssociation() {
            return this.association;
        }

        public boolean isEnumType() {
            return this.enumType;
        }
    }
}
