package edu.ums.ui;

import edu.ums.api.Session;
import java.util.List;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;

/** Top bar (app, user, role, logout), one tab set per role, status bar. */
final class ShellView {

  private final BorderPane root = new BorderPane();

  ShellView(Session session, Background background, Runnable onLogout) {
    StatusBar status = new StatusBar(background);
    Label app = new Label("UMS");
    app.getStyleClass().add("brand");
    Label who = new Label(session.user().displayName() + " · " + session.user().username());
    Label role = new Label(session.role().name());
    role.getStyleClass().add("role-pill");
    HBox top =
        new HBox(
            12,
            app,
            Ui.spacer(),
            who,
            role,
            Ui.button(
                "Logout",
                () ->
                    background.run(
                        () -> {
                          session.close();
                          return Boolean.TRUE;
                        },
                        ok -> onLogout.run(),
                        err -> onLogout.run())));
    top.getStyleClass().add("top-bar");
    TabPane tabs = new TabPane();
    tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
    tabs.getTabs().setAll(tabsFor(session, background, status));
    tabs.getSelectionModel()
        .selectedItemProperty()
        .addListener(
            (obs, old, now) -> {
              if (now != null && now.getUserData() instanceof Runnable refresh) {
                refresh.run();
              }
            });
    root.setTop(top);
    root.setCenter(tabs);
    root.setBottom(status.root());
    Tab first = tabs.getTabs().get(0);
    if (first.getUserData() instanceof Runnable refresh) {
      refresh.run();
    }
  }

  private static List<Tab> tabsFor(Session session, Background bg, StatusBar status) {
    return switch (session.role()) {
      case STUDENT -> new StudentTabs(session.student(), bg, status).tabs();
      case FACULTY -> new FacultyTabs(session.faculty(), bg, status).tabs();
      case ADMIN -> new AdminTabs(session.admin(), bg, status).tabs();
      case DEV -> new DevTabs(session.dev(), bg, status).tabs();
    };
  }

  /** A tab whose {@code refresh} runs whenever it is selected. */
  static Tab tab(String title, javafx.scene.Node content, Runnable refresh) {
    Tab tab = new Tab(title, content);
    tab.setUserData(refresh);
    return tab;
  }

  Parent root() {
    return root;
  }
}
