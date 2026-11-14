package dev.interview.commandprocessor;

import dev.interview.commandprocessor.command.CommandParser;
import dev.interview.commandprocessor.console.CommandConsole;
import dev.interview.commandprocessor.persistence.Database;
import dev.interview.commandprocessor.persistence.JdbcSessionRepository;
import dev.interview.commandprocessor.processing.AsyncCommandProcessor;
import dev.interview.commandprocessor.processing.CommandHandler;

import java.io.BufferedReader;
import java.io.InputStreamReader;

public final class App {

    public static void main(final String[] args) {
        try (Database database = Database.inMemory("command-processor")) {
            final JdbcSessionRepository repository = new JdbcSessionRepository(database.dataSource());
            repository.initializeSchema();

            final CommandHandler handler = new CommandHandler(repository, System.out);
            final BufferedReader stdin = new BufferedReader(new InputStreamReader(System.in));

            // the processor is closed before the database, so queued commands can still finish
            try (AsyncCommandProcessor processor = new AsyncCommandProcessor(handler, System.err)) {
                new CommandConsole(stdin, System.err, new CommandParser(), processor).run();
            }
        }
    }
}
