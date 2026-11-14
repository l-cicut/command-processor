package dev.interview.commandprocessor.persistence;

import dev.interview.commandprocessor.model.UserId;

/**
 * There is no isLoggedIn() on purpose. Each method does the check and the change in one step
 * and returns whether it happened, otherwise two threads could both pass the check.
 */
public interface SessionRepository {

    /** @return false if the user is already logged in */
    boolean login(UserId userId);

    /** The user's modifications are kept. @return false if the user is not logged in */
    boolean logout(UserId userId);

    /** @return false if the user is not logged in */
    boolean recordModification(UserId userId);

    Statistics statistics();
}
