package edu.ums.api.proto;

/** Attendance status of one enrolment for a session (prototype). */
public record AttendanceMark(long enrolmentId, AttendanceStatus status) {}
