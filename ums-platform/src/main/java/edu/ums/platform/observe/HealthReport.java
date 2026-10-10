package edu.ums.platform.observe;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Result of one health check run.
 *
 * @param status worst status across probes ({@code UP} when there are none)
 * @param probes per-probe results, in probe registration order
 * @param checkedAt when the run started
 */
public record HealthReport(HealthStatus status, List<ProbeResult> probes, Instant checkedAt) {

  /** Copies the probe list and rejects {@code null}s. */
  public HealthReport {
    Objects.requireNonNull(status, "status");
    probes = List.copyOf(probes);
    Objects.requireNonNull(checkedAt, "checkedAt");
  }
}
