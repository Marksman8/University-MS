package edu.ums.core.archfixturea.service;

import edu.ums.core.facade.ArchFixtureFacade;

/** Violates SERVICES_DO_NOT_DEPEND_ON_FACADES. */
public class ServiceUsingFacade {
  ArchFixtureFacade facade;
}
