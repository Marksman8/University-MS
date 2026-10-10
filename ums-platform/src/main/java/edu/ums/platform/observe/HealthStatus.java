package edu.ums.platform.observe;

/** Health of one probe or of the whole application; declared from best to worst. */
public enum HealthStatus {
  UP,
  DEGRADED,
  DOWN;

  /** Returns the worse of this and {@code other}. */
  public HealthStatus worst(HealthStatus other) {
    return compareTo(other) >= 0 ? this : other;
  }
}
