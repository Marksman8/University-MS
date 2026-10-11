package edu.ums.proto.marks.service;

import edu.ums.api.proto.MarkEntry;
import edu.ums.api.proto.MarkSheetSummary;
import edu.ums.api.proto.MarkSheetView;
import edu.ums.api.proto.SectionView;
import edu.ums.proto.shared.Actor;
import java.util.List;

/** Prototype mark entry, submission and approval. Runs in the caller's transaction. */
public interface MarksService {

  /** The faculty member's sections in the current term. */
  List<SectionView> mySections(Actor faculty);

  /** The mark sheet of an own section. */
  MarkSheetView sheet(Actor faculty, long sectionId);

  /** Saves marks of an own DRAFT sheet. */
  void save(Actor faculty, long sectionId, List<MarkEntry> entries);

  /** DRAFT → SUBMITTED when complete. */
  void submit(Actor faculty, long sectionId);

  /** SUBMITTED → APPROVED with results computed. */
  void approve(Actor admin, long sectionId);

  /** SUBMITTED → DRAFT. */
  void returnToFaculty(Actor admin, long sectionId);

  /** Sheets of the current term. */
  List<MarkSheetSummary> summaries();
}
