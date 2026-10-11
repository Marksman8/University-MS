package edu.ums.app.proto;

import edu.ums.api.Session;
import edu.ums.api.UmsClient;
import edu.ums.proto.shared.Actor;
import java.util.Objects;

/** Prototype {@link UmsClient}: signs in and opens a {@link ProtoSession}. */
final class ProtoClient implements UmsClient {

  private final ProtoServices services;

  ProtoClient(ProtoServices services) {
    this.services = Objects.requireNonNull(services, "services");
  }

  @Override
  public Session login(String username, char[] password) {
    Actor actor = services.auth().login(username, password);
    return new ProtoSession(actor, services);
  }
}
