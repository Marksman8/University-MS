package edu.ums.proto.term.domain;

/** A term row with its optimistic version. */
public record TermRow(long id, String name, TermState state, long version) {}
