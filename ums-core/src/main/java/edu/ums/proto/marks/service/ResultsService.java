package edu.ums.proto.marks.service;

import edu.ums.api.proto.ResultsView;
import edu.ums.proto.marks.repository.ResultsRepository;
import edu.ums.proto.shared.Actor;
import java.util.List;
import java.util.Objects;

/** The signed-in student's published results with SGPA and CGPA. */
// Complexity: time O(k) per call for the student's k results in the term; space O(k)
public final class ResultsService {

  private final ResultsRepository results;

  /** Creates the service. */
  public ResultsService(ResultsRepository results) {
    this.results = Objects.requireNonNull(results, "results");
  }

  /** Results of the latest published term; empty view when nothing is published. */
  public ResultsView forStudent(Actor student) {
    return results
        .latestPublished(student.userId())
        .map(
            gpa ->
                new ResultsView(
                    gpa.termName(),
                    results.results(student.userId(), gpa.termId()),
                    gpa.sgpa(),
                    gpa.cgpa(),
                    gpa.credits()))
        .orElseGet(() -> new ResultsView(null, List.of(), null, null, 0));
  }
}
