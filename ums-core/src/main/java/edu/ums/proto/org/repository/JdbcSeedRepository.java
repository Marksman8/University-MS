package edu.ums.proto.org.repository;

import edu.ums.platform.db.BaseRepository;
import edu.ums.platform.db.CursorCodec;
import edu.ums.platform.db.TransactionRunner;
import edu.ums.platform.error.SqlErrorMapper;
import java.math.BigDecimal;
import java.time.LocalDate;

/** JDBC {@link SeedRepository}. */
// Complexity: time O(log n) per insert; space O(1)
public final class JdbcSeedRepository extends BaseRepository implements SeedRepository {

  /** Creates the repository. */
  public JdbcSeedRepository(
      TransactionRunner transactions, SqlErrorMapper errors, CursorCodec cursors) {
    super(transactions, errors, cursors);
  }

  @Override
  public long department(String code, String name) {
    return queryOne(
        "INSERT INTO department (code, name) VALUES (?, ?) RETURNING id",
        ps -> {
          ps.setString(1, code);
          ps.setString(2, name);
        },
        rs -> rs.getLong(1));
  }

  @Override
  public long programme(String code, String name, long departmentId) {
    return queryOne(
        "INSERT INTO programme (code, name, department_id) VALUES (?, ?, ?) RETURNING id",
        ps -> {
          ps.setString(1, code);
          ps.setString(2, name);
          ps.setLong(3, departmentId);
        },
        rs -> rs.getLong(1));
  }

  @Override
  public long course(String code, String title, int credits, long departmentId) {
    return queryOne(
        "INSERT INTO course (code, title, credits, department_id) VALUES (?, ?, ?, ?)"
            + " RETURNING id",
        ps -> {
          ps.setString(1, code);
          ps.setString(2, title);
          ps.setInt(3, credits);
          ps.setLong(4, departmentId);
        },
        rs -> rs.getLong(1));
  }

  @Override
  public void prerequisite(long courseId, long prereqId) {
    update(
        "INSERT INTO course_prereq (course_id, prereq_id) VALUES (?, ?)",
        ps -> {
          ps.setLong(1, courseId);
          ps.setLong(2, prereqId);
        });
  }

  @Override
  public void student(long userId, long programmeId, String rollNo) {
    update(
        "INSERT INTO student (user_id, programme_id, roll_no) VALUES (?, ?, ?)",
        ps -> {
          ps.setLong(1, userId);
          ps.setLong(2, programmeId);
          ps.setString(3, rollNo);
        });
  }

  @Override
  public void faculty(long userId, long departmentId) {
    update(
        "INSERT INTO faculty (user_id, department_id) VALUES (?, ?)",
        ps -> {
          ps.setLong(1, userId);
          ps.setLong(2, departmentId);
        });
  }

  @Override
  public long term(String name, LocalDate start, LocalDate end, String state) {
    return queryOne(
        "INSERT INTO term (name, start_date, end_date, reg_open, reg_close, state)"
            + " VALUES (?, ?, ?, ?, ?, ?) RETURNING id",
        ps -> {
          ps.setString(1, name);
          ps.setObject(2, start);
          ps.setObject(3, end);
          ps.setObject(4, start.minusWeeks(2));
          ps.setObject(5, end.minusWeeks(2));
          ps.setString(6, state);
        },
        rs -> rs.getLong(1));
  }

  @Override
  public long section(SectionSpec s) {
    return queryOne(
        "INSERT INTO section (term_id, course_id, code, capacity, faculty_id, day, start_min,"
            + " end_min, room) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id",
        ps -> {
          ps.setLong(1, s.termId());
          ps.setLong(2, s.courseId());
          ps.setString(3, s.code());
          ps.setInt(4, s.capacity());
          ps.setLong(5, s.facultyId());
          ps.setInt(6, s.day());
          ps.setInt(7, s.startMin());
          ps.setInt(8, s.endMin());
          ps.setString(9, s.room());
        },
        rs -> rs.getLong(1));
  }

  @Override
  public void component(long sectionId, String name, BigDecimal max, BigDecimal weight) {
    update(
        "INSERT INTO assessment_component (section_id, name, max_marks, weight_pct)"
            + " VALUES (?, ?, ?, ?)",
        ps -> {
          ps.setLong(1, sectionId);
          ps.setString(2, name);
          ps.setBigDecimal(3, max);
          ps.setBigDecimal(4, weight);
        });
  }

  @Override
  public void completedResult(
      long studentId,
      long sectionId,
      long termId,
      long courseId,
      BigDecimal total,
      String grade,
      BigDecimal point) {
    update(
        "WITH e AS (INSERT INTO enrolment (student_id, section_id, term_id, course_id, status)"
            + " VALUES (?, ?, ?, ?, 'COMPLETED') RETURNING id)"
            + " INSERT INTO result (enrolment_id, total_pct, grade, grade_point)"
            + " SELECT id, ?, ?, ? FROM e",
        ps -> {
          ps.setLong(1, studentId);
          ps.setLong(2, sectionId);
          ps.setLong(3, termId);
          ps.setLong(4, courseId);
          ps.setBigDecimal(5, total);
          ps.setString(6, grade);
          ps.setBigDecimal(7, point);
        });
  }

  @Override
  public void termResult(
      long studentId, long termId, BigDecimal sgpa, BigDecimal cgpa, int credits) {
    update(
        "INSERT INTO term_result (student_id, term_id, sgpa, cgpa, credits_earned)"
            + " VALUES (?, ?, ?, ?, ?)",
        ps -> {
          ps.setLong(1, studentId);
          ps.setLong(2, termId);
          ps.setBigDecimal(3, sgpa);
          ps.setBigDecimal(4, cgpa);
          ps.setInt(5, credits);
        });
  }

  @Override
  public void setEnrolled(long sectionId, int count) {
    update(
        "UPDATE section SET enrolled_count = ? WHERE id = ?",
        ps -> {
          ps.setInt(1, count);
          ps.setLong(2, sectionId);
        });
  }
}
