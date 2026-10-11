package edu.ums.api.proto;

/** One enrolled student of a section (prototype). */
public record RosterEntry(long enrolmentId, String rollNo, String studentName) {}
