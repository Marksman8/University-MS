package edu.ums.api.archfixture;

import edu.ums.platform.config.Secret;

/** Violates API_DEPENDS_ON_NOTHING: ums-api code depending on ums-platform. */
public class ApiUsingPlatform {
  Secret leaked;
}
