package edu.ums.api.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a facade method limited per session. Calls beyond the limit fail with {@code RATE_LIMITED}.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface RateLimited {

  /** Maximum calls per session per minute; must be positive. */
  int perMinute();
}
