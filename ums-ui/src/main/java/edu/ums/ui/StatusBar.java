package edu.ums.ui;

import edu.ums.api.error.UmsApiException;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.HBox;

/** Bottom status line: last message (info or error) and a spinner while calls run. */
final class StatusBar {

  private final HBox root;
  private final Label text = new Label("Ready");

  StatusBar(Background background) {
    ProgressIndicator spinner = new ProgressIndicator();
    spinner.setPrefSize(18, 18);
    spinner.visibleProperty().bind(background.busy());
    root = new HBox(8, spinner, text);
    root.getStyleClass().add("status-bar");
  }

  HBox root() {
    return root;
  }

  void info(String message) {
    text.getStyleClass().remove("error");
    text.setText(message);
  }

  void error(UmsApiException e) {
    if (!text.getStyleClass().contains("error")) {
      text.getStyleClass().add("error");
    }
    text.setText(Ui.message(e));
  }
}
