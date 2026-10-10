package edu.ums.core.archfixtureb.domain;

import edu.ums.core.archfixturea.domain.CycleA;

/** With CycleA, violates NO_FEATURE_CYCLES. */
public class CycleB {
  CycleA other;
}
