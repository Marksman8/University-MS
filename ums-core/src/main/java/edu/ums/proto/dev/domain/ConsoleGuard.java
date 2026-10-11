package edu.ums.proto.dev.domain;

/**
 * Accepts exactly one statement starting with SELECT or WITH. Leading whitespace and comments
 * ({@code --} and block comments) are skipped and statement separators found outside quotes and
 * comments with a single character loop (no regex). The console role is read-only as well; this
 * guard is the first line, not the only one.
 */
// Complexity: time O(n) per check, n <= MAX_LENGTH; space O(1)
public final class ConsoleGuard {

  /** Longest accepted statement. */
  public static final int MAX_LENGTH = 10_000;

  /** Outcome of a check. */
  public sealed interface Verdict {}

  /** Accepted; {@code sql} is the statement without a trailing semicolon. */
  public record Allowed(String sql) implements Verdict {}

  /** Rejected with a short reason. */
  public record Rejected(String reason) implements Verdict {}

  private ConsoleGuard() {}

  /** Checks {@code sql}. */
  public static Verdict check(String sql) {
    if (sql == null || sql.isBlank()) {
      return new Rejected("empty statement");
    }
    if (sql.length() > MAX_LENGTH) {
      return new Rejected("statement longer than " + MAX_LENGTH + " characters");
    }
    int start = skipTrivia(sql, 0);
    if (!startsWithKeyword(sql, start, "SELECT") && !startsWithKeyword(sql, start, "WITH")) {
      return new Rejected("only SELECT or WITH statements are allowed");
    }
    int end = statementEnd(sql, start);
    if (end < 0) {
      return new Rejected("unterminated quote or comment");
    }
    if (end < sql.length() && skipTrivia(sql, end + 1) < sql.length()) {
      return new Rejected("exactly one statement is allowed");
    }
    return new Allowed(sql.substring(start, end).strip());
  }

  /** Index of the first character after whitespace and comments. */
  static int skipTrivia(String s, int from) {
    int i = from;
    while (i < s.length()) {
      char c = s.charAt(i);
      if (Character.isWhitespace(c)) {
        i++;
      } else if (c == '-' && i + 1 < s.length() && s.charAt(i + 1) == '-') {
        while (i < s.length() && s.charAt(i) != '\n') {
          i++;
        }
      } else if (c == '/' && i + 1 < s.length() && s.charAt(i + 1) == '*') {
        int close = s.indexOf("*/", i + 2);
        i = close < 0 ? s.length() : close + 2;
      } else {
        break;
      }
    }
    return i;
  }

  /** Index of the first top-level ';' (or length); -1 if a quote or comment never closes. */
  static int statementEnd(String s, int from) {
    int i = from;
    while (i < s.length()) {
      char c = s.charAt(i);
      if (c == ';') {
        return i;
      }
      if (c == '\'' || c == '"') {
        int close = s.indexOf(c, i + 1);
        if (close < 0) {
          return -1;
        }
        i = close + 1;
      } else if (c == '$') {
        return -1; // dollar quoting is not supported by the guard
      } else if (c == '-' && next(s, i) == '-') {
        int newline = s.indexOf('\n', i);
        i = newline < 0 ? s.length() : newline + 1;
      } else if (c == '/' && next(s, i) == '*') {
        int close = s.indexOf("*/", i + 2);
        if (close < 0) {
          return -1;
        }
        i = close + 2;
      } else {
        i++;
      }
    }
    return s.length();
  }

  private static char next(String s, int i) {
    return i + 1 < s.length() ? s.charAt(i + 1) : '\0';
  }

  private static boolean startsWithKeyword(String s, int at, String keyword) {
    int end = at + keyword.length();
    if (end > s.length() || !s.regionMatches(true, at, keyword, 0, keyword.length())) {
      return false;
    }
    return end == s.length() || !Character.isLetterOrDigit(s.charAt(end)) && s.charAt(end) != '_';
  }
}
