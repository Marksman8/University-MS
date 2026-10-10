package edu.ums.platform.db;

/** Validates SQL identifiers that are concatenated into statements. Pure helpers, no regex. */
// Complexity: time O(n) per check, n = identifier length (<= 63); space O(1)
final class SqlIdentifiers {

  /** PostgreSQL's maximum identifier length. */
  static final int MAX_LENGTH = 63;

  private SqlIdentifiers() {}

  /**
   * Returns {@code true} for a lower-case identifier: {@code [a-z_][a-z0-9_]*}, at most 63 chars.
   */
  static boolean isPlain(String name) {
    if (name == null || name.isEmpty() || name.length() > MAX_LENGTH) {
      return false;
    }
    if (!isStart(name.charAt(0))) {
      return false;
    }
    for (int i = 1; i < name.length(); i++) {
      if (!isPart(name.charAt(i))) {
        return false;
      }
    }
    return true;
  }

  /**
   * Returns {@code true} for a plain identifier optionally qualified once: {@code alias.column}.
   */
  static boolean isColumn(String name) {
    if (name == null) {
      return false;
    }
    int dot = name.indexOf('.');
    if (dot < 0) {
      return isPlain(name);
    }
    return isPlain(name.substring(0, dot)) && isPlain(name.substring(dot + 1));
  }

  private static boolean isStart(char c) {
    return (c >= 'a' && c <= 'z') || c == '_';
  }

  private static boolean isPart(char c) {
    return isStart(c) || (c >= '0' && c <= '9');
  }
}
