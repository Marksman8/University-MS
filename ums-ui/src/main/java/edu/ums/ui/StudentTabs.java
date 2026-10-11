package edu.ums.ui;

import edu.ums.api.StudentApi;
import edu.ums.api.proto.AttendanceSummary;
import edu.ums.api.proto.ResultRow;
import edu.ums.api.proto.SectionView;
import java.util.List;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TableView;

/** Student tabs: Register, Timetable, Attendance, Results. */
final class StudentTabs {

  private final StudentApi api;
  private final Background bg;
  private final StatusBar status;
  private final TableView<SectionView> sections = Ui.table();
  private final TableView<SectionView> timetable = Ui.table();
  private final TableView<AttendanceSummary> attendance = Ui.table();
  private final TableView<ResultRow> results = Ui.table();
  private final Label gpa = new Label();

  StudentTabs(StudentApi api, Background bg, StatusBar status) {
    this.api = api;
    this.bg = bg;
    this.status = status;
    sectionColumns(sections, true);
    sectionColumns(timetable, false);
    attendance
        .getColumns()
        .setAll(
            List.of(
                Ui.col("Course", AttendanceSummary::courseCode, 90),
                Ui.col("Title", AttendanceSummary::courseTitle, 240),
                Ui.col("Present", AttendanceSummary::present, 80),
                Ui.col("Absent", AttendanceSummary::absent, 80),
                Ui.col("Excused", AttendanceSummary::excused, 80),
                Ui.col("Attendance %", a -> a.percent() == null ? "—" : a.percent(), 110)));
    results
        .getColumns()
        .setAll(
            List.of(
                Ui.col("Course", ResultRow::courseCode, 90),
                Ui.col("Title", ResultRow::courseTitle, 240),
                Ui.col("Credits", ResultRow::credits, 70),
                Ui.col("Total %", ResultRow::totalPct, 90),
                Ui.col("Grade", ResultRow::grade, 70),
                Ui.col("Grade point", ResultRow::gradePoint, 100)));
    gpa.getStyleClass().add("heading");
  }

  private static void sectionColumns(TableView<SectionView> table, boolean withSeats) {
    table.getColumns().add(Ui.col("Course", SectionView::courseCode, 80));
    table.getColumns().add(Ui.col("Title", SectionView::courseTitle, 220));
    table.getColumns().add(Ui.col("Section", SectionView::sectionCode, 70));
    table.getColumns().add(Ui.col("Credits", SectionView::credits, 60));
    table
        .getColumns()
        .add(Ui.col("Day / time", s -> Ui.slot(s.day(), s.startMin(), s.endMin()), 140));
    table.getColumns().add(Ui.col("Room", SectionView::room, 80));
    if (withSeats) {
      table.getColumns().add(Ui.col("Seats left", s -> s.capacity() - s.enrolled(), 80));
    }
    table.getColumns().add(Ui.col("Faculty", SectionView::facultyName, 160));
    if (withSeats) {
      table.getColumns().add(Ui.col("Registered", s -> s.mine() ? "✓" : "", 90));
    }
  }

  List<Tab> tabs() {
    return List.of(
        ShellView.tab(
            "Register",
            Ui.page(
                Ui.toolbar(
                    Ui.primary("Register", () -> act(true)),
                    Ui.button("Drop", () -> act(false)),
                    Ui.spacer(),
                    Ui.button("Refresh", this::loadSections)),
                sections),
            this::loadSections),
        ShellView.tab(
            "Timetable",
            Ui.page(Ui.toolbar(Ui.heading("My timetable"), Ui.spacer()), timetable),
            () -> bg.run(api::timetable, timetable.getItems()::setAll, status::error)),
        ShellView.tab(
            "Attendance",
            Ui.page(Ui.toolbar(Ui.heading("Attendance this term")), attendance),
            () -> bg.run(api::attendance, attendance.getItems()::setAll, status::error)),
        ShellView.tab("Results", Ui.page(gpa, results), this::loadResults));
  }

  private void loadSections() {
    bg.run(api::openSections, sections.getItems()::setAll, status::error);
  }

  private void loadResults() {
    bg.run(
        api::results,
        view -> {
          results.getItems().setAll(view.results());
          gpa.setText(
              view.termName() == null
                  ? "No published results yet"
                  : view.termName()
                      + "   ·   SGPA "
                      + view.sgpa()
                      + "   ·   CGPA "
                      + view.cgpa()
                      + "   ·   Credits earned "
                      + view.creditsEarned());
        },
        status::error);
  }

  private void act(boolean register) {
    SectionView selected = sections.getSelectionModel().getSelectedItem();
    if (selected == null) {
      status.info("Select a section first");
      return;
    }
    String label = selected.courseCode() + "-" + selected.sectionCode();
    bg.run(
        () -> {
          if (register) {
            api.register(selected.sectionId());
          } else {
            api.drop(selected.sectionId());
          }
          return Boolean.TRUE;
        },
        ok -> {
          status.info((register ? "Registered for " : "Dropped ") + label);
          loadSections();
        },
        status::error);
  }
}
