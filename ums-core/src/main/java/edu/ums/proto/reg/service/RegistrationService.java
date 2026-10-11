package edu.ums.proto.reg.service;

import edu.ums.api.proto.SectionView;
import edu.ums.proto.shared.Actor;
import java.util.List;

/** Prototype registration for the signed-in student. Runs in the caller's transaction. */
public interface RegistrationService {

  /** Sections of the current term. */
  List<SectionView> sections(Actor student);

  /** The student's timetable in the current term. */
  List<SectionView> timetable(Actor student);

  /** Registers the student for a section. */
  void register(Actor student, long sectionId);

  /** Drops the student's seat in a section. */
  void drop(Actor student, long sectionId);
}
