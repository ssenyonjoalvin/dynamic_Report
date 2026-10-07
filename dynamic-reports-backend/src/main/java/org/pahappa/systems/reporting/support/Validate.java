package org.pahappa.systems.reporting.support;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.sers.webutils.model.exception.ValidationFailedException;

import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Map;

import static java.lang.String.format;

/**
 * Vendored copy of the reporting module's validation helper (adapted from
 * Spring's Assert class but throwing ValidationFailedException), kept
 * self-contained so dynamic-reports-backend has no dependency on a host
 * project's own utility classes.
 */
public abstract class Validate {

	public static void check(Boolean expression, String message, Object... params) throws ValidationFailedException {
		isTrue(expression, format(message, params));
	}

	public static void state(Boolean expression, String message) throws ValidationFailedException {
		if (!Boolean.TRUE.equals(expression)) {
			throw new ValidationFailedException(message);
		}
	}

	public static void isTrue(Boolean expression, String message, Object... params) throws ValidationFailedException {
		if (!Boolean.TRUE.equals(expression)) {
			throw new ValidationFailedException(format(message, params));
		}
	}

	public static void isNull(Object object, String message, Object... args) throws ValidationFailedException {
		if (object != null) {
			throw new ValidationFailedException(format(message, args));
		}
	}

	public static void notNull(Object object, String message, Object... args) throws ValidationFailedException {
		if (object == null) {
			throw new ValidationFailedException(format(message, args));
		}
	}

	public static void hasLength(String text, String message) throws ValidationFailedException {
		if (!StringUtils.isNotEmpty(text)) {
			throw new ValidationFailedException(message);
		}
	}

	public static void hasText(String text, String message) throws ValidationFailedException {
		if (!StringUtils.isNotBlank(text)) {
			throw new ValidationFailedException(message);
		}
	}

	public static void doesNotContain(String textToSearch, String substring, String message)
			throws ValidationFailedException {
		if (StringUtils.isNotEmpty(textToSearch) && StringUtils.isNotEmpty(substring)
				&& textToSearch.contains(substring)) {
			throw new ValidationFailedException(message);
		}
	}

	public static void notEmpty(Object[] array, String message) throws ValidationFailedException {
		if (ArrayUtils.isEmpty(array)) {
			throw new ValidationFailedException(message);
		}
	}

	public static void noNullElements(Object[] array, String message) throws ValidationFailedException {
		if (array != null) {
			for (Object element : array) {
				if (element == null) {
					throw new ValidationFailedException(message);
				}
			}
		}
	}

	public static void notEmpty(Collection<?> collection, String message) throws ValidationFailedException {
		if (CollectionUtils.isEmpty(collection)) {
			throw new ValidationFailedException(message);
		}
	}

	public static void notEmpty(Map<?, ?> map, String message) throws ValidationFailedException {
		if (MapUtils.isEmpty(map)) {
			throw new ValidationFailedException(message);
		}
	}

	public static void isInstanceOf(Class<?> type, Object obj, String message) throws ValidationFailedException {
		notNull(type, "Type to check against must not be null");
		if (!type.isInstance(obj)) {
			instanceCheckFailed(type, obj, message);
		}
	}

	public static void isInstanceOf(Class<?> type, Object obj) throws ValidationFailedException {
		isInstanceOf(type, obj, "");
	}

	public static void isAssignable(Class<?> superType, Class<?> subType, String message)
			throws ValidationFailedException {
		notNull(superType, "Super type to check against must not be null");
		if (subType == null || !superType.isAssignableFrom(subType)) {
			assignableCheckFailed(superType, subType, message);
		}
	}

	public static void isAssignable(Class<?> superType, Class<?> subType) throws ValidationFailedException {
		isAssignable(superType, subType, "");
	}

	private static void instanceCheckFailed(Class<?> type, Object obj, String msg) throws ValidationFailedException {
		String className = (obj != null ? obj.getClass().getName() : "null");
		String result = "";
		boolean defaultMessage = true;
		if (StringUtils.isNotEmpty(msg)) {
			if (endsWithSeparator(msg)) {
				result = msg + " ";
			} else {
				result = messageWithTypeName(msg, className);
				defaultMessage = false;
			}
		}
		if (defaultMessage) {
			result = result + ("Object of class [" + className + "] must be an instance of " + type);
		}
		throw new ValidationFailedException(result);
	}

	private static void assignableCheckFailed(Class<?> superType, Class<?> subType, String msg)
			throws ValidationFailedException {
		String result = "";
		boolean defaultMessage = true;

		if (StringUtils.isNotEmpty(msg)) {
			if (endsWithSeparator(msg)) {
				result = msg + " ";
			} else {
				result = messageWithTypeName(msg, subType);
				defaultMessage = false;
			}
		}
		if (defaultMessage) {
			result = result + (subType + " is not assignable to " + superType);
		}
		throw new ValidationFailedException(result);
	}

	private static boolean endsWithSeparator(String msg) {
		return (msg.endsWith(":") || msg.endsWith(";") || msg.endsWith(",") || msg.endsWith("."));
	}

	private static String messageWithTypeName(String msg, Object typeName) {
		return msg + (msg.endsWith(" ") ? "" : ": ") + typeName;
	}

	public static void validateDayMonth(String dateString, String format, String message)
			throws ValidationFailedException {
		try {
			SimpleDateFormat sdf = new SimpleDateFormat(format);
			sdf.parse(dateString);
		} catch (Exception exc) {
			throw new ValidationFailedException(message + " " + exc.getMessage());
		}
	}

	public static void equalObjects(Object object1, Object object2, String message) throws ValidationFailedException {
		if (object1 == null || object2 == null) {
			throw new ValidationFailedException(message);
		} else {
			if (!object1.equals(object2)) {
				throw new ValidationFailedException(message);
			}
		}
	}

	public static void isFalse(Boolean expression, String message, Object... params) throws ValidationFailedException {
		isTrue(!expression, message, params);
	}

}
