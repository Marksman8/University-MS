package edu.ums.platform.observe;

/** One health check (Strategy). Implementations block; {@link HealthService} bounds their time. */
public interface HealthProbe {

  /** Returns the probe's stable name. */
  String name();

  /**
   * Runs the check. May throw; the service reports a throwing or slow probe as {@link
   * HealthStatus#DOWN}.
   */
  ProbeResult check();
}
