package edu.ums.proto.reg.domain;

/** What registration needs to know about a section. */
public record SectionSlot(
    long id,
    long termId,
    long courseId,
    String label,
    int credits,
    int day,
    int startMin,
    int endMin) {

  /** Same day and {@code start < otherEnd && otherStart < end}. */
  public boolean overlaps(int otherDay, int otherStart, int otherEnd) {
    return day == otherDay && startMin < otherEnd && otherStart < endMin;
  }
}
