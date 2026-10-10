package edu.ums.platform.db;

import static org.assertj.core.api.Assertions.assertThat;

import edu.ums.api.dto.Cursor;
import edu.ums.api.dto.Page;
import edu.ums.api.dto.PageRequest;
import edu.ums.platform.db.KeysetQuery.Direction;
import edu.ums.platform.db.ProbeRepository.Probe;
import edu.ums.platform.error.ConstraintErrorMap;
import edu.ums.platform.error.SqlErrorMapper;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/** Keyset paging over 1 000 rows whose sort key has many duplicates (only 10 distinct times). */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class KeysetPagingIT {

  private static final int ROWS = 1_000;
  private static final int PAGE = 37;
  private static final Instant BASE = Instant.parse("2026-03-01T09:00:00Z");

  private DataSources pools;
  private TransactionRunner tx;
  private ProbeRepository probes;

  @BeforeAll
  void seed() throws SQLException {
    DbTestSupport.exec(
        "CREATE SCHEMA IF NOT EXISTS ums",
        ProbeRepository.CREATE_TABLE,
        "TRUNCATE ums.it_probe RESTART IDENTITY");
    SqlErrorMapper errors = new SqlErrorMapper(ConstraintErrorMap.standard());
    pools = DataSources.create(DbTestSupport.config(Duration.ofSeconds(10)), errors);
    tx = new TransactionRunner(pools.app(), errors, new RetryPolicy(new Random(), Sleeper.SYSTEM));
    probes = new ProbeRepository(tx, errors);
    tx.inTransaction(
        false,
        () -> {
          // shuffled insert order so ids do not follow time order
          List<Integer> order = new ArrayList<>(IntStream.range(0, ROWS).boxed().toList());
          java.util.Collections.shuffle(order, new Random(11));
          for (int i : order) {
            probes.insert("p" + i, i % 7, BASE.plusSeconds(i % 10));
          }
          return null;
        });
  }

  @AfterAll
  void stop() throws SQLException {
    pools.close();
    DbTestSupport.exec("DROP TABLE IF EXISTS ums.it_probe");
  }

  @Test
  void descendingPagesAreDisjointOrderedAndComplete() {
    List<Probe> all = readAll(Direction.DESC, null);

    assertThat(all).hasSize(ROWS);
    assertThat(all.stream().map(Probe::id).distinct()).hasSize(ROWS);
    assertThat(all)
        .isSortedAccordingTo(
            Comparator.comparing(Probe::createdAt).thenComparing(Probe::id).reversed());
  }

  @Test
  void ascendingPagesWithFilterAreDisjointOrderedAndComplete() {
    List<Probe> all = readAll(Direction.ASC, 3);

    long expected = IntStream.range(0, ROWS).filter(i -> i % 7 != 3).count();
    assertThat(all).hasSize((int) expected).noneMatch(p -> p.bucket() == 3);
    assertThat(all.stream().map(Probe::id).distinct()).hasSize((int) expected);
    assertThat(all)
        .isSortedAccordingTo(Comparator.comparing(Probe::createdAt).thenComparing(Probe::id));
  }

  @Test
  void lastPageHasNoCursorAndFullPagesHaveExactSize() {
    List<Page<Probe>> pages = pages(Direction.DESC, null);
    assertThat(pages).hasSize((ROWS + PAGE - 1) / PAGE);
    pages.subList(0, pages.size() - 1).forEach(p -> assertThat(p.items()).hasSize(PAGE));
    assertThat(pages.get(pages.size() - 1).next()).isEmpty();
  }

  private List<Probe> readAll(Direction direction, Integer excludeBucket) {
    List<Probe> all = new ArrayList<>();
    pages(direction, excludeBucket).forEach(p -> all.addAll(p.items()));
    return all;
  }

  private List<Page<Probe>> pages(Direction direction, Integer excludeBucket) {
    List<Page<Probe>> pages = new ArrayList<>();
    Set<Cursor> seen = new HashSet<>();
    Optional<Cursor> after = Optional.empty();
    do {
      Optional<Cursor> current = after;
      Page<Probe> page =
          tx.inTransaction(
              true,
              () -> probes.pageByTime(new PageRequest(current, PAGE), direction, excludeBucket));
      pages.add(page);
      after = page.next();
      after.ifPresent(c -> assertThat(seen.add(c)).as("cursor repeats").isTrue());
    } while (after.isPresent() && pages.size() <= ROWS);
    return pages;
  }
}
