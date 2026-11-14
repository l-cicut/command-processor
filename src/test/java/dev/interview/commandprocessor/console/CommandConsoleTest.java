package dev.interview.commandprocessor.console;

import dev.interview.commandprocessor.command.CommandParser;
import dev.interview.commandprocessor.persistence.Database;
import dev.interview.commandprocessor.persistence.JdbcSessionRepository;
import dev.interview.commandprocessor.processing.AsyncCommandProcessor;
import dev.interview.commandprocessor.processing.CommandHandler;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// whole application: text in, text out, real H2
class CommandConsoleTest {

    private final ByteArrayOutputStream out = new ByteArrayOutputStream();
    private final ByteArrayOutputStream err = new ByteArrayOutputStream();

    @Test
    void processesCommandsInInputOrder() {
        run("""
                LOGIN(1)
                LOGIN(2)
                DATA_MODIFY(1)
                DATA_MODIFY(1)
                STATS()
                LOGOUT(1)
                STATS()
                EXIT()
                """);

        assertThat(lines(out))
                .containsExactly(
                        "User 1 logged in.",
                        "User 2 logged in.",
                        "Data modification of user 1 recorded.",
                        "Data modification of user 1 recorded.",
                        "Logged-in users: 2",
                        "Users modifying data: 1",
                        "  1: 2 modification(s)",
                        "User 1 logged out.",
                        "Logged-in users: 1",
                        "Users modifying data: 1",
                        "  1: 2 modification(s)",
                        "Shutting down.");
        assertThat(lines(err))
                .isEmpty();
    }

    @Test
    void ignoresCommandsThatBreakTheRules() {
        run("""
                LOGIN(1)
                LOGIN(1)
                LOGOUT(2)
                DATA_MODIFY(2)
                EXIT()
                """);

        assertThat(lines(out))
                .containsExactly(
                        "User 1 logged in.",
                        "LOGIN ignored: user 1 is already logged in.",
                        "LOGOUT ignored: user 2 is not logged in.",
                        "DATA_MODIFY ignored: user 2 is not logged in.",
                        "Shutting down.");
    }

    @Test
    void invalidCommandsAreReportedAndDoNotStopTheApplication() {
        run("""
                nonsense
                FLY(1)
                LOGIN()

                LOGIN(1)
                EXIT()
                """);

        assertThat(lines(err))
                .hasSize(3)
                .allMatch(line -> line.startsWith("Invalid command: "));
        assertThat(lines(out))
                .containsExactly("User 1 logged in.", "Shutting down.");
    }

    @Test
    void stopsReadingAfterExit() {
        run("""
                EXIT()
                LOGIN(1)
                """);

        assertThat(lines(out))
                .containsExactly("Shutting down.");
    }

    @Test
    void endOfInputFinishesQueuedCommandsWithoutExit() {
        run("""
                LOGIN(1)
                DATA_MODIFY(1)
                """);

        assertThat(lines(out))
                .containsExactly(
                        "User 1 logged in.",
                        "Data modification of user 1 recorded.");
    }

    // same wiring as in App
    private void run(final String input) {
        final PrintStream outStream = new PrintStream(out, true, StandardCharsets.UTF_8);
        final PrintStream errStream = new PrintStream(err, true, StandardCharsets.UTF_8);

        try (Database database = Database.inMemory("test-" + UUID.randomUUID())) {
            final JdbcSessionRepository repository =
                    new JdbcSessionRepository(database.dataSource());
            repository.initializeSchema();

            try (AsyncCommandProcessor processor =
                         new AsyncCommandProcessor(new CommandHandler(repository, outStream), errStream)) {
                new CommandConsole(new BufferedReader(new StringReader(input)), errStream, new CommandParser(), processor)
                        .run();
            }
        }
    }

    private static List<String> lines(final ByteArrayOutputStream stream) {
        return stream.toString(StandardCharsets.UTF_8).lines().toList();
    }
}
