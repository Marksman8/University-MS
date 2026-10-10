package edu.ums.platform.db;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.ums.api.dto.Cursor;
import edu.ums.api.error.ErrorCode;
import edu.ums.platform.db.KeysetQuery.KeyColumn;
import edu.ums.platform.error.InfrastructureException;
import edu.ums.platform.error.ValidationException;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * Encodes a keyset sort key {@code (sortKey..., id)} as an opaque cursor: URL-safe Base64 of a JSON
 * array of strings. Decoding validates the shape against the expected key columns; any tampering
 * yields {@code VALIDATION_FAILED}. Thread-safe.
 */
// Complexity: time O(k + L) per encode/decode, k key columns, L token length (<= 512); space O(L)
public final class CursorCodec {

  private static final TypeReference<List<String>> STRINGS = new TypeReference<>() {};
  private static final Map<String, Object> INVALID = Map.of("field", "cursor", "reason", "invalid");

  private final ObjectMapper json = new ObjectMapper();

  /**
   * Encodes {@code values} (one per key column).
   *
   * @throws InfrastructureException {@code INTERNAL_ERROR} if the key does not fit in a cursor
   */
  public Cursor encode(List<Object> values, List<KeyColumn> keys) {
    if (values.size() != keys.size()) {
      throw internal("key has " + values.size() + " values for " + keys.size() + " columns");
    }
    List<String> text = new ArrayList<>(values.size());
    for (int i = 0; i < values.size(); i++) {
      text.add(keys.get(i).type().format(values.get(i)));
    }
    String token;
    try {
      token = Base64.getUrlEncoder().withoutPadding().encodeToString(json.writeValueAsBytes(text));
    } catch (JsonProcessingException e) {
      throw new InfrastructureException(ErrorCode.INTERNAL_ERROR, e);
    }
    if (token.length() > Cursor.MAX_LENGTH) {
      throw internal("cursor longer than " + Cursor.MAX_LENGTH + " characters");
    }
    return new Cursor(token);
  }

  /**
   * Decodes {@code cursor} into typed values, one per key column.
   *
   * @throws ValidationException {@code VALIDATION_FAILED} if the cursor is malformed or tampered
   */
  public List<Object> decode(Cursor cursor, List<KeyColumn> keys) {
    List<String> text;
    try {
      byte[] raw =
          Base64.getUrlDecoder().decode(cursor.token().getBytes(StandardCharsets.US_ASCII));
      text = json.readValue(raw, STRINGS);
    } catch (IllegalArgumentException | java.io.IOException malformed) {
      throw new ValidationException(INVALID, malformed);
    }
    if (text == null || text.size() != keys.size() || text.contains(null)) {
      throw new ValidationException(INVALID);
    }
    List<Object> values = new ArrayList<>(keys.size());
    try {
      for (int i = 0; i < keys.size(); i++) {
        values.add(keys.get(i).type().parse(text.get(i)));
      }
    } catch (NumberFormatException | DateTimeParseException malformed) {
      throw new ValidationException(INVALID, malformed);
    }
    return values;
  }

  private static InfrastructureException internal(String reason) {
    return new InfrastructureException(ErrorCode.INTERNAL_ERROR, new IllegalStateException(reason));
  }
}
