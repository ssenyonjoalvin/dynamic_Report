package org.pahappa.systems.reporting.support;

import org.pahappa.systems.models.reporting.FieldKind;
import org.sers.webutils.model.BaseEntity;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

/**
 * Value helpers shared by the report engine and the builder UI: labels,
 * type classification, filter-value coercion and cell formatting.
 */
public final class ReportValues {

    private static final String[] DATE_PATTERNS = {"yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd'T'HH:mm:ss", "yyyy-MM-dd", "dd/MM/yyyy"};

    private ReportValues() {
    }

    /** "dateOfBirth" -> "Date Of Birth", "PRWRequiredAttachment" -> "PRW Required Attachment". */
    public static String humanize(String name) {
        if (name == null || name.isEmpty()) {
            return "";
        }
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (i == 0) {
                result.append(Character.toUpperCase(c));
            } else if (Character.isUpperCase(c) && (!Character.isUpperCase(name.charAt(i - 1))
                    // end of an acronym: "PRWRequired" -> "PRW Required"
                    || (i + 1 < name.length() && Character.isLowerCase(name.charAt(i + 1))))) {
                result.append(' ').append(c);
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }

    /** "ON_LEAVE" -> "On Leave". */
    public static String humanizeConstant(String constant) {
        if (constant == null) {
            return "";
        }
        StringBuilder result = new StringBuilder();
        for (String word : constant.toLowerCase().split("_")) {
            if (word.isEmpty()) {
                continue;
            }
            if (result.length() > 0) {
                result.append(' ');
            }
            result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return result.toString();
    }

    public static FieldKind kindOf(Class<?> type) {
        if (type == null) {
            return null;
        }
        if (type.isEnum()) {
            return FieldKind.ENUM;
        }
        if (BaseEntity.class.isAssignableFrom(type)) {
            return FieldKind.REFERENCE;
        }
        if (type == String.class || type == Character.class || type == Character.TYPE) {
            return FieldKind.TEXT;
        }
        if (type == Boolean.class || type == Boolean.TYPE) {
            return FieldKind.BOOLEAN;
        }
        if (Date.class.isAssignableFrom(type)) {
            return FieldKind.DATE;
        }
        if (Number.class.isAssignableFrom(type) || (type.isPrimitive() && type != Void.TYPE)) {
            return FieldKind.NUMBER;
        }
        return null;
    }

    /**
     * Display label for an enum constant: the enum's own getDisplayName /
     * getLabel / getName when it has one (webutils' Gender has getName() ->
     * "Male"), then an overridden toString(), then the humanized constant.
     */
    public static String enumLabel(Enum<?> constant) {
        if (constant == null) {
            return null;
        }
        for (String getter : new String[]{"getDisplayName", "getLabel", "getName", "getTitle"}) {
            try {
                Method method = constant.getClass().getMethod(getter);
                if (method.getReturnType() == String.class) {
                    Object value = method.invoke(constant);
                    if (value != null && !((String) value).trim().isEmpty()) {
                        return (String) value;
                    }
                }
            } catch (Exception e) {
                // no such getter - try the next one
            }
        }
        String text = constant.toString();
        if (text != null && !text.equals(constant.name())) {
            return text;
        }
        return humanizeConstant(constant.name());
    }

    /**
     * Readable text for an entity. webutils' BaseEntity.toString() returns
     * the literal "Equals not implemented" unless a subclass overrides it,
     * so fall back to common name-like getters, then "Type #id".
     */
    public static String entityLabel(Object entity) {
        if (entity == null) {
            return null;
        }
        String direct = String.valueOf(entity);
        if (!"Equals not implemented".equals(direct) && !direct.trim().isEmpty()) {
            return direct;
        }
        String simpleName = entity.getClass().getSimpleName();
        String[] candidates = {"getName", "get" + simpleName + "Name", "getTitle", "getDisplayName", "getLabel",
                "getFullName", "getCode", "getDescription"};
        for (String getterName : candidates) {
            try {
                Object result = entity.getClass().getMethod(getterName).invoke(entity);
                if (result instanceof String && !((String) result).trim().isEmpty()) {
                    return (String) result;
                }
            } catch (Exception e) {
                // getter doesn't exist or failed - try the next one
            }
        }
        return simpleName + " #" + ((BaseEntity) entity).getId();
    }

    /** Formats a raw query value for a report cell or CSV. */
    public static Object present(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof BaseEntity) {
            return entityLabel(raw);
        }
        if (raw instanceof Enum) {
            return enumLabel((Enum<?>) raw);
        }
        if (raw instanceof Boolean) {
            return ((Boolean) raw) ? "Yes" : "No";
        }
        if (raw instanceof Date) {
            return formatDate((Date) raw);
        }
        if (raw instanceof Double || raw instanceof Float) {
            BigDecimal decimal = BigDecimal.valueOf(((Number) raw).doubleValue()).stripTrailingZeros();
            return decimal.scale() < 0 ? decimal.setScale(0) : decimal;
        }
        return raw;
    }

    public static String formatDate(Date date) {
        if (date == null) {
            return null;
        }
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        boolean dateOnly = calendar.get(Calendar.HOUR_OF_DAY) == 0 && calendar.get(Calendar.MINUTE) == 0
                && calendar.get(Calendar.SECOND) == 0;
        return new SimpleDateFormat(dateOnly ? "dd MMM yyyy" : "dd MMM yyyy HH:mm").format(date);
    }

    public static Date parseDate(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        for (String pattern : DATE_PATTERNS) {
            try {
                SimpleDateFormat format = new SimpleDateFormat(pattern);
                format.setLenient(false);
                return format.parse(value.trim());
            } catch (ParseException e) {
                // try next pattern
            }
        }
        return null;
    }

    public static Date startOfDay(Date date) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTime();
    }

    public static Date addDays(Date date, int days) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        calendar.add(Calendar.DAY_OF_MONTH, days);
        return calendar.getTime();
    }

    public static Date addYears(Date date, int years) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        calendar.add(Calendar.YEAR, years);
        return calendar.getTime();
    }

    /**
     * Converts typed-in filter text to the field's Java type so the bound
     * query parameter matches the column (ordering comparisons on numbers
     * must not be done on strings). Returns null when the text doesn't parse.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static Object coerce(String text, Class<?> type) {
        if (text == null || type == null) {
            return null;
        }
        String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        try {
            if (type.isEnum()) {
                return Enum.valueOf((Class<? extends Enum>) type, trimmed);
            }
            if (type == String.class) {
                return text;
            }
            if (Date.class.isAssignableFrom(type)) {
                return parseDate(trimmed);
            }
            if (type == Integer.class || type == Integer.TYPE) {
                return Integer.valueOf(new BigDecimal(trimmed).intValueExact());
            }
            if (type == Long.class || type == Long.TYPE) {
                return Long.valueOf(new BigDecimal(trimmed).longValueExact());
            }
            if (type == Short.class || type == Short.TYPE) {
                return Short.valueOf(new BigDecimal(trimmed).shortValueExact());
            }
            if (type == Byte.class || type == Byte.TYPE) {
                return Byte.valueOf(new BigDecimal(trimmed).byteValueExact());
            }
            if (type == Double.class || type == Double.TYPE) {
                return Double.valueOf(trimmed);
            }
            if (type == Float.class || type == Float.TYPE) {
                return Float.valueOf(trimmed);
            }
            if (type == BigDecimal.class) {
                return new BigDecimal(trimmed);
            }
            if (type == BigInteger.class) {
                return new BigInteger(trimmed);
            }
            if (type == Boolean.class || type == Boolean.TYPE) {
                return Boolean.valueOf(trimmed);
            }
            if (type == Character.class || type == Character.TYPE) {
                return trimmed.charAt(0);
            }
        } catch (RuntimeException e) {
            return null;
        }
        return text;
    }

    /** Numeric view of a value for calculations: numbers, booleans (1/0), numeric text. */
    public static Double toDouble(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        if (value instanceof Boolean) {
            return ((Boolean) value) ? 1d : 0d;
        }
        if (value instanceof Date) {
            return (double) ((Date) value).getTime();
        }
        try {
            return Double.valueOf(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static String toText(Object value) {
        Object presented = present(value);
        return presented == null ? "" : String.valueOf(presented);
    }
}
