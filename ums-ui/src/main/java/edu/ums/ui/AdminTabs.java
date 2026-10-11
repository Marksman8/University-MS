package edu.ums.ui;

import edu.ums.api.AdminApi;
import edu.ums.api.Role;
import edu.ums.api.proto.MarkSheetSummary;
import edu.ums.api.proto.TermView;
import edu.ums.api.proto.UserRow;
import java.util.List;
import java.util.function.Supplier;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;

/** Admin tabs: Term, Mark sheets, Users, Activity. */
final class AdminTabs {

  private final AdminApi api;
  private final Background bg;
  private final StatusBar status;
  private final Label termLabel = new Label();
  private final Label termState = new Label();
  private final Button teach;
  private final Button grade;
  private final Button publish;
  private final TableView<MarkSheetSummary> sheets = Ui.table();
  private final TableView<UserRow> users = Ui.table();
  private final ActivityFeed feed;

  AdminTabs(AdminApi api, Background bg, StatusBar status) {
    this.api = api;
    this.bg = bg;
    this.status = status;
    this.feed = new ActivityFeed(api::activity, bg, status, false);
    teach = Ui.primary("Start teaching", () -> transition(api::startTeaching));
    grade = Ui.primary("Start grading", () -> transition(api::startGrading));
    publish = Ui.primary("Publish results", () -> transition(api::publishResults));
    termLabel.getStyleClass().add("heading");
    termState.getStyleClass().add("role-pill");
    sheets
        .getColumns()
        .setAll(
            List.of(
                Ui.col("Course", MarkSheetSummary::courseCode, 90),
                Ui.col("Section", MarkSheetSummary::sectionCode, 80),
                Ui.col("Faculty", MarkSheetSummary::facultyName, 200),
                Ui.col("State", MarkSheetSummary::state, 120)));
    users
        .getColumns()
        .setAll(
            List.of(
                Ui.col("Username", UserRow::username, 140),
                Ui.col("Name", UserRow::fullName, 220),
                Ui.col("Role", UserRow::role, 100),
                Ui.col("Active", u -> u.active() ? "yes" : "no", 70)));
  }

  List<Tab> tabs() {
    return List.of(
        ShellView.tab(
            "Term",
            Ui.page(
                Ui.toolbar(termLabel, termState),
                Ui.toolbar(teach, grade, publish),
                new Label(
                    "Start teaching creates a DRAFT mark sheet per section. Publish requires every"
                        + " sheet to be APPROVED.")),
            this::loadTerm),
        ShellView.tab(
            "Mark sheets",
            Ui.page(
                Ui.toolbar(
                    Ui.primary("Approve", () -> sheetAction(true)),
                    Ui.button("Return to faculty", () -> sheetAction(false)),
                    Ui.spacer(),
                    Ui.button("Refresh", this::loadSheets)),
                sheets),
            this::loadSheets),
        ShellView.tab("Users", Ui.page(createForm(), users), this::loadUsers),
        ShellView.tab("Activity", feed.root(), feed::refresh));
  }

  private javafx.scene.Node createForm() {
    ChoiceBox<Role> role = new ChoiceBox<>();
    role.getItems().setAll(Role.values());
    role.setValue(Role.STUDENT);
    TextField username = new TextField();
    username.setPromptText("username");
    TextField name = new TextField();
    name.setPromptText("Full name");
    Button create =
        Ui.primary(
            "New user",
            () -> {
              Role r = role.getValue();
              String u = username.getText();
              String n = name.getText();
              bg.run(
                  () -> api.createUser(r, u, n),
                  temp -> {
                    username.clear();
                    name.clear();
                    showOnce(u, temp);
                    loadUsers();
                  },
                  status::error);
            });
    return Ui.toolbar(new Label("Role"), role, username, name, create);
  }

  private void showOnce(String username, String temporary) {
    Alert alert = new Alert(Alert.AlertType.INFORMATION);
    alert.setTitle("User created");
    alert.setHeaderText("Temporary password for " + username + " (shown once)");
    TextArea text = new TextArea(temporary);
    text.setEditable(false);
    text.setPrefRowCount(1);
    alert.getDialogPane().setContent(text);
    alert.showAndWait();
    status.info("Created " + username);
  }

  private void loadTerm() {
    bg.run(api::currentTerm, this::showTerm, status::error);
  }

  private void showTerm(TermView term) {
    termLabel.setText(term.name());
    termState.setText(term.state());
    teach.setDisable(!"REGISTRATION_OPEN".equals(term.state()));
    grade.setDisable(!"IN_PROGRESS".equals(term.state()));
    publish.setDisable(!"GRADING".equals(term.state()));
  }

  private void transition(Supplier<TermView> call) {
    bg.run(
        call,
        term -> {
          showTerm(term);
          status.info("Term is now " + term.state());
        },
        status::error);
  }

  private void loadSheets() {
    bg.run(api::markSheets, sheets.getItems()::setAll, status::error);
  }

  private void sheetAction(boolean approve) {
    MarkSheetSummary s = sheets.getSelectionModel().getSelectedItem();
    if (s == null) {
      status.info("Select a mark sheet first");
      return;
    }
    bg.run(
        () -> {
          if (approve) {
            api.approveSheet(s.sectionId());
          } else {
            api.returnSheet(s.sectionId());
          }
          return api.markSheets();
        },
        list -> {
          sheets.getItems().setAll(list);
          status.info(
              s.courseCode() + "-" + s.sectionCode() + (approve ? " approved" : " returned"));
        },
        status::error);
  }

  private void loadUsers() {
    bg.run(api::users, users.getItems()::setAll, status::error);
  }
}
