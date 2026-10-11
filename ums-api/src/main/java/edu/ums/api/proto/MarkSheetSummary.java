package edu.ums.api.proto;

/** A mark sheet as listed to the admin (prototype). */
public record MarkSheetSummary(
    long sectionId, String sectionCode, String courseCode, String facultyName, String state) {}
