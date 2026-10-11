package edu.ums.proto.att.service;

import edu.ums.api.proto.AttendanceMark;
import edu.ums.api.proto.AttendanceSummary;
import edu.ums.api.proto.RosterEntry;
import edu.ums.proto.shared.Actor;
import java.time.LocalDate;
import java.util.List;

/** Prototype attendance. Runs in the caller's transaction. */
public interface AttendanceService {

  /** Enrolled students of an own section. */
  List<RosterEntry> roster(Actor faculty, long sectionId);

  /** Records attendance of an own section for a date that is not in the future. */
  void record(Actor faculty, long sectionId, LocalDate date, List<AttendanceMark> marks);

  /** The student's attendance per course in the current term. */
  List<AttendanceSummary> summary(Actor student);
}
