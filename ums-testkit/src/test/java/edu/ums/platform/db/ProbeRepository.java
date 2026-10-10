package edu.ums.platform.db;

import edu.ums.api.dto.Page;
import edu.ums.api.dto.PageRequest;
import edu.ums.platform.db.KeysetQuery.Direction;
import edu.ums.platform.db.KeysetQuery.KeyColumn;
import edu.ums.platform.error.SqlErrorMapper;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

/** Test repository over {@code ums.it_probe}, exercising every {@link BaseRepository} template. */
final class ProbeRepository extends BaseRepository {

  static final String CREATE_TABLE =
      """
      CREATE TABLE IF NOT EXISTS ums.it_probe (
        id         BIGINT GENERATED ALWAYS AS IDENTITY CONSTRAINT pk_it_probe PRIMARY KEY,
        code       TEXT NOT NULL CONSTRAINT uq_it_probe_code UNIQUE,
        bucket     INT NOT NULL,
        created_at TIMESTAMPTZ NOT NULL,
        state      TEXT NOT NULL DEFAULT 'DRAFT',
        version    BIGINT NOT NULL DEFAULT 0
      )""";

  private static final List<KeyColumn> TIME_KEYS =
      List.of(new KeyColumn("created_at", KeyType.TIMESTAMP), new KeyColumn("id", KeyType.LONG));

  record Probe(long id, String code, int bucket, Instant createdAt, String state, long version) {}

  ProbeRepository(TransactionRunner tx, SqlErrorMapper errors) {
    super(tx, errors, new CursorCodec());
  }

  long insert(String code, int bucket, Instant createdAt) {
    return queryOne(
        "INSERT INTO it_probe (code, bucket, created_at) VALUES (?, ?, ?) RETURNING id",
        ps -> {
          ps.setString(1, code);
          ps.setInt(2, bucket);
          ps.setObject(3, OffsetDateTime.ofInstant(createdAt, ZoneOffset.UTC));
        },
        rs -> rs.getLong(1));
  }

  int insertAll(List<String> codes, Instant createdAt) {
    return batch(
        "INSERT INTO it_probe (code, bucket, created_at) VALUES (?, 0, ?)",
        codes,
        (ps, code) -> {
          ps.setString(1, code);
          ps.setObject(2, OffsetDateTime.ofInstant(createdAt, ZoneOffset.UTC));
        });
  }

  long count() {
    return queryOne("SELECT count(*) FROM it_probe", StatementBinder.NONE, rs -> rs.getLong(1));
  }

  Probe get(long id) {
    return queryOne("SELECT * FROM it_probe WHERE id = ?", ps -> ps.setLong(1, id), this::map);
  }

  Optional<Probe> find(String code) {
    return queryOptional(
        "SELECT * FROM it_probe WHERE code = ?", ps -> ps.setString(1, code), this::map);
  }

  long transition(long id, String from, String to, long version) {
    return casState("it_probe", id, from, to, version);
  }

  Page<Probe> pageByTime(PageRequest request, Direction direction, Integer excludeBucket) {
    String where = excludeBucket == null ? "" : "bucket <> ?";
    List<Object> params = excludeBucket == null ? List.of() : List.of(excludeBucket);
    return queryPage(
        new KeysetQuery<>(
            "SELECT * FROM it_probe",
            where,
            params,
            TIME_KEYS,
            direction,
            this::map,
            p -> List.of(p.createdAt(), p.id())),
        request);
  }

  String sleep(int seconds) {
    return queryOne(
        "SELECT pg_sleep(?)::text",
        ps -> ps.setInt(1, seconds),
        rs -> String.valueOf(rs.getString(1)));
  }

  private Probe map(java.sql.ResultSet rs) throws SQLException {
    return new Probe(
        rs.getLong("id"),
        rs.getString("code"),
        rs.getInt("bucket"),
        rs.getObject("created_at", OffsetDateTime.class).toInstant(),
        rs.getString("state"),
        rs.getLong("version"));
  }
}
