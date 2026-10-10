package edu.ums.api;

/** Handle for a registered listener. */
public interface Subscription {

  /** Stops delivery to the listener. Idempotent and thread-safe. */
  void unsubscribe();
}
