package dev.interview.commandprocessor.persistence;

import dev.interview.commandprocessor.model.UserId;

import java.util.Map;

/** modificationsPerUser contains every user with at least one modification, logged in or not. */
public record Statistics(int loggedInUsers, Map<UserId, Long> modificationsPerUser) {

    public int usersModifyingData() {
        return modificationsPerUser.size();
    }
}
