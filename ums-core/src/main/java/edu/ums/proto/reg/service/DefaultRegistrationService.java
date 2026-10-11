package edu.ums.proto.reg.service;

import edu.ums.api.error.ErrorCode;
import edu.ums.api.proto.SectionView;
import edu.ums.platform.error.AccessDeniedException;
import edu.ums.platform.error.NotFoundException;
import edu.ums.platform.error.RuleViolationException;
import edu.ums.proto.audit.service.ActivityRecorder;
import edu.ums.proto.reg.domain.SectionSlot;
import edu.ums.proto.reg.repository.RegistrationRepository;
import edu.ums.proto.shared.Actor;
import edu.ums.proto.term.domain.TermRow;
import edu.ums.proto.term.domain.TermState;
import edu.ums.proto.term.repository.TermRepository;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Registration in the documented order: term FOR SHARE → student FOR UPDATE → already enrolled →
 * prerequisites → credit limit → timetable clash → atomic seat take → insert. Lock order term →
 * student → section.
 */
// Complexity: time O(log n + k) per register/drop (indexed checks over the student's k
// enrolments); space O(1) beyond the bounded result lists
public final class DefaultRegistrationService implements RegistrationService {

  private final RegistrationRepository registrations;
  private final TermRepository terms;
  private final ActivityRecorder activity;
  private final int creditLimit;

  /** Creates the service. */
  public DefaultRegistrationService(
      RegistrationRepository registrations,
      TermRepository terms,
      ActivityRecorder activity,
      int creditLimit) {
    this.registrations = Objects.requireNonNull(registrations, "registrations");
    this.terms = Objects.requireNonNull(terms, "terms");
    this.activity = Objects.requireNonNull(activity, "activity");
    this.creditLimit = creditLimit;
  }

  @Override
  public List<SectionView> sections(Actor student) {
    return registrations.sections(currentTermId(), student.userId());
  }

  @Override
  public List<SectionView> timetable(Actor student) {
    return registrations.timetable(currentTermId(), student.userId());
  }

  @Override
  public void register(Actor student, long sectionId) {
    TermRow term = openTermForShare();
    SectionSlot slot = slotInTerm(sectionId, term);
    lockStudent(student);
    long studentId = student.userId();
    if (registrations.alreadyEnrolled(studentId, term.id(), slot.courseId())) {
      throw rule(ErrorCode.ALREADY_ENROLLED, Map.of("section", slot.label()));
    }
    List<String> missing = registrations.unmetPrerequisites(studentId, slot.courseId());
    if (!missing.isEmpty()) {
      throw rule(ErrorCode.PREREQ_NOT_MET, Map.of("courses", String.join(", ", missing)));
    }
    int credits = registrations.enrolledCredits(studentId, term.id()) + slot.credits();
    if (credits > creditLimit) {
      throw rule(ErrorCode.CREDIT_LIMIT, Map.of("limit", creditLimit, "credits", credits));
    }
    registrations
        .clash(studentId, slot)
        .ifPresent(
            other -> {
              throw rule(ErrorCode.TIMETABLE_CLASH, Map.of("section", other));
            });
    if (!registrations.takeSeat(slot.id())) {
      throw rule(ErrorCode.SECTION_FULL, Map.of("section", slot.label()));
    }
    long enrolmentId = registrations.insertEnrolment(studentId, slot);
    activity.success(
        student, "REGISTER", "enrolment", enrolmentId, Map.of("section", slot.label()));
  }

  @Override
  public void drop(Actor student, long sectionId) {
    TermRow term = openTermForShare();
    SectionSlot slot = slotInTerm(sectionId, term);
    lockStudent(student);
    long enrolmentId =
        registrations
            .liveEnrolment(student.userId(), sectionId)
            .orElseThrow(() -> new NotFoundException(Map.of("entity", "enrolment")));
    registrations.dropEnrolment(enrolmentId);
    registrations.releaseSeat(sectionId);
    activity.success(student, "DROP", "enrolment", enrolmentId, Map.of("section", slot.label()));
  }

  private TermRow openTermForShare() {
    TermRow term =
        terms.currentForShare().orElseThrow(() -> new NotFoundException(Map.of("entity", "term")));
    if (term.state() != TermState.REGISTRATION_OPEN) {
      throw rule(ErrorCode.WINDOW_CLOSED, Map.of("state", term.state()));
    }
    return term;
  }

  private SectionSlot slotInTerm(long sectionId, TermRow term) {
    SectionSlot slot =
        registrations
            .slot(sectionId)
            .orElseThrow(() -> new NotFoundException(Map.of("entity", "section")));
    if (slot.termId() != term.id()) {
      throw new NotFoundException(Map.of("entity", "section"));
    }
    return slot;
  }

  private void lockStudent(Actor student) {
    if (!registrations.lockStudent(student.userId())) {
      throw new AccessDeniedException(Map.of());
    }
  }

  private long currentTermId() {
    return terms.current().orElseThrow(() -> new NotFoundException(Map.of("entity", "term"))).id();
  }

  private static RuleViolationException rule(ErrorCode code, Map<String, Object> details) {
    return new RuleViolationException(code, details);
  }
}
