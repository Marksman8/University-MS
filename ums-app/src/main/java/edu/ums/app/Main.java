package edu.ums.app;

import edu.ums.platform.config.ClasspathConfigSource;
import edu.ums.platform.observe.HealthReport;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Headless entry point: starts the runtime, logs health, installs a shutdown hook and waits. {@code
 * --check} starts, logs health, stops and exits (smoke run for CI and operators).
 *
 * <p>Run with {@code mvn -pl ums-app exec:java} (add {@code -Dexec.args=--check} for a smoke run).
 */
public final class Main {

  private static final Logger LOG = LoggerFactory.getLogger(Main.class);

  private Main() {}

  /** Starts UMS. Exits non-zero (via the thrown exception) if startup fails. */
  public static void main(String[] args) throws InterruptedException {
    UmsRuntime runtime =
        new UmsRuntime(new ClasspathConfigSource(Main.class.getClassLoader()), System.getenv());
    runtime.start();
    HealthReport report = runtime.health();
    LOG.info("Health {}: {}", report.status(), report.probes());

    if (Arrays.asList(args).contains("--check")) {
      runtime.stop();
      return;
    }
    if (Arrays.asList(args).contains("--seed-demo")) {
      seedDemo(runtime);
      return;
    }
    CountDownLatch stopped = new CountDownLatch(1);
    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(
                () -> {
                  runtime.stop();
                  stopped.countDown();
                },
                "ums-shutdown"));
    stopped.await();
  }

  /** Prototype: seeds the demo data, then stops. Exits non-zero when refused. */
  private static void seedDemo(UmsRuntime runtime) {
    boolean seeded;
    try {
      seeded = runtime.seedDemo();
    } finally {
      runtime.stop();
    }
    if (!seeded) {
      LOG.error("--seed-demo refused: app_user is not empty");
      System.exit(2);
    }
    LOG.info("Demo data seeded (password for every demo user is in README_DEMO.md)");
  }
}
