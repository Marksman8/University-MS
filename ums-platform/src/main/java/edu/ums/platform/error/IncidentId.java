package edu.ums.platform.error;

import java.util.Objects;
import java.util.random.RandomGenerator;
import java.util.regex.Pattern;

/**
 * Short correlation id for an {@code INTERNAL_ERROR}: 8 characters of RFC 4648 Base32 (40 random
 * bits), shown to the user and written to the ERROR log line.
 *
 * @param value exactly 8 characters from {@code A-Z2-7}
 */
public record IncidentId(String value) {

  /** Number of characters. */
  public static final int LENGTH = 8;

  private static final char[] ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".toCharArray();
  private static final Pattern FORMAT = Pattern.compile("[A-Z2-7]{8}");
  private static final int BITS_PER_CHAR = 5;
  private static final long MASK = (1L << (LENGTH * BITS_PER_CHAR)) - 1;

  /** Validates the format. */
  public IncidentId {
    Objects.requireNonNull(value, "value");
    if (!FORMAT.matcher(value).matches()) {
      throw new IllegalArgumentException("Incident id must be 8 Base32 characters");
    }
  }

  /** Generates a new id from {@code random}. Pure function of the random source. */
  public static IncidentId random(RandomGenerator random) {
    long bits = random.nextLong() & MASK;
    char[] chars = new char[LENGTH];
    for (int i = LENGTH - 1; i >= 0; i--) {
      chars[i] = ALPHABET[(int) (bits & 0x1F)];
      bits >>>= BITS_PER_CHAR;
    }
    return new IncidentId(new String(chars));
  }

  @Override
  public String toString() {
    return value;
  }
}
