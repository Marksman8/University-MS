package edu.ums.app;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.rolling.RollingFileAppender;
import ch.qos.logback.core.rolling.TimeBasedRollingPolicy;
import ch.qos.logback.core.status.Status;
import java.net.URL;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LogbackConfigTest {

  @TempDir Path logDir;

  private LoggerContext context;

  @BeforeEach
  void configure() throws Exception {
    context = new LoggerContext();
    context.putProperty("UMS_LOG_DIR", logDir.toString());
    JoranConfigurator configurator = new JoranConfigurator();
    configurator.setContext(context);
    URL config = LogbackConfigTest.class.getResource("/logback.xml");
    assertThat(config).as("logback.xml on classpath").isNotNull();
    configurator.doConfigure(config);
  }

  @AfterEach
  void stop() {
    context.stop();
  }

  @Test
  void parsesWithoutErrors() {
    assertThat(context.getStatusManager().getCopyOfStatusList())
        .noneMatch(s -> s.getLevel() >= Status.WARN);
  }

  @Test
  void rootHasConsoleAndDailyRollingFileKeptFourteenDays() {
    Logger root = context.getLogger(Logger.ROOT_LOGGER_NAME);
    assertThat(root.getAppender("CONSOLE")).isNotNull();

    RollingFileAppender<ILoggingEvent> file =
        (RollingFileAppender<ILoggingEvent>) root.getAppender("FILE");
    assertThat(file.getFile()).startsWith(logDir.toString());
    TimeBasedRollingPolicy<?> policy = (TimeBasedRollingPolicy<?>) file.getRollingPolicy();
    assertThat(policy.getMaxHistory()).isEqualTo(14);
    assertThat(policy.getFileNamePattern()).contains("%d{yyyy-MM-dd, UTC}");
  }

  @Test
  void patternUsesUtcAndMdcFields() {
    Logger root = context.getLogger(Logger.ROOT_LOGGER_NAME);
    RollingFileAppender<ILoggingEvent> file =
        (RollingFileAppender<ILoggingEvent>) root.getAppender("FILE");
    String pattern = ((PatternLayoutEncoder) file.getEncoder()).getPattern();
    assertThat(pattern).contains("UTC", "%X{traceId", "%X{userId", "%X{role");
  }
}
