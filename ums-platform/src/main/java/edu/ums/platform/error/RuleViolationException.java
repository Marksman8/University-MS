package edu.ums.platform.error;

import edu.ums.api.error.ErrorCode;
import edu.ums.api.error.ErrorCode.Area;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * A specific business rule rejected the request, e.g. {@code SECTION_FULL}, {@code CREDIT_LIMIT},
 * {@code LAST_ADMIN}, {@code USERNAME_TAKEN}. Policy and infrastructure codes are not allowed.
 */
public final class RuleViolationException extends DomainException {

  private static final long serialVersionUID = 1L;

  private static final Set<Area> ALLOWED =
      EnumSet.of(Area.AUTH, Area.DOMAIN, Area.OFFER, Area.REG, Area.ATT, Area.MARKS);

  /**
   * Creates the exception.
   *
   * @throws IllegalArgumentException if {@code code} is a policy or infrastructure code
   */
  public RuleViolationException(ErrorCode code, Map<String, Object> details) {
    this(code, details, null);
  }

  /** Creates the exception with an underlying cause (e.g. a constraint violation). */
  public RuleViolationException(ErrorCode code, Map<String, Object> details, Throwable cause) {
    super(requireArea(code, ALLOWED), details, cause);
  }
}
