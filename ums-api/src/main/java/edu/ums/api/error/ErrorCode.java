package edu.ums.api.error;

import java.util.Locale;

/**
 * Every error the backend can report across the facade boundary. Each code has a stable message key
 * {@code error.<area>.<lower_snake>} resolved from {@code i18n/messages_en.properties}.
 */
public enum ErrorCode {
  BAD_CREDENTIALS(Area.AUTH),
  ACCOUNT_LOCKED(Area.AUTH),
  SESSION_EXPIRED(Area.AUTH),
  ACCESS_DENIED(Area.AUTH),
  PASSWORD_POLICY(Area.AUTH),
  LAST_ADMIN(Area.AUTH),
  USERNAME_TAKEN(Area.AUTH),
  FEATURE_DISABLED(Area.POLICY),
  RATE_LIMITED(Area.POLICY),
  CONFIRMATION_REQUIRED(Area.POLICY),
  VALIDATION_FAILED(Area.DOMAIN),
  NOT_FOUND(Area.DOMAIN),
  STALE_DATA(Area.DOMAIN),
  INVALID_TRANSITION(Area.DOMAIN),
  GUARD_FAILED(Area.DOMAIN),
  SLOT_CLASH(Area.OFFER),
  INVALID_SCHEME(Area.OFFER),
  WINDOW_CLOSED(Area.REG),
  ALREADY_ENROLLED(Area.REG),
  PREREQ_NOT_MET(Area.REG),
  CREDIT_LIMIT(Area.REG),
  TIMETABLE_CLASH(Area.REG),
  SECTION_FULL(Area.REG),
  EDIT_WINDOW_CLOSED(Area.ATT),
  INVALID_SESSION_DATE(Area.ATT),
  MARK_OUT_OF_RANGE(Area.MARKS),
  NOT_EDITABLE(Area.MARKS),
  MARKS_INCOMPLETE(Area.MARKS),
  DB_UNAVAILABLE(Area.INFRA),
  BUSY_TRY_AGAIN(Area.INFRA),
  CONFLICT_TRY_AGAIN(Area.INFRA),
  TIMEOUT(Area.INFRA),
  CANCELLED(Area.INFRA),
  INTERNAL_ERROR(Area.INFRA);

  private final Area area;
  private final String messageKey;

  ErrorCode(Area area) {
    this.area = area;
    this.messageKey = "error." + area.key() + "." + name().toLowerCase(Locale.ROOT);
  }

  /** Returns the functional area this code belongs to. */
  public Area area() {
    return area;
  }

  /** Returns the i18n key, e.g. {@code error.reg.section_full}. */
  public String messageKey() {
    return messageKey;
  }

  /** Functional areas used as the middle segment of message keys. */
  public enum Area {
    AUTH,
    POLICY,
    DOMAIN,
    OFFER,
    REG,
    ATT,
    MARKS,
    INFRA;

    /** Returns the lower-case key segment for this area. */
    public String key() {
      return name().toLowerCase(Locale.ROOT);
    }
  }
}
