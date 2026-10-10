package edu.ums.platform.db;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/** SQL type of a keyset sort column: how to bind it and how to round-trip it through a cursor. */
public enum KeyType {
  LONG {
    @Override
    Object parse(String text) {
      return Long.parseLong(text);
    }

    @Override
    void bind(PreparedStatement ps, int index, Object value) throws SQLException {
      ps.setLong(index, (Long) value);
    }
  },
  STRING {
    @Override
    Object parse(String text) {
      return text;
    }

    @Override
    void bind(PreparedStatement ps, int index, Object value) throws SQLException {
      ps.setString(index, (String) value);
    }
  },
  /** {@code timestamptz}; values are {@link Instant} or {@link OffsetDateTime}. */
  TIMESTAMP {
    @Override
    String format(Object value) {
      return value instanceof OffsetDateTime odt ? odt.toInstant().toString() : value.toString();
    }

    @Override
    Object parse(String text) {
      return Instant.parse(text);
    }

    @Override
    void bind(PreparedStatement ps, int index, Object value) throws SQLException {
      ps.setObject(index, OffsetDateTime.ofInstant((Instant) value, ZoneOffset.UTC));
    }
  };

  /** Encodes a key value (as returned by the key extractor) for a cursor. */
  String format(Object value) {
    return value.toString();
  }

  /** Decodes a cursor value; throws on malformed input. */
  abstract Object parse(String text);

  /** Binds a decoded value. */
  abstract void bind(PreparedStatement ps, int index, Object value) throws SQLException;
}
