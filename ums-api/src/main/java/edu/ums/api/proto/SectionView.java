package edu.ums.api.proto;

/**
 * A section as listed to students, faculty and admins; sheetState is null before teaching starts
 * (prototype).
 */
public record SectionView(
    long sectionId,
    String courseCode,
    String courseTitle,
    String sectionCode,
    int credits,
    int day,
    int startMin,
    int endMin,
    String room,
    int capacity,
    int enrolled,
    String facultyName,
    boolean mine,
    String sheetState) {}
