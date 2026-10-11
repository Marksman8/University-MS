package edu.ums.ui;

import edu.ums.api.DevApi;
import edu.ums.api.proto.HealthView;
import edu.ums.api.proto.QueryResult;
import java.util.List;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.Tab;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;

/** Developer tabs: live activity feed, SQL console, health. */
final class DevTabs {

  private final DevApi api;
  private final Background bg;
  private final StatusBar status;
  private final ActivityFeed feed;
  private final TextArea sql =
      new TextArea("SELECT username, role, full_name FROM app_user ORDER BY id");
  private final TableView<List<String>> results = Ui.table();
  private final Label queryInfo = new Label();
  private final Label pill = new Label("?");
  private final ListView<String> probes = new ListView<>();

  DevTabs(DevApi api, Background bg, StatusBar status) {
    this.api = api;
    this.bg = bg;
    this.status = status;
    this.feed = new ActivityFeed(api::activity, bg, status, true);
    sql.setPrefRowCount(6);
    sql.getStyleClass().add("sql");
    pill.getStyleClass().add("health-pill");
  }

  List<Tab> tabs() {
    return List.of(
        ShellView.tab("Activity feed", feed.root(), feed::refresh),
        ShellView.tab(
            "SQL console",
            Ui.page(
                new Label("One read-only SELECT or WITH statement · max 200 rows · 5 s timeout"),
                sql,
                Ui.toolbar(Ui.primary("Run", this::run), queryInfo),
                results),
            () -> {}),
        ShellView.tab(
            "Health", Ui.page(Ui.toolbar(Ui.heading("Status"), pill), probes), this::health));
  }

  private void run() {
    String text = sql.getText();
    bg.run(() -> api.runQuery(text), this::show, status::error);
  }

  private void show(QueryResult r) {
    results.getColumns().clear();
    results.getItems().clear();
    queryInfo.getStyleClass().remove("error");
    if (r.error() != null) {
      queryInfo.getStyleClass().add("error");
      queryInfo.setText("Error " + r.sqlState() + ": " + r.error());
      return;
    }
    for (int i = 0; i < r.columns().size(); i++) {
      int index = i;
      TableColumn<List<String>, String> column = new TableColumn<>(r.columns().get(i));
      column.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().get(index)));
      column.setPrefWidth(140);
      results.getColumns().add(column);
    }
    results.getItems().setAll(r.rows());
    queryInfo.setText(
        r.rows().size()
            + " row(s) in "
            + r.elapsedMillis()
            + " ms"
            + (r.truncated() ? " — truncated to 200 rows" : ""));
  }

  private void health() {
    bg.run(api::health, this::showHealth, status::error);
  }

  private void showHealth(HealthView h) {
    pill.setText(h.status());
    pill.getStyleClass().removeAll("up", "down");
    pill.getStyleClass().add("UP".equals(h.status()) ? "up" : "down");
    probes.getItems().setAll(h.probes());
  }
}
