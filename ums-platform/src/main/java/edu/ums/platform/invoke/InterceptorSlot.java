package edu.ums.platform.invoke;

/**
 * Fixed positions in the facade interceptor chain, outermost first (master prompt §4). A chain
 * holds at most one interceptor per slot and always runs them in this order.
 */
public enum InterceptorSlot {
  ERROR_MAPPING,
  SESSION,
  AUTHORISATION,
  FEATURE_FLAG,
  RATE_LIMIT,
  TRACING,
  AUDIT,
  TRANSACTION
}
