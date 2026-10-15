package dev.mtgcmd.game;

import java.util.Map;

/** Strict command data parsing with the existing defaults, ranges, and error messages. */
final class CommandInput {
  private static final String INVALID = "Invalid ";

  private CommandInput() {}

  static String clean(final String value, final String name, final int limit) {
    require(value != null && !value.isBlank() && value.length() <= limit, INVALID + name);
    return value.trim();
  }

  static String string(final Map<String, Object> data, final String key, final int limit) {
    require(data.get(key) instanceof String, INVALID + key);
    return clean((String) data.get(key), key, limit);
  }

  static String optionalString(final Map<String, Object> data, final String key, final int limit) {
    if (!data.containsKey(key)) {
      return "";
    }
    require(
        data.get(key) instanceof String && ((String) data.get(key)).length() <= limit,
        INVALID + key);
    return ((String) data.get(key)).trim();
  }

  static boolean bool(final Map<String, Object> data, final String key, final boolean fallback) {
    if (!data.containsKey(key)) {
      return fallback;
    }
    require(data.get(key) instanceof Boolean, INVALID + key);
    return (Boolean) data.get(key);
  }

  static int integer(
      final Map<String, Object> data,
      final String key,
      final int fallback,
      final int min,
      final int max) {
    if (!data.containsKey(key)) {
      require(fallback >= min && fallback <= max, INVALID + key);
      return fallback;
    }
    require(data.get(key) instanceof Number, INVALID + key);
    return exactInteger((Number) data.get(key), key, min, max);
  }

  static int exactInteger(final Number value, final String key, final int min, final int max) {
    final double number = value.doubleValue();
    require(
        Double.isFinite(number) && number == Math.rint(number) && number >= min && number <= max,
        INVALID + key);
    return (int) number;
  }

  static double decimal(final Map<String, Object> data, final String key) {
    require(data.get(key) instanceof Number, INVALID + key);
    final double number = ((Number) data.get(key)).doubleValue();
    require(Double.isFinite(number) && number >= 0 && number <= 1, INVALID + key);
    return number;
  }

  static void require(final boolean condition, final String message) {
    if (!condition) {
      throw new IllegalArgumentException(message);
    }
  }
}
