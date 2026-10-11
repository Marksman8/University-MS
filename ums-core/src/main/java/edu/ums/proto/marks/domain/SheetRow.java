package edu.ums.proto.marks.domain;

/** A section's mark sheet with ownership info. */
public record SheetRow(
    long sheetId,
    long sectionId,
    String sectionCode,
    String courseCode,
    long facultyId,
    SheetState state,
    long version) {}
