package edu.ums.ui;

import edu.ums.api.proto.ActivityRow;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.LongFunction;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.scene.control.CheckBox;
import javafx.scene.control.TableView;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

/**
 * Activity feed, newest first. Polls every 2 s while auto-refresh is on (prototype; P1 uses
 * LISTEN/NOTIFY). Keeps at most 1,000 rows; at most one call in flight.
 */
// Complexity: time O(k) per poll for k new rows (<= 200); space O(1,000) rows
final class ActivityFeed {

  private static final int MAX_ROWS = 1_000;
  private static final DateTimeFormatter TIME =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

  private final LongFunction<List<ActivityRow>> source;
  private final Background bg;
  private final StatusBar status;
  private final TableView<ActivityRow> table = Ui.table();
  private final VBox root;
  private long lastId;
  private boolean inFlight;

  ActivityFeed(
      LongFunction<List<ActivityRow>> source, Background bg, StatusBar status, boolean auto) {
    this.source = source;
    this.bg = bg;
    this.status = status;
    table
        .getColumns()
        .setAll(
            List.of(
                Ui.col("Time", r -> TIME.format(r.occurredAt()), 150),
                Ui.col("User", ActivityRow::username, 110),
                Ui.col("Role", ActivityRow::role, 90),
                Ui.col("Action", ActivityRow::action, 170),
                Ui.col("Entity", ActivityRow::entity, 130),
                Ui.col("Outcome", ActivityRow::outcome, 80),
                Ui.col("Details", ActivityRow::details, 260)));
    CheckBox autoRefresh = new CheckBox("Auto-refresh (2 s)");
    autoRefresh.setSelected(auto);
    Timeline poll = new Timeline(new KeyFrame(Duration.seconds(2), e -> refresh()));
    poll.setCycleCount(Timeline.INDEFINITE);
    autoRefresh
        .selectedProperty()
        .addListener(
            (obs, was, on) -> {
              if (on) {
                poll.play();
              } else {
                poll.stop();
              }
            });
    if (auto) {
      poll.play();
    }
    root =
        Ui.page(
            Ui.toolbar(
                Ui.heading("Activity"),
                Ui.spacer(),
                autoRefresh,
                Ui.button("Refresh", this::refresh)),
            table);
  }

  VBox root() {
    return root;
  }

  void refresh() {
    if (inFlight) {
      return;
    }
    inFlight = true;
    long after = lastId;
    bg.run(
        () -> source.apply(after),
        rows -> {
          inFlight = false;
          for (ActivityRow row : rows) {
            table.getItems().add(0, row);
            lastId = Math.max(lastId, row.id());
          }
          if (table.getItems().size() > MAX_ROWS) {
            table.getItems().remove(MAX_ROWS, table.getItems().size());
          }
        },
        err -> {
          inFlight = false;
          status.error(err);
        });
  }
}
