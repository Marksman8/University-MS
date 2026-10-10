package edu.ums.platform.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import edu.ums.platform.config.AppConfig.DbAccount;
import org.junit.jupiter.api.Test;

class SecretTest {

  @Test
  void toStringIsMasked() {
    Secret secret = Secret.of("hunter2-password");
    assertThat(secret.toString()).isEqualTo("****");
    assertThat(new DbAccount("ums_app", secret).toString())
        .contains("ums_app")
        .doesNotContain("hunter2");
  }

  @Test
  void revealReturnsACopy() {
    Secret secret = Secret.of("abc");
    char[] copy = secret.reveal();
    copy[0] = 'X';
    assertThat(secret.reveal()).containsExactly('a', 'b', 'c');
  }

  @Test
  void clearZeroesAndBlocksFurtherUse() {
    Secret secret = Secret.of("abc");
    secret.clear();
    secret.clear();
    assertThat(secret.isCleared()).isTrue();
    assertThatIllegalStateException().isThrownBy(secret::reveal);
    assertThat(secret.toString()).isEqualTo("****");
  }
}
