package edu.ums.proto.att.repository;

import edu.ums.api.proto.AttendanceMark;
import edu.ums.api.proto.RosterEntry;
import java.time.LocalDate;
import java.util.List;

/** Prototype persistence of class sessions and attendance. Runs in the caller's transaction. */
public interface AttendanceRepository {

  /** Raw counts per course for one student. */
  record Counts(String courseCode, String courseTitle, int present, int absent, int excused) {}

  /** Whether the section exists and is taught by the faculty member. */
  boolean ownsSection(long facultyId, long sectionId);

  /** ENROLLED students of the section ordered by roll number. */
  List<RosterEntry> roster(long sectionId);

  /** Inserts (or touches) the session for the date and returns its id. */
  long upsertSession(long sectionId, LocalDate date);

  /** Batch upsert; marks for students not ENROLLED in the section are ignored. */
  int upsertAttendance(long sessionId, long sectionId, List<AttendanceMark> marks);

  /** Counts per course of the student's enrolments in the term. */
  List<Counts> counts(long studentId, long termId);
}
