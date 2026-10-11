package edu.ums.api;

import edu.ums.api.annotation.Writes;
import edu.ums.api.proto.ActivityRow;
import edu.ums.api.proto.HealthView;
import edu.ums.api.proto.QueryResult;
import java.util.List;

/**
 * Developer facade: query console, pipeline view, diagnostics and the live activity feed. The
 * methods below are the prototype slice.
 */
public interface DevApi {

  /** Activity entries newer than {@code afterId} (≤ 0: the latest 200), oldest first. */
  List<ActivityRow> activity(long afterId);

  /**
   * Runs one read-only SELECT/WITH statement on the console pool; SQL errors come back as data.
   * Marked {@code @Writes} because the call itself is recorded in the activity log.
   */
  @Writes
  QueryResult runQuery(String sql);

  /** Current health. */
  HealthView health();
}
