package edu.ums.ui;

import edu.ums.api.FacultyApi;
import edu.ums.api.error.ErrorCode;
import edu.ums.api.error.UmsApiException;
import edu.ums.api.proto.AttendanceMark;
import edu.ums.api.proto.AttendanceStatus;
import edu.ums.api.proto.ComponentView;
import edu.ums.api.proto.MarkCell;
import edu.ums.api.proto.MarkEntry;
import edu.ums.api.proto.MarkRow;
import edu.ums.api.proto.MarkSheetView;
import edu.ums.api.proto.RosterEntry;
import edu.ums.api.proto.SectionView;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

/** Faculty tabs: My sections, Attendance, Marks. */
final class FacultyTabs {

  private final FacultyApi api;
  private final Background bg;
  private final StatusBar status;
  private final TableView<SectionView> mine = Ui.table();
  private final ComboBox<SectionView> attSection = sectionBox();
  private final ComboBox<SectionView> marksSection = sectionBox();
  private final DatePicker date = new DatePicker(LocalDate.now());
  private final GridPane attGrid = grid();
  private final GridPane marksGrid = grid();
  private final Label sheetState = new Label();
  private final List<AttendanceRow> attRows = new ArrayList<>();
  private final List<MarkInput> markInputs = new ArrayList<>();
  private MarkSheetView currentSheet;

  private record AttendanceRow(long enrolmentId, ChoiceBox<AttendanceStatus> choice) {}

  private record MarkInput(long enrolmentId, long componentId, TextField value, CheckBox absent) {}

  FacultyTabs(FacultyApi api, Background bg, StatusBar status) {
    this.api = api;
    this.bg = bg;
    this.status = status;
    mine.getColumns()
        .setAll(
            List.of(
                Ui.col("Course", SectionView::courseCode, 80),
                Ui.col("Title", SectionView::courseTitle, 220),
                Ui.col("Section", SectionView::sectionCode, 70),
                Ui.col("Day / time", s -> Ui.slot(s.day(), s.startMin(), s.endMin()), 140),
                Ui.col("Room", SectionView::room, 80),
                Ui.col("Enrolled", SectionView::enrolled, 80),
                Ui.col("Capacity", SectionView::capacity, 80),
                Ui.col("Mark sheet", s -> s.sheetState() == null ? "—" : s.sheetState(), 110)));
    sheetState.getStyleClass().add("role-pill");
  }

  List<Tab> tabs() {
    return List.of(
        ShellView.tab("My sections", Ui.page(mine), this::loadSections),
        ShellView.tab(
            "Attendance",
            Ui.page(
                Ui.toolbar(
                    new Label("Section"),
                    attSection,
                    new Label("Date"),
                    date,
                    Ui.button("Load roster", this::loadRoster),
                    Ui.spacer(),
                    Ui.primary("Save attendance", this::saveAttendance)),
                scroll(attGrid)),
            this::loadSections),
        ShellView.tab(
            "Marks",
            Ui.page(
                Ui.toolbar(
                    new Label("Section"),
                    marksSection,
                    Ui.button("Load sheet", this::loadSheet),
                    sheetState,
                    Ui.spacer(),
                    Ui.button("Save marks", this::saveMarks),
                    Ui.primary("Submit sheet", this::submitSheet)),
                scroll(marksGrid)),
            this::loadSections));
  }

  private void loadSections() {
    bg.run(
        api::mySections,
        list -> {
          mine.getItems().setAll(list);
          keepSelection(attSection, list);
          keepSelection(marksSection, list);
        },
        status::error);
  }

  private void loadRoster() {
    SectionView s = attSection.getValue();
    if (s == null) {
      status.info("Choose a section");
      return;
    }
    bg.run(() -> api.roster(s.sectionId()), this::showRoster, status::error);
  }

  private void showRoster(List<RosterEntry> roster) {
    attGrid.getChildren().clear();
    attRows.clear();
    attGrid.addRow(0, bold("Roll no"), bold("Student"), bold("Status"));
    int r = 1;
    for (RosterEntry e : roster) {
      ChoiceBox<AttendanceStatus> choice = new ChoiceBox<>();
      choice.getItems().setAll(AttendanceStatus.values());
      choice.setValue(AttendanceStatus.PRESENT);
      attRows.add(new AttendanceRow(e.enrolmentId(), choice));
      attGrid.addRow(r++, new Label(e.rollNo()), new Label(e.studentName()), choice);
    }
    status.info(roster.size() + " students");
  }

  private void saveAttendance() {
    SectionView s = attSection.getValue();
    if (s == null || attRows.isEmpty()) {
      status.info("Load a roster first");
      return;
    }
    List<AttendanceMark> marks =
        attRows.stream()
            .map(r -> new AttendanceMark(r.enrolmentId(), r.choice().getValue()))
            .toList();
    LocalDate day = date.getValue();
    bg.run(
        () -> {
          api.recordAttendance(s.sectionId(), day, marks);
          return Boolean.TRUE;
        },
        ok -> status.info("Attendance saved for " + day),
        status::error);
  }

  private void loadSheet() {
    SectionView s = marksSection.getValue();
    if (s == null) {
      status.info("Choose a section");
      return;
    }
    bg.run(() -> api.markSheet(s.sectionId()), this::showSheet, status::error);
  }

  private void showSheet(MarkSheetView sheet) {
    currentSheet = sheet;
    sheetState.setText(sheet.state());
    boolean editable = "DRAFT".equals(sheet.state());
    marksGrid.getChildren().clear();
    markInputs.clear();
    marksGrid.add(bold("Roll no"), 0, 0);
    marksGrid.add(bold("Student"), 1, 0);
    int c = 2;
    for (ComponentView comp : sheet.components()) {
      marksGrid.add(
          bold(
              comp.name()
                  + " (/"
                  + comp.maxMarks().stripTrailingZeros().toPlainString()
                  + ", "
                  + comp.weightPct().stripTrailingZeros().toPlainString()
                  + "%)"),
          c,
          0,
          2,
          1);
      c += 2;
    }
    int r = 1;
    for (MarkRow row : sheet.rows()) {
      marksGrid.add(new Label(row.rollNo()), 0, r);
      marksGrid.add(new Label(row.studentName()), 1, r);
      int col = 2;
      for (MarkCell cell : row.cells()) {
        TextField value = new TextField(cell.value() == null ? "" : cell.value().toPlainString());
        value.setPrefColumnCount(5);
        CheckBox absent = new CheckBox("Absent");
        absent.setSelected(cell.absent());
        value.disableProperty().bind(absent.selectedProperty());
        value.setEditable(editable);
        absent.setDisable(!editable);
        markInputs.add(new MarkInput(row.enrolmentId(), cell.componentId(), value, absent));
        marksGrid.add(value, col, r);
        marksGrid.add(absent, col + 1, r);
        col += 2;
      }
      r++;
    }
    status.info(
        sheet.courseCode()
            + "-"
            + sheet.sectionCode()
            + ": "
            + sheet.rows().size()
            + " students, sheet "
            + sheet.state());
  }

  private void saveMarks() {
    if (currentSheet == null) {
      status.info("Load a sheet first");
      return;
    }
    List<MarkEntry> entries = new ArrayList<>();
    for (MarkInput in : markInputs) {
      String text = in.value().getText().strip();
      if (!in.absent().isSelected() && text.isEmpty()) {
        continue;
      }
      BigDecimal value;
      try {
        value = in.absent().isSelected() ? null : new BigDecimal(text);
      } catch (NumberFormatException bad) {
        status.error(
            new UmsApiException(
                ErrorCode.VALIDATION_FAILED,
                ErrorCode.VALIDATION_FAILED.messageKey(),
                Map.of("value", text)));
        return;
      }
      entries.add(
          new MarkEntry(in.enrolmentId(), in.componentId(), value, in.absent().isSelected()));
    }
    long sectionId = currentSheet.sectionId();
    bg.run(
        () -> {
          api.saveMarks(sectionId, entries);
          return api.markSheet(sectionId);
        },
        sheet -> {
          showSheet(sheet);
          status.info("Saved " + entries.size() + " marks");
        },
        status::error);
  }

  private void submitSheet() {
    if (currentSheet == null) {
      status.info("Load a sheet first");
      return;
    }
    long sectionId = currentSheet.sectionId();
    bg.run(
        () -> {
          api.submitSheet(sectionId);
          return api.markSheet(sectionId);
        },
        sheet -> {
          showSheet(sheet);
          status.info("Sheet submitted for approval");
          loadSections();
        },
        status::error);
  }

  private static ComboBox<SectionView> sectionBox() {
    ComboBox<SectionView> box = new ComboBox<>();
    box.setConverter(
        new StringConverter<>() {
          @Override
          public String toString(SectionView s) {
            return s == null ? "" : s.courseCode() + "-" + s.sectionCode() + " " + s.courseTitle();
          }

          @Override
          public SectionView fromString(String text) {
            return null;
          }
        });
    box.setPrefWidth(280);
    return box;
  }

  private static void keepSelection(ComboBox<SectionView> box, List<SectionView> list) {
    long selected = box.getValue() == null ? -1 : box.getValue().sectionId();
    box.getItems().setAll(list);
    list.stream()
        .filter(s -> s.sectionId() == selected)
        .findFirst()
        .ifPresentOrElse(
            box::setValue,
            () -> {
              if (!list.isEmpty()) {
                box.setValue(list.get(0));
              }
            });
  }

  private static GridPane grid() {
    GridPane g = new GridPane();
    g.setHgap(10);
    g.setVgap(6);
    g.getStyleClass().add("grid");
    return g;
  }

  private static ScrollPane scroll(GridPane grid) {
    ScrollPane pane = new ScrollPane(new VBox(grid));
    pane.setFitToWidth(true);
    VBox.setVgrow(pane, Priority.ALWAYS);
    return pane;
  }

  private static Label bold(String text) {
    Label l = new Label(text);
    l.getStyleClass().add("column-head");
    return l;
  }
}
