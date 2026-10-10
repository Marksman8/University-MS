package edu.ums.platform.invoke;

import edu.ums.api.annotation.Writes;

/** Test-only facade. */
interface DemoApi {

  String read(String input);

  @Writes
  String write(String input);
}
