package edu.ums.api.dto;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * One page of a keyset-paged list.
 *
 * @param items the page content, unmodifiable, never {@code null}
 * @param next cursor for the following page, empty on the last page
 */
public record Page<T>(List<T> items, Optional<Cursor> next) {

  /** Copies {@code items} and rejects {@code null} components. */
  public Page {
    items = List.copyOf(Objects.requireNonNull(items, "items"));
    Objects.requireNonNull(next, "next");
  }

  /** Returns the last page: {@code items} with no next cursor. */
  public static <T> Page<T> last(List<T> items) {
    return new Page<>(items, Optional.empty());
  }
}
