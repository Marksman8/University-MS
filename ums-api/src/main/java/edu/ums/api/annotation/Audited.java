package edu.ums.api.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Marks a facade method whose outcome is written to the activity log under {@link #action()}. */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Audited {

  /** Activity-log action name, e.g. {@code REG_ENROL}. */
  String action();
}
