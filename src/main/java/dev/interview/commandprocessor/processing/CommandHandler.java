package dev.interview.commandprocessor.processing;

import dev.interview.commandprocessor.command.Command;
import dev.interview.commandprocessor.model.UserId;
import dev.interview.commandprocessor.persistence.SessionRepository;
import dev.interview.commandprocessor.persistence.Statistics;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public final class CommandHandler implements Consumer<Command> {

    private final SessionRepository repository;
    private final PrintStream out;

    public CommandHandler(final SessionRepository repository, final PrintStream out) {
        this.repository = repository;
        this.out = out;
    }

    @Override
    public void accept(final Command command) {
        // no default, so a new command does not compile until it is handled here
        switch (command) {
            case Command.Login login -> login(login.userId());
            case Command.Logout logout -> logout(logout.userId());
            case Command.DataModify dataModify -> modifyData(dataModify.userId());
            case Command.Stats stats -> printStatistics(repository.statistics());
            case Command.Exit exit -> out.println("Shutting down.");
        }
    }

    private void login(final UserId userId) {
        if (!repository.login(userId)) {
            out.printf("LOGIN ignored: user %s is already logged in.%n", userId);
            return;
        }
        out.printf("User %s logged in.%n", userId);
    }

    private void logout(final UserId userId) {
        if (!repository.logout(userId)) {
            out.printf("LOGOUT ignored: user %s is not logged in.%n", userId);
            return;
        }
        out.printf("User %s logged out.%n", userId);
    }

    private void modifyData(final UserId userId) {
        if (!repository.recordModification(userId)) {
            out.printf("DATA_MODIFY ignored: user %s is not logged in.%n", userId);
            return;
        }
        out.printf("Data modification of user %s recorded.%n", userId);
    }

    private void printStatistics(final Statistics statistics) {
        final List<String> lines = new ArrayList<>();
        lines.add(String.format("Logged-in users: %s", statistics.loggedInUsers()));
        lines.add(String.format("Users modifying data: %s", statistics.usersModifyingData()));
        statistics.modificationsPerUser().forEach((userId, count) ->
                lines.add(String.format("  %s: %d modification(s)", userId, count)));

        out.println(String.join(System.lineSeparator(), lines));
    }
}
