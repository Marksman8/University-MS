package edu.ums.proto.reg.repository;

import edu.ums.api.proto.SectionView;
import edu.ums.proto.reg.domain.SectionSlot;
import java.util.List;
import java.util.Optional;

/** Prototype persistence for registration. Runs in the caller's transaction. */
public interface RegistrationRepository {

  /** Sections of the term with seats, faculty and whether the student holds a seat. */
  List<SectionView> sections(long termId, long studentId);

  /** The student's ENROLLED sections in the term, ordered by day and start. */
  List<SectionView> timetable(long termId, long studentId);

  /** The section, if it exists. */
  Optional<SectionSlot> slot(long sectionId);

  /** Locks the student row; false if the user is not a student. */
  boolean lockStudent(long studentId);

  /** Whether the student already holds a live enrolment in the course this term. */
  boolean alreadyEnrolled(long studentId, long termId, long courseId);

  /** Codes of prerequisites of the course the student has not passed. */
  List<String> unmetPrerequisites(long studentId, long courseId);

  /** Sum of credits of the student's ENROLLED sections in the term. */
  int enrolledCredits(long studentId, long termId);

  /** Label of one of the student's sections in the term that overlaps the slot, if any. */
  Optional<String> clash(long studentId, SectionSlot slot);

  /** Atomic seat take; false when the section is full. */
  boolean takeSeat(long sectionId);

  /** Releases one seat. */
  void releaseSeat(long sectionId);

  /** Inserts an ENROLLED row and returns its id. */
  long insertEnrolment(long studentId, SectionSlot slot);

  /** The student's ENROLLED enrolment in the section, if any. */
  Optional<Long> liveEnrolment(long studentId, long sectionId);

  /** ENROLLED → DROPPED (STALE_DATA if it changed meanwhile). */
  void dropEnrolment(long enrolmentId);
}
