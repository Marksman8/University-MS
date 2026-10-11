package edu.ums.proto.auth.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.HexFormat;
import org.mindrot.jbcrypt.BCrypt;

/**
 * BCrypt hashing for the prototype. Every method zeroes the {@code char[]} it is given. Unknown
 * users are verified against a dummy hash so their timing matches a wrong password.
 */
// Complexity: time O(2^cost) per hash/verify; space O(1)
public final class PasswordHasher {

  /** Production work factor. */
  public static final int COST = 12;

  private static final int HASH_LENGTH = 60;

  private final int cost;
  private final SecureRandom random;
  private final String dummyHash;

  /** Creates a hasher with the given BCrypt cost (4..31). */
  public PasswordHasher(int cost, SecureRandom random) {
    if (cost < 4 || cost > 31) {
      throw new IllegalArgumentException("BCrypt cost must be 4..31");
    }
    this.cost = cost;
    this.random = random;
    byte[] seed = new byte[16];
    random.nextBytes(seed);
    this.dummyHash = BCrypt.hashpw(HexFormat.of().formatHex(seed), BCrypt.gensalt(cost, random));
  }

  /** Hashes and zeroes {@code password}. */
  public String hash(char[] password) {
    try {
      return BCrypt.hashpw(new String(password), BCrypt.gensalt(cost, random));
    } finally {
      Arrays.fill(password, '\0');
    }
  }

  /** Verifies and zeroes {@code password}; a malformed hash is a mismatch. */
  public boolean verify(char[] password, String storedHash) {
    try {
      String target = wellFormed(storedHash) ? storedHash : dummyHash;
      String candidate = BCrypt.hashpw(new String(password), target);
      return target == storedHash
          && MessageDigest.isEqual(
              candidate.getBytes(StandardCharsets.US_ASCII),
              storedHash.getBytes(StandardCharsets.US_ASCII));
    } finally {
      Arrays.fill(password, '\0');
    }
  }

  /** Spends the same time as a real verification, then returns false. */
  public boolean verifyDummy(char[] password) {
    verify(password, dummyHash);
    return false;
  }

  /** Generates a random temporary password (16 characters, letters, digits and symbols). */
  public char[] temporaryPassword() {
    String alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789@#%";
    char[] out = new char[16];
    for (int i = 0; i < out.length; i++) {
      out[i] = alphabet.charAt(random.nextInt(alphabet.length()));
    }
    return out;
  }

  private static boolean wellFormed(String hash) {
    return hash != null && hash.length() == HASH_LENGTH && hash.startsWith("$2");
  }
}
