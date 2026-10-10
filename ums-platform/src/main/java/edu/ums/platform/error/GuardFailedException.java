package edu.ums.platform.error;

import edu.ums.api.error.ErrorCode;
import edu.ums.api.error.ErrorCode.Area;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * A transition exists but its guard rejected it. The code is {@code GUARD_FAILED} or a more
 * specific domain code chosen by the guard (e.g. {@code MARKS_INCOMPLETE}).
 */
public final class GuardFailedException extends DomainException {

  private static final long serialVersionUID = 1L;

  private static final Set<Area> ALLOWED =
      EnumSet.of(Area.AUTH, Area.DOMAIN, Area.OFFER, Area.REG, Area.ATT, Area.MARKS);

  /**
   * Creates the exception.
   *
   * @throws IllegalArgumentException if {@code code} is a policy or infrastructure code
   */
  public GuardFailedException(ErrorCode code, Map<String, Object> details) {
    this(code, details, null);
  }

  /** Creates the exception with an underlying cause (e.g. a constraint violation). */
  public GuardFailedException(ErrorCode code, Map<String, Object> details, Throwable cause) {
    super(requireArea(code, ALLOWED), details, cause);
  }
}
