package edu.ums.app.proto;

import edu.ums.api.AdminApi;
import edu.ums.api.DevApi;
import edu.ums.api.FacultyApi;
import edu.ums.api.Role;
import edu.ums.api.Session;
import edu.ums.api.StudentApi;
import edu.ums.api.Subscription;
import edu.ums.api.UserSummary;
import edu.ums.api.error.ErrorCode;
import edu.ums.api.error.UmsApiException;
import edu.ums.api.listener.NoticeListener;
import edu.ums.platform.error.UmsException;
import edu.ums.proto.facade.AdminFacade;
import edu.ums.proto.facade.DevFacade;
import edu.ums.proto.facade.FacultyFacade;
import edu.ums.proto.facade.StudentFacade;
import edu.ums.proto.shared.Actor;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A signed-in session. Builds only the facade of its own role (others → ACCESS_DENIED, audited),
 * each behind the interceptor chain and a {@link GuardedFacade} that rejects calls after close and
 * audits failures. Thread-safe: immutable apart from the {@code closed} flag.
 */
final class ProtoSession implements Session {

  private static final Logger LOG = LoggerFactory.getLogger(ProtoSession.class);

  private final Actor actor;
  private final ProtoServices services;
  private final AtomicBoolean closed = new AtomicBoolean();
  private final Object facade;

  ProtoSession(Actor actor, ProtoServices services) {
    this.actor = Objects.requireNonNull(actor, "actor");
    this.services = Objects.requireNonNull(services, "services");
    this.facade = buildFacade();
  }

  private Object buildFacade() {
    return switch (actor.role()) {
      case STUDENT ->
          wrap(
              StudentApi.class,
              new StudentFacade(
                  actor, services.registration(), services.attendance(), services.results()));
      case FACULTY ->
          wrap(FacultyApi.class, new FacultyFacade(actor, services.attendance(), services.marks()));
      case ADMIN ->
          wrap(
              AdminApi.class,
              new AdminFacade(
                  actor, services.terms(), services.marks(), services.auth(), services.activity()));
      case DEV -> wrap(DevApi.class, new DevFacade(actor, services.dev(), services.activity()));
    };
  }

  private <T> T wrap(Class<T> api, T impl) {
    T proxied = services.facades().create(api, impl, services.chain());
    return GuardedFacade.wrap(api, proxied, closed, actor, services.activity());
  }

  @Override
  public UserSummary user() {
    return new UserSummary(actor.username(), actor.fullName(), actor.role());
  }

  @Override
  public Role role() {
    return actor.role();
  }

  @Override
  public boolean mustChangePassword() {
    return false;
  }

  @Override
  public void changePassword(char[] current, char[] next) {
    java.util.Arrays.fill(current, '\0');
    java.util.Arrays.fill(next, '\0');
    throw UmsApiException.of(ErrorCode.FEATURE_DISABLED); // not part of the prototype slice
  }

  @Override
  public StudentApi student() {
    return facadeFor(Role.STUDENT, StudentApi.class);
  }

  @Override
  public FacultyApi faculty() {
    return facadeFor(Role.FACULTY, FacultyApi.class);
  }

  @Override
  public AdminApi admin() {
    return facadeFor(Role.ADMIN, AdminApi.class);
  }

  @Override
  public DevApi dev() {
    return facadeFor(Role.DEV, DevApi.class);
  }

  private <T> T facadeFor(Role role, Class<T> api) {
    if (closed.get()) {
      throw UmsApiException.of(ErrorCode.SESSION_EXPIRED);
    }
    if (actor.role() != role) {
      services
          .activity()
          .failure(actor, null, "ACCESS_DENIED", Map.of("facade", api.getSimpleName()));
      throw UmsApiException.of(ErrorCode.ACCESS_DENIED);
    }
    return api.cast(facade);
  }

  @Override
  public Subscription onNotice(NoticeListener listener) {
    return () -> {}; // notices are not part of the prototype slice
  }

  @Override
  public void close() {
    if (!closed.compareAndSet(false, true)) {
      return;
    }
    try {
      services
          .tx()
          .inTransaction(
              false,
              () -> {
                services.auth().logout(actor);
                return null;
              });
    } catch (UmsException e) { // documented best-effort path: close never throws
      LOG.warn("Could not record logout ({})", e.code(), e);
    }
  }
}
