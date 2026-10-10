package edu.ums.platform.observe;

import java.util.Objects;

/**
 * Outcome of one health probe.
 *
 * @param name probe name, e.g. {@code database}
 * @param status the probe's status
 * @param detail short operator-facing explanation; never contains secrets or personal data
 */
public record ProbeResult(String name, HealthStatus status, String detail) {

  /** Rejects {@code null} components. */
  public ProbeResult {
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(status, "status");
    Objects.requireNonNull(detail, "detail");
  }
}
