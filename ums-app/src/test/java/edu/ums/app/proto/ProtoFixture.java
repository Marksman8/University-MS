package edu.ums.app.proto;

import edu.ums.api.Session;
import edu.ums.api.proto.SectionView;
import edu.ums.app.UmsRuntime;
import edu.ums.platform.config.ClasspathConfigSource;
import edu.ums.platform.config.ConfigLoader;
import edu.ums.platform.db.MigrationRunner;
import edu.ums.testkit.TestDatabase;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Starts a fresh UMS runtime on the test database (clean schema) and seeds the demo data. */
final class ProtoFixture implements AutoCloseable {

  static final String PASSWORD = "DemoPass@2026";

  private final UmsRuntime runtime;

  ProtoFixture() {
    ClasspathConfigSource source = new ClasspathConfigSource(ProtoFixture.class.getClassLoader());
    Map<String, String> env = new HashMap<>(System.getenv());
    env.put("UMS_DB_URL", TestDatabase.url());
    env.put("UMS_ENV", "TEST");
    MigrationRunner.forOwner(new ConfigLoader(source, env).load()).clean();
    runtime = new UmsRuntime(source, env);
    runtime.start();
    if (!runtime.seedDemo()) {
      throw new IllegalStateException("seed refused on a clean schema");
    }
  }

  Session login(String username) {
    return runtime.client().login(username, PASSWORD.toCharArray());
  }

  static long section(List<SectionView> sections, String courseCode) {
    return sections.stream()
        .filter(s -> s.courseCode().equals(courseCode))
        .findFirst()
        .orElseThrow()
        .sectionId();
  }

  @Override
  public void close() {
    runtime.stop();
  }
}
