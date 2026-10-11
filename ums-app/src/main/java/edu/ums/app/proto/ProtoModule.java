package edu.ums.app.proto;

import edu.ums.api.UmsClient;
import edu.ums.platform.config.AppConfig;
import edu.ums.platform.db.CursorCodec;
import edu.ums.platform.db.DataSources;
import edu.ums.platform.db.TransactionRunner;
import edu.ums.platform.error.ApiErrorTranslator;
import edu.ums.platform.error.SqlErrorMapper;
import edu.ums.platform.invoke.ErrorMappingInterceptor;
import edu.ums.platform.invoke.FacadeProxyFactory;
import edu.ums.platform.invoke.Interceptor;
import edu.ums.platform.invoke.TracingInterceptor;
import edu.ums.platform.invoke.TransactionInterceptor;
import edu.ums.platform.observe.HealthService;
import edu.ums.platform.observe.MetricsRegistry;
import edu.ums.platform.observe.Tracer;
import edu.ums.proto.att.repository.JdbcAttendanceRepository;
import edu.ums.proto.att.service.AttendanceService;
import edu.ums.proto.att.service.DefaultAttendanceService;
import edu.ums.proto.audit.repository.JdbcActivityRepository;
import edu.ums.proto.audit.service.ActivityRecorder;
import edu.ums.proto.audit.service.DefaultActivityRecorder;
import edu.ums.proto.auth.repository.JdbcProtoUserRepository;
import edu.ums.proto.auth.service.AuthService;
import edu.ums.proto.auth.service.DefaultAuthService;
import edu.ums.proto.auth.service.PasswordHasher;
import edu.ums.proto.dev.repository.JdbcConsoleRepository;
import edu.ums.proto.dev.service.DefaultDevService;
import edu.ums.proto.dev.service.DevService;
import edu.ums.proto.marks.repository.JdbcMarksRepository;
import edu.ums.proto.marks.repository.JdbcResultsRepository;
import edu.ums.proto.marks.service.DefaultMarksService;
import edu.ums.proto.marks.service.MarksService;
import edu.ums.proto.marks.service.ResultsService;
import edu.ums.proto.org.repository.JdbcSeedRepository;
import edu.ums.proto.org.service.DemoSeeder;
import edu.ums.proto.reg.repository.JdbcRegistrationRepository;
import edu.ums.proto.reg.service.DefaultRegistrationService;
import edu.ums.proto.reg.service.RegistrationService;
import edu.ums.proto.term.repository.JdbcTermRepository;
import edu.ums.proto.term.repository.TermRepository;
import edu.ums.proto.term.service.DefaultTermService;
import edu.ums.proto.term.service.TermService;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.List;

/**
 * Prototype composition (called from {@code AppContext}): repositories → services → the {@link
 * UmsClient} whose sessions hand out role facades behind the P0 interceptor chain.
 */
public final class ProtoModule {

  private final UmsClient client;
  private final DemoSeeder seeder;
  private final ProtoServices services;

  /** Wires everything. {@code bcryptCost} is {@link PasswordHasher#COST} outside tests. */
  public ProtoModule(
      AppConfig config,
      DataSources dataSources,
      TransactionRunner tx,
      SqlErrorMapper errors,
      ApiErrorTranslator apiErrors,
      FacadeProxyFactory facades,
      Tracer tracer,
      MetricsRegistry metrics,
      HealthService health,
      int bcryptCost) {
    CursorCodec cursors = new CursorCodec();
    Clock clock = Clock.systemDefaultZone();
    ActivityRecorder activity =
        new DefaultActivityRecorder(new JdbcActivityRepository(tx, errors, cursors), tx);
    PasswordHasher hasher = new PasswordHasher(bcryptCost, new SecureRandom());
    JdbcProtoUserRepository users = new JdbcProtoUserRepository(tx, errors, cursors);
    AuthService auth =
        new DefaultAuthService(
            users,
            activity,
            hasher,
            tx,
            config.policy().lockoutMaxFailures(),
            config.policy().lockoutDuration());
    TermRepository terms = new JdbcTermRepository(tx, errors, cursors);
    TermService termService = new DefaultTermService(terms, activity);
    RegistrationService registration =
        new DefaultRegistrationService(
            new JdbcRegistrationRepository(tx, errors, cursors),
            terms,
            activity,
            config.policy().creditLimit());
    AttendanceService attendance =
        new DefaultAttendanceService(
            new JdbcAttendanceRepository(tx, errors, cursors), terms, activity, clock);
    MarksService marks =
        new DefaultMarksService(new JdbcMarksRepository(tx, errors, cursors), terms, activity);
    ResultsService results = new ResultsService(new JdbcResultsRepository(tx, errors, cursors));
    DevService dev =
        new DefaultDevService(new JdbcConsoleRepository(dataSources.console()), health, activity);
    List<Interceptor> chain =
        List.of(
            new ErrorMappingInterceptor(apiErrors),
            new TracingInterceptor(tracer, metrics),
            new TransactionInterceptor(tx));
    this.services =
        new ProtoServices(
            auth,
            activity,
            termService,
            registration,
            attendance,
            marks,
            results,
            dev,
            facades,
            chain,
            apiErrors,
            tx);
    this.client =
        facades.create(
            edu.ums.api.UmsClient.class,
            new ProtoClient(services),
            List.of(
                new ErrorMappingInterceptor(apiErrors), new TracingInterceptor(tracer, metrics)));
    this.seeder =
        new DemoSeeder(users, new JdbcSeedRepository(tx, errors, cursors), hasher, activity, tx);
  }

  /** The frontend entry point. */
  public UmsClient client() {
    return client;
  }

  /** The demo seeder. */
  public DemoSeeder seeder() {
    return seeder;
  }
}
