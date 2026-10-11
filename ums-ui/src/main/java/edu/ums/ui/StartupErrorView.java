package edu.ums.ui;

import javafx.scene.Parent;
import javafx.scene.control.Label;

/** Shown when the backend could not start (configuration or database unavailable). */
final class StartupErrorView {

  private final Parent root;

  StartupErrorView(String reason) {
    Label detail =
        new Label(
            "UMS could not start: "
                + reason
                + "\n\nCheck UMS_ENV, UMS_DB_URL and the role passwords (see README_DEMO.md).");
    detail.setWrapText(true);
    root = Ui.page(Ui.heading("Startup failed"), detail);
  }

  Parent root() {
    return root;
  }
}
