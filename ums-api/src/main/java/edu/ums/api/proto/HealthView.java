package edu.ums.api.proto;

import java.util.List;

/** Health status and one line per probe (prototype). */
public record HealthView(String status, List<String> probes) {}
