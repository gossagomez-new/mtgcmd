package dev.mtgcmd.game;

import java.util.Map;

/** Strict command data parsing with the existing defaults, ranges, and error messages. */
final class CommandInput {
  private static final String INVALID = "Invalid ";

  private CommandInput() {}

  static String clean(String value, String name, int limit) {
    require(value != null && !value.isBlank() && value.length() <= limit, INVALID + name);
    return value.trim();
  }

  static String string(Map<String, Object> d, String key, int limit) {
    require(d.get(key) instanceof String, INVALID + key);
    return clean((String) d.get(key), key, limit);
  }

  static String optionalString(Map<String, Object> d, String key, int limit) {
    if (!d.containsKey(key)) return "";
    require(d.get(key) instanceof String && ((String) d.get(key)).length() <= limit, INVALID + key);
    return ((String) d.get(key)).trim();
  }

  static boolean bool(Map<String, Object> d, String key, boolean fallback) {
    if (!d.containsKey(key)) return fallback;
    require(d.get(key) instanceof Boolean, INVALID + key);
    return (Boolean) d.get(key);
  }

  static int integer(Map<String, Object> d, String key, int fallback, int min, int max) {
    if (!d.containsKey(key)) {
      require(fallback >= min && fallback <= max, INVALID + key);
      return fallback;
    }
    require(d.get(key) instanceof Number, INVALID + key);
    return exactInteger((Number) d.get(key), key, min, max);
  }

  static int exactInteger(Number n, String key, int min, int max) {
    double v = n.doubleValue();
    require(Double.isFinite(v) && v == Math.rint(v) && v >= min && v <= max, INVALID + key);
    return (int) v;
  }

  static double decimal(Map<String, Object> d, String key) {
    require(d.get(key) instanceof Number, INVALID + key);
    double v = ((Number) d.get(key)).doubleValue();
    require(Double.isFinite(v) && v >= 0 && v <= 1, INVALID + key);
    return v;
  }

  static void require(boolean condition, String message) {
    if (!condition) throw new IllegalArgumentException(message);
  }
}
