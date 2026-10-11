package edu.ums.proto.org.repository;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Inserts demo reference data (prototype seed only). Runs in the caller's transaction. */
public interface SeedRepository {

  /** A section to insert. */
  record SectionSpec(
      long termId,
      long courseId,
      String code,
      int capacity,
      long facultyId,
      int day,
      int startMin,
      int endMin,
      String room) {}

  long department(String code, String name);

  long programme(String code, String name, long departmentId);

  long course(String code, String title, int credits, long departmentId);

  void prerequisite(long courseId, long prereqId);

  void student(long userId, long programmeId, String rollNo);

  void faculty(long userId, long departmentId);

  long term(String name, LocalDate start, LocalDate end, String state);

  long section(SectionSpec spec);

  void component(long sectionId, String name, BigDecimal max, BigDecimal weight);

  /** A COMPLETED enrolment with its result (history). */
  void completedResult(
      long studentId,
      long sectionId,
      long termId,
      long courseId,
      BigDecimal total,
      String grade,
      BigDecimal point);

  /** A term GPA row (history). */
  void termResult(long studentId, long termId, BigDecimal sgpa, BigDecimal cgpa, int credits);

  /** Sets a section's enrolled count (history sections). */
  void setEnrolled(long sectionId, int count);
}
