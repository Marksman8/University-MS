package edu.ums.core.archfixturea.repository;

import edu.ums.core.archfixturea.service.CompliantService;

/** Violates LAYER_DIRECTION: a repository calling a service. */
public class RepositoryUsingService {
  CompliantService service;
}
