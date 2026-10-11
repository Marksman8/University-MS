package edu.ums.ui;

import edu.ums.api.error.UmsApiException;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.TreeMap;
import java.util.function.Function;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/** Small pure helpers shared by the views. */
final class Ui {

  private static final ResourceBundle MESSAGES = ResourceBundle.getBundle("i18n/messages_en");
  private static final String[] DAYS = {"", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"};

  private Ui() {}

  /** The English message for an API error plus its safe arguments, never a stack trace. */
  static String message(UmsApiException e) {
    String text;
    try {
      text = MESSAGES.getString(e.messageKey());
    } catch (MissingResourceException missing) {
      text = e.code().name();
    }
    Map<String, Object> args = new TreeMap<>(e.args());
    args.remove("incidentId");
    StringBuilder out = new StringBuilder(text);
    if (!args.isEmpty()) {
      out.append("  (");
      args.forEach((k, v) -> out.append(k).append(": ").append(v).append("; "));
      out.setLength(out.length() - 2);
      out.append(')');
    }
    e.incidentId().ifPresent(id -> out.append("  [incident ").append(id).append(']'));
    return out.toString();
  }

  static <T> TableColumn<T, String> col(String title, Function<T, Object> value, double width) {
    TableColumn<T, String> column = new TableColumn<>(title);
    column.setCellValueFactory(
        cell -> {
          Object v = value.apply(cell.getValue());
          return new ReadOnlyStringWrapper(v == null ? "" : String.valueOf(v));
        });
    column.setPrefWidth(width);
    return column;
  }

  static <T> TableView<T> table() {
    TableView<T> table = new TableView<>();
    table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
    table.setPlaceholder(new Label("Nothing to show yet"));
    VBox.setVgrow(table, Priority.ALWAYS);
    return table;
  }

  static String day(int day) {
    return day >= 1 && day < DAYS.length ? DAYS[day] : "?";
  }

  static String time(int minutes) {
    return String.format("%02d:%02d", minutes / 60, minutes % 60);
  }

  static String slot(int day, int start, int end) {
    return day(day) + " " + time(start) + "–" + time(end);
  }

  static Button button(String text, Runnable action) {
    Button b = new Button(text);
    b.setOnAction(e -> action.run());
    return b;
  }

  static Button primary(String text, Runnable action) {
    Button b = button(text, action);
    b.getStyleClass().add("primary");
    return b;
  }

  static HBox toolbar(javafx.scene.Node... nodes) {
    HBox bar = new HBox(8, nodes);
    bar.getStyleClass().add("toolbar");
    return bar;
  }

  static Region spacer() {
    Region r = new Region();
    HBox.setHgrow(r, Priority.ALWAYS);
    return r;
  }

  static VBox page(javafx.scene.Node... nodes) {
    VBox box = new VBox(8, nodes);
    box.setPadding(new Insets(12));
    return box;
  }

  static Label heading(String text) {
    Label l = new Label(text);
    l.getStyleClass().add("heading");
    return l;
  }
}
