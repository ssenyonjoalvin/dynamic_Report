package org.pahappa.systems.reporting.support;

public final class SqlIdentifierQuoter {

    private SqlIdentifierQuoter() {
    }

    public static String quoteIdentifier(String identifier) {
        return "`" + identifier.replace("`", "``") + "`";
    }

    public static String quoteQualified(String table, String column) {
        return quoteIdentifier(table) + "." + quoteIdentifier(column);
    }

    public static String escapeLikeValue(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
