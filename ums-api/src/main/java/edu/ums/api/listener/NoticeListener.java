package edu.ums.api.listener;

/**
 * Receives notices published to the signed-in user's audience. The callback method and the notice
 * DTO arrive with the NOTICE feature (P4); until then this interface has no methods. Callbacks are
 * invoked on backend threads and must return quickly.
 */
public interface NoticeListener {}
