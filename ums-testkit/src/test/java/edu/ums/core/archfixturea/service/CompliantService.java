package edu.ums.core.archfixturea.service;

import java.util.List;

/** Satisfies every rule: depends only on the JDK. */
public class CompliantService {
  private static final int LIMIT = 10;
  private final List<String> names = List.of();

  int limit() {
    return LIMIT + names.size();
  }
}
