package dev.interview.commandprocessor.processing;

import dev.interview.commandprocessor.command.Command;

import java.io.PrintStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * submit() only puts the command into the executor's queue, a worker thread runs it later.
 * One worker only, so commands run in the order they came in (LOGIN before DATA_MODIFY).
 */
public final class AsyncCommandProcessor implements AutoCloseable {

    private static final long SHUTDOWN_TIMEOUT_SECONDS = 30;

    private final Consumer<Command> handler;
    private final PrintStream err;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(
            Thread.ofPlatform().name("command-worker").factory());

    public AsyncCommandProcessor(final Consumer<Command> handler, final PrintStream err) {
        this.handler = handler;
        this.err = err;
    }

    public void submit(final Command command) {
        worker.execute(() -> {
            try {
                handler.accept(command);
            } catch (RuntimeException e) {
                // one failed command must not stop the others
                err.printf("Command %s failed: %s%n", command, describe(e));
            }
        });
    }

    // the message of PersistenceException alone does not say what went wrong in the database
    private static String describe(final RuntimeException e) {
        if (e.getCause() == null) {
            return e.getMessage();
        }
        return e.getMessage() + " (" + e.getCause().getMessage() + ")";
    }

    /** Waits until everything already submitted is done, but not longer than the timeout. */
    @Override
    public void close() {
        worker.shutdown();
        try {
            if (!worker.awaitTermination(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                final int dropped = worker.shutdownNow().size();
                err.printf("Shutdown timed out, %d command(s) were not processed.%n", dropped);
            }
        } catch (InterruptedException e) {
            worker.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
