package edu.ums.core.archfixturea.service;

import java.sql.Connection;

/** Violates JDBC_ONLY_IN_DATA_ACCESS: a service touching java.sql. */
public class ServiceUsingJdbc {
  Connection connection;
}
