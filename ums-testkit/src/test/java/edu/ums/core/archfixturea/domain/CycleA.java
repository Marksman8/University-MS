package edu.ums.core.archfixturea.domain;

import edu.ums.core.archfixtureb.domain.CycleB;

/** With CycleB, violates NO_FEATURE_CYCLES (archfixturea <-> archfixtureb). */
public class CycleA {
  CycleB other;
}
