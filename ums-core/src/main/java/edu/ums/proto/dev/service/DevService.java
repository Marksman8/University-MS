package edu.ums.proto.dev.service;

import edu.ums.api.proto.HealthView;
import edu.ums.api.proto.QueryResult;
import edu.ums.proto.shared.Actor;

/** Prototype developer tools. */
public interface DevService {

  /** Guards and runs a console query; rejections and SQL errors come back as data. */
  QueryResult query(Actor dev, String sql);

  /** Current health. */
  HealthView health();
}
