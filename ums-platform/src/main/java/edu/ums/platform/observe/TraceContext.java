package edu.ums.platform.observe;

import java.util.Objects;
import java.util.UUID;

/**
 * Identity of the facade call currently running on this thread.
 *
 * @param traceId random id shared by every nested call made while handling one outer call
 */
public record TraceContext(UUID traceId) {

  /** Rejects a {@code null} id. */
  public TraceContext {
    Objects.requireNonNull(traceId, "traceId");
  }
}
