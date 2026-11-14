package dev.interview.commandprocessor.processing;

import dev.interview.commandprocessor.command.Command;
import dev.interview.commandprocessor.model.UserId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Timeout(10)
class AsyncCommandProcessorTest {

    private final ByteArrayOutputStream err = new ByteArrayOutputStream();

    @Test
    void submitDoesNotWaitForTheCommandToBeHandled() throws InterruptedException {
        final CountDownLatch handlerStarted = new CountDownLatch(1);
        final CountDownLatch releaseHandler = new CountDownLatch(1);
        final Consumer<Command> blockingHandler = command -> {
            handlerStarted.countDown();
            awaitUninterruptibly(releaseHandler);
        };

        try (AsyncCommandProcessor processor = newProcessor(blockingHandler)) {
            processor.submit(new Command.Stats());

            // the handler is still blocked here, so submit() did not wait for it
            assertThat(handlerStarted.await(5, TimeUnit.SECONDS))
                    .isTrue();
            releaseHandler.countDown();
        }
    }

    @Test
    void handlesCommandsOnAnotherThread() {
        final List<String> handlingThreads = new CopyOnWriteArrayList<>();

        try (AsyncCommandProcessor processor =
                     newProcessor(command -> handlingThreads.add(Thread.currentThread().getName()))) {
            processor.submit(new Command.Stats());
        }

        assertThat(handlingThreads)
                .containsExactly("command-worker");
    }

    @Test
    void closeHandlesEverythingQueuedInSubmissionOrder() {
        final List<Command> handled = new CopyOnWriteArrayList<>();
        final List<Command> submitted = IntStream.range(0, 1_000)
                .<Command>mapToObj(i -> new Command.DataModify(new UserId(i)))
                .toList();

        try (AsyncCommandProcessor processor = newProcessor(handled::add)) {
            submitted.forEach(processor::submit);
        }

        assertThat(handled)
                .containsExactlyElementsOf(submitted);
    }

    @Test
    void failingCommandIsReportedAndDoesNotStopTheNextOnes() {
        final List<Command> handled = new CopyOnWriteArrayList<>();
        final Consumer<Command> handler = command -> {
            if (command instanceof Command.Stats) {
                throw new IllegalStateException("database is down");
            }
            handled.add(command);
        };
        final Command.Login login = new Command.Login(new UserId(1));

        try (AsyncCommandProcessor processor = newProcessor(handler)) {
            processor.submit(new Command.Stats());
            processor.submit(login);
        }

        assertThat(handled)
                .containsExactly(login);
        assertThat(err.toString(StandardCharsets.UTF_8))
                .contains("database is down");
    }

    @Test
    void rejectsCommandsAfterClose() {
        final AsyncCommandProcessor processor = newProcessor(command -> { });
        processor.close();

        assertThatThrownBy(() -> processor.submit(new Command.Stats()))
                .isInstanceOf(RejectedExecutionException.class);
    }

    private AsyncCommandProcessor newProcessor(final Consumer<Command> handler) {
        return new AsyncCommandProcessor(handler, new PrintStream(err, true, StandardCharsets.UTF_8));
    }

    private static void awaitUninterruptibly(final CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
