package edu.ums.api.id;

/**
 * A typed database identifier. Concrete ids ({@code StudentId}, {@code TermId}, ...) are records
 * implementing this interface and arrive with their features.
 */
public interface EntityId {

  /** Returns the raw identity value; always positive. */
  long value();
}
