package edu.ums.proto.facade;

import edu.ums.api.DevApi;
import edu.ums.api.proto.ActivityRow;
import edu.ums.api.proto.HealthView;
import edu.ums.api.proto.QueryResult;
import edu.ums.proto.audit.service.ActivityRecorder;
import edu.ums.proto.dev.service.DevService;
import edu.ums.proto.shared.Actor;
import java.util.List;
import java.util.Objects;

/** Developer facade bound to one signed-in developer. */
public final class DevFacade implements DevApi {

  private final Actor dev;
  private final DevService service;
  private final ActivityRecorder activity;

  /** Creates the facade for {@code dev}. */
  public DevFacade(Actor dev, DevService service, ActivityRecorder activity) {
    this.dev = Objects.requireNonNull(dev, "dev");
    this.service = Objects.requireNonNull(service, "service");
    this.activity = Objects.requireNonNull(activity, "activity");
  }

  @Override
  public List<ActivityRow> activity(long afterId) {
    return activity.feed(afterId);
  }

  @Override
  public QueryResult runQuery(String sql) {
    return service.query(dev, sql);
  }

  @Override
  public HealthView health() {
    return service.health();
  }
}
