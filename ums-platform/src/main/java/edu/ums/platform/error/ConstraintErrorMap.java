package edu.ums.platform.error;

import edu.ums.api.error.ErrorCode;
import java.util.Map;
import java.util.Optional;

/**
 * Database constraint name → {@link ErrorCode}. Constraint names are contracts: migrations must
 * create constraints with exactly these names (master prompt §6 and §10).
 */
// Complexity: time O(1) per lookup (immutable hash map); space O(c) — c registered constraints
public final class ConstraintErrorMap {

  private final Map<String, ErrorCode> codes;

  /** Creates a map from {@code codes} (copied). */
  public ConstraintErrorMap(Map<String, ErrorCode> codes) {
    this.codes = Map.copyOf(codes);
  }

  /** Returns the map with every constraint named in the master prompt. */
  public static ConstraintErrorMap standard() {
    return new ConstraintErrorMap(
        Map.of(
            "uq_enrolment_live", ErrorCode.ALREADY_ENROLLED,
            "uq_app_user_username", ErrorCode.USERNAME_TAKEN,
            "ex_room_slot", ErrorCode.SLOT_CLASH,
            "ex_faculty_slot", ErrorCode.SLOT_CLASH,
            "ck_section_capacity", ErrorCode.SECTION_FULL,
            "uq_one_active_term", ErrorCode.GUARD_FAILED,
            "uq_result_current", ErrorCode.STALE_DATA,
            "ck_mark_nonneg", ErrorCode.MARK_OUT_OF_RANGE));
  }

  /** Returns the code for {@code constraintName}, or empty if it is not registered. */
  public Optional<ErrorCode> lookup(String constraintName) {
    return constraintName == null
        ? Optional.empty()
        : Optional.ofNullable(codes.get(constraintName));
  }

  /** Returns all registrations, unmodifiable. */
  public Map<String, ErrorCode> asMap() {
    return codes;
  }
}
