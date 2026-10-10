package edu.ums.core.archfixturea.domain;

import edu.ums.core.archfixturea.repository.RepositoryUsingService;

/** Violates LAYER_DIRECTION: a domain class calling a repository. */
public class DomainUsingRepository {
  RepositoryUsingService repository;
}
