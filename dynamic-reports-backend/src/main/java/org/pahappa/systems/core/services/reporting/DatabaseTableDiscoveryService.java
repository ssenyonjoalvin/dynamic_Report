package org.pahappa.systems.core.services.reporting;

import java.io.Serializable;
import java.util.List;

public interface DatabaseTableDiscoveryService {

    List<String> listTables();

    List<DiscoveredColumn> discoverColumns(String tableName);

    class DiscoveredColumn implements Serializable {
        private final String columnName;
        private final String typeName;
        private final boolean foreignKey;
        private final String referencedTable;
        private final String referencedColumn;

        public DiscoveredColumn(String columnName, String typeName, boolean foreignKey,
                                 String referencedTable, String referencedColumn) {
            this.columnName = columnName;
            this.typeName = typeName;
            this.foreignKey = foreignKey;
            this.referencedTable = referencedTable;
            this.referencedColumn = referencedColumn;
        }

        public String getColumnName() {
            return this.columnName;
        }

        public String getTypeName() {
            return this.typeName;
        }

        public boolean isForeignKey() {
            return this.foreignKey;
        }

        public String getReferencedTable() {
            return this.referencedTable;
        }

        public String getReferencedColumn() {
            return this.referencedColumn;
        }
    }
}
