package edu.ums.platform.concurrent;

/** Shutdown stages, run in declaration order (master prompt §9). */
public enum ShutdownStage {
  /** Stop accepting new facade calls (readiness off). */
  ACCEPT_STOP,
  /** Drain queues that must not lose data (audit writer). */
  DRAINERS,
  /** Stop scheduled jobs. */
  SCHEDULER,
  /** Stop listeners (activity listener, listener dispatch). */
  LISTENERS,
  /** Stop worker pools, then close connection pools. */
  POOLS
}
