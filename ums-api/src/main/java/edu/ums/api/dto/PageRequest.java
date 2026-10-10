package edu.ums.api.dto;

import edu.ums.api.error.ErrorCode;
import edu.ums.api.error.UmsApiException;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Request for one page of a keyset-paged list.
 *
 * @param after cursor returned by the previous page, empty for the first page
 * @param size page size, {@value #MIN_SIZE}..{@value #MAX_SIZE}
 */
public record PageRequest(Optional<Cursor> after, int size) {

  /** Smallest allowed page size. */
  public static final int MIN_SIZE = 1;

  /** Largest allowed page size. */
  public static final int MAX_SIZE = 200;

  /**
   * Validates the request.
   *
   * @throws UmsApiException {@code VALIDATION_FAILED} if {@code size} is out of range
   */
  public PageRequest {
    Objects.requireNonNull(after, "after");
    if (size < MIN_SIZE || size > MAX_SIZE) {
      throw new UmsApiException(
          ErrorCode.VALIDATION_FAILED,
          ErrorCode.VALIDATION_FAILED.messageKey(),
          Map.of("field", "size", "min", MIN_SIZE, "max", MAX_SIZE));
    }
  }

  /** Returns a request for the first page. */
  public static PageRequest first(int size) {
    return new PageRequest(Optional.empty(), size);
  }
}
