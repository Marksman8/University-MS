package edu.ums.proto.org.service;

import edu.ums.api.Role;
import edu.ums.platform.db.TransactionRunner;
import edu.ums.proto.audit.service.ActivityRecorder;
import edu.ums.proto.auth.repository.ProtoUserRepository;
import edu.ums.proto.auth.service.PasswordHasher;
import edu.ums.proto.org.repository.SeedRepository;
import edu.ums.proto.org.repository.SeedRepository.SectionSpec;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Seeds the demo dataset (prototype): 9 users, CSA/MCA, CS501–CS504, a published history term in
 * which every student took CS501 (stu.esha failed it), and "Odd 2026" in REGISTRATION_OPEN with
 * four sections — CS501-A overlaps CS502-A (TIMETABLE_CLASH) and CS503-A has capacity 2
 * (SECTION_FULL).
 */
// Complexity: time O(u × 2^cost) for u = 9 BCrypt hashes; space O(u)
public final class DemoSeeder {

  /** Password of every demo user. Demo data only; refused outside LOCAL/TEST by the caller. */
  public static final String DEMO_PASSWORD = "DemoPass@2026";

  private static final String[][] STUDENTS = {
    {"stu.anu", "Anu Thomas", "MCA26001", "85.00", "A+", "9"},
    {"stu.binu", "Binu Mathew", "MCA26002", "72.00", "A", "8"},
    {"stu.chitra", "Chitra Nair", "MCA26003", "64.00", "B+", "7"},
    {"stu.dev", "Dev Kumar", "MCA26004", "55.00", "B", "6"},
    {"stu.esha", "Esha Pillai", "MCA26005", "30.00", "F", "0"},
  };

  private final ProtoUserRepository users;
  private final SeedRepository seed;
  private final PasswordHasher hasher;
  private final ActivityRecorder activity;
  private final TransactionRunner transactions;

  /** Creates the seeder. */
  public DemoSeeder(
      ProtoUserRepository users,
      SeedRepository seed,
      PasswordHasher hasher,
      ActivityRecorder activity,
      TransactionRunner transactions) {
    this.users = Objects.requireNonNull(users, "users");
    this.seed = Objects.requireNonNull(seed, "seed");
    this.hasher = Objects.requireNonNull(hasher, "hasher");
    this.activity = Objects.requireNonNull(activity, "activity");
    this.transactions = Objects.requireNonNull(transactions, "transactions");
  }

  /**
   * Seeds everything in one transaction.
   *
   * @return {@code false} (nothing written) if any user already exists
   */
  public boolean seed() {
    Map<String, String> hashes = new HashMap<>();
    for (String name : usernames()) {
      hashes.put(name, hasher.hash(DEMO_PASSWORD.toCharArray()));
    }
    return transactions.inTransaction(
        false,
        () -> {
          if (users.any()) {
            return false;
          }
          write(hashes);
          activity.system("DEMO_SEEDED", null, null, Map.of("users", hashes.size()));
          return true;
        });
  }

  private static List<String> usernames() {
    return List.of(
        "admin",
        "dev",
        "fac.rao",
        "fac.menon",
        "stu.anu",
        "stu.binu",
        "stu.chitra",
        "stu.dev",
        "stu.esha");
  }

  private void write(Map<String, String> hashes) {
    users.insert("admin", hashes.get("admin"), Role.ADMIN, "Asha Admin");
    users.insert("dev", hashes.get("dev"), Role.DEV, "Deepak Dev");
    long dept = seed.department("CSA", "Computer Science and Applications");
    long mca = seed.programme("MCA", "Master of Computer Applications", dept);
    long rao = users.insert("fac.rao", hashes.get("fac.rao"), Role.FACULTY, "Dr. Ravi Rao");
    long menon =
        users.insert("fac.menon", hashes.get("fac.menon"), Role.FACULTY, "Dr. Meera Menon");
    seed.faculty(rao, dept);
    seed.faculty(menon, dept);
    long cs501 = seed.course("CS501", "Data Structures", 4, dept);
    long cs502 = seed.course("CS502", "Database Management Systems", 4, dept);
    long cs503 = seed.course("CS503", "Programming in Java", 3, dept);
    long cs504 = seed.course("CS504", "Artificial Intelligence", 3, dept);
    seed.prerequisite(cs504, cs501);

    long history =
        seed.term(
            "Even 2026", LocalDate.of(2026, 1, 5), LocalDate.of(2026, 5, 15), "RESULTS_PUBLISHED");
    long historySection =
        seed.section(new SectionSpec(history, cs501, "H", 60, rao, 1, 540, 630, "LH-101"));
    seed.setEnrolled(historySection, STUDENTS.length);
    for (String[] s : STUDENTS) {
      long id = users.insert(s[0], hashes.get(s[0]), Role.STUDENT, s[1]);
      seed.student(id, mca, s[2]);
      BigDecimal point = new BigDecimal(s[5]);
      seed.completedResult(id, historySection, history, cs501, new BigDecimal(s[3]), s[4], point);
      seed.termResult(id, history, point, point, "F".equals(s[4]) ? 0 : 4);
    }

    long odd =
        seed.term(
            "Odd 2026", LocalDate.of(2026, 7, 20), LocalDate.of(2026, 12, 18), "REGISTRATION_OPEN");
    section(new SectionSpec(odd, cs501, "A", 40, rao, 1, 600, 690, "LH-101"));
    section(new SectionSpec(odd, cs502, "A", 40, menon, 1, 540, 630, "LH-102"));
    section(new SectionSpec(odd, cs503, "A", 2, rao, 2, 540, 630, "LAB-1"));
    section(new SectionSpec(odd, cs504, "A", 40, menon, 3, 840, 930, "LH-103"));
  }

  private void section(SectionSpec spec) {
    long id = seed.section(spec);
    seed.component(id, "Internal", BigDecimal.valueOf(40), BigDecimal.valueOf(40));
    seed.component(id, "Final", BigDecimal.valueOf(60), BigDecimal.valueOf(60));
  }
}
