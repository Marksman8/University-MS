package edu.ums.api.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a facade method gated by a feature flag. When the flag is off the call fails with {@code
 * FEATURE_DISABLED}.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Feature {

  /** Feature flag key, e.g. {@code REGISTRATION}. */
  String flag();
}
