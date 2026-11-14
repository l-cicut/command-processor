package dev.interview.commandprocessor.command;

import dev.interview.commandprocessor.model.UserId;

public sealed interface Command {

    record Login(UserId userId) implements Command {
    }

    record Logout(UserId userId) implements Command {
    }

    record DataModify(UserId userId) implements Command {
    }

    record Stats() implements Command {
    }

    record Exit() implements Command {
    }
}
