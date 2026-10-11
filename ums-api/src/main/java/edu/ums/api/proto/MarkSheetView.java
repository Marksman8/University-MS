package edu.ums.api.proto;

import java.util.List;

/** A section's mark sheet (prototype). */
public record MarkSheetView(
    long sectionId,
    String sectionCode,
    String courseCode,
    String state,
    List<ComponentView> components,
    List<MarkRow> rows) {}
