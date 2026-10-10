package edu.ums.platform.error;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import org.postgresql.util.PSQLException;
import org.postgresql.util.ServerErrorMessage;

class SqlErrorMapperPropertiesTest {

  private static final String LEAK = "SELECT password_hash FROM app_user WHERE username='alice'";

  private final SqlErrorMapper mapper = new SqlErrorMapper(ConstraintErrorMap.standard());
  private final ApiErrorTranslator translator = new ApiErrorTranslator(new java.util.Random(7));

  @Property
  void neverReturnsNullAndNeverLeaksSqlText(
      @ForAll("states") String state,
      @ForAll("constraints") String constraint,
      @ForAll boolean cancelRequested,
      @ForAll boolean asPsql) {
    SQLException e =
        asPsql
            ? new PSQLException(
                new ServerErrorMessage(
                    "SERROR\0C" + state + "\0M" + LEAK + "\0n" + constraint + "\0"))
            : new SQLException(LEAK, state);

    UmsException mapped = mapper.map(e, cancelRequested);

    assertThat(mapped).isNotNull();
    assertThat(mapped.getMessage()).doesNotContain("SELECT", "alice");
    assertThat(mapped.details().values())
        .allSatisfy(v -> assertThat(String.valueOf(v)).doesNotContain("alice"));

    var api = translator.translate(mapped);
    assertThat(api.getMessage()).doesNotContain("SELECT", "alice");
    assertThat(api.args().values())
        .allSatisfy(v -> assertThat(String.valueOf(v)).doesNotContain("alice"));
  }

  @Provide
  Arbitrary<String> states() {
    Arbitrary<String> known =
        Arbitraries.of(
            "08001", "08006", "40001", "40P01", "23505", "23P01", "23514", "23503", "55P03",
            "57014");
    Arbitrary<String> random =
        Arbitraries.strings().withCharRange('0', '9').withCharRange('A', 'Z').ofLength(5);
    return Arbitraries.oneOf(known, random);
  }

  @Provide
  Arbitrary<String> constraints() {
    return Arbitraries.oneOf(
        Arbitraries.of(ConstraintErrorMap.standard().asMap().keySet()),
        Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(20));
  }
}
