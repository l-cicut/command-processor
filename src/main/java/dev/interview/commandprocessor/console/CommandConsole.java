package dev.interview.commandprocessor.console;

import dev.interview.commandprocessor.command.Command;
import dev.interview.commandprocessor.command.CommandParser;
import dev.interview.commandprocessor.command.ParseResult;
import dev.interview.commandprocessor.processing.AsyncCommandProcessor;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;

public final class CommandConsole {

    private final BufferedReader in;
    private final PrintStream err;
    private final CommandParser parser;
    private final AsyncCommandProcessor processor;

    public CommandConsole(final BufferedReader in, final PrintStream err, final CommandParser parser,
            final AsyncCommandProcessor processor) {
        this.in = in;
        this.err = err;
        this.parser = parser;
        this.processor = processor;
    }

    /** Reads until EXIT() or the end of the input. */
    public void run() {
        try {
            String line;
            while ((line = in.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                switch (parser.parse(line)) {
                    case ParseResult.Failure(String reason) -> err.println("Invalid command: " + reason);
                    case ParseResult.Success(Command command) -> {
                        processor.submit(command);
                        if (command instanceof Command.Exit) {
                            return;
                        }
                    }
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read the input", e);
        }
    }
}
