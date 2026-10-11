package edu.ums.app.proto;

import edu.ums.platform.db.TransactionRunner;
import edu.ums.platform.error.ApiErrorTranslator;
import edu.ums.platform.invoke.FacadeProxyFactory;
import edu.ums.platform.invoke.Interceptor;
import edu.ums.proto.att.service.AttendanceService;
import edu.ums.proto.audit.service.ActivityRecorder;
import edu.ums.proto.auth.service.AuthService;
import edu.ums.proto.dev.service.DevService;
import edu.ums.proto.marks.service.MarksService;
import edu.ums.proto.marks.service.ResultsService;
import edu.ums.proto.reg.service.RegistrationService;
import edu.ums.proto.term.service.TermService;
import java.util.List;

/** The stateless services a session needs to build its facades. */
record ProtoServices(
    AuthService auth,
    ActivityRecorder activity,
    TermService terms,
    RegistrationService registration,
    AttendanceService attendance,
    MarksService marks,
    ResultsService results,
    DevService dev,
    FacadeProxyFactory facades,
    List<Interceptor> chain,
    ApiErrorTranslator apiErrors,
    TransactionRunner tx) {

  ProtoServices {
    chain = List.copyOf(chain);
  }
}
