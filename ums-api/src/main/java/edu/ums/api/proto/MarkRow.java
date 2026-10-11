package edu.ums.api.proto;

import java.util.List;

/** One student's marks for every component (prototype). */
public record MarkRow(long enrolmentId, String rollNo, String studentName, List<MarkCell> cells) {}
