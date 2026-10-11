package edu.ums.proto.term.service;

import edu.ums.api.proto.TermView;
import edu.ums.proto.shared.Actor;
import edu.ums.proto.term.domain.TermRow;
import edu.ums.proto.term.domain.TermState;

/** Prototype term lifecycle. Runs in the caller's transaction. */
public interface TermService {

  /** The current term (NOT_FOUND if none). */
  TermRow current();

  /** The current term as a view. */
  TermView currentView();

  /** Moves the current term to {@code target} through the state machine. */
  TermView advance(Actor admin, TermState target);
}
