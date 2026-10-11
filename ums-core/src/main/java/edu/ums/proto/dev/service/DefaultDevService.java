package edu.ums.proto.dev.service;

import edu.ums.api.proto.HealthView;
import edu.ums.api.proto.QueryResult;
import edu.ums.platform.observe.HealthReport;
import edu.ums.platform.observe.HealthService;
import edu.ums.proto.audit.service.ActivityRecorder;
import edu.ums.proto.dev.domain.ConsoleGuard;
import edu.ums.proto.dev.repository.ConsoleRepository;
import edu.ums.proto.shared.Actor;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Console queries through {@link ConsoleGuard}; health from the P0 {@link HealthService}. */
// Complexity: time O(n) guard + O(r × c) query (bounded by the console repository); space same
public final class DefaultDevService implements DevService {

  private final ConsoleRepository console;
  private final HealthService health;
  private final ActivityRecorder activity;

  /** Creates the service. */
  public DefaultDevService(
      ConsoleRepository console, HealthService health, ActivityRecorder activity) {
    this.console = Objects.requireNonNull(console, "console");
    this.health = Objects.requireNonNull(health, "health");
    this.activity = Objects.requireNonNull(activity, "activity");
  }

  @Override
  public QueryResult query(Actor dev, String sql) {
    ConsoleGuard.Verdict verdict = ConsoleGuard.check(sql);
    QueryResult result =
        switch (verdict) {
          case ConsoleGuard.Allowed allowed -> console.run(allowed.sql());
          case ConsoleGuard.Rejected rejected ->
              new QueryResult(List.of(), List.of(), false, 0, "GUARD", rejected.reason());
        };
    activity.success(
        dev,
        "CONSOLE_QUERY",
        null,
        null,
        Map.of(
            "chars", sql == null ? 0 : sql.length(),
            "rows", result.rows().size(),
            "error", result.sqlState() == null ? "-" : result.sqlState()));
    return result;
  }

  @Override
  public HealthView health() {
    HealthReport report = health.check();
    return new HealthView(
        report.status().name(),
        report.probes().stream()
            .map(p -> p.name() + ": " + p.status() + (p.detail() == null ? "" : " — " + p.detail()))
            .toList());
  }
}
