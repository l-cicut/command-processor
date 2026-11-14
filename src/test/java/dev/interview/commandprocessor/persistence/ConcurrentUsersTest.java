package dev.interview.commandprocessor.persistence;

import dev.interview.commandprocessor.model.UserId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

// many users at once, the repository is called from many threads
@Timeout(60)
class ConcurrentUsersTest {

    private static final int THREADS = 16;

    private Database database;
    private JdbcSessionRepository repository;
    private ExecutorService threads;

    @BeforeEach
    void setUp() {
        database = Database.inMemory("test-" + UUID.randomUUID());
        repository = new JdbcSessionRepository(database.dataSource());
        repository.initializeSchema();
        threads = Executors.newFixedThreadPool(THREADS);
    }

    @AfterEach
    void tearDown() {
        threads.shutdownNow();
        database.close();
    }

    @Test
    void onlyOneOfManySimultaneousLoginsOfTheSameUserSucceeds() throws Exception {
        final UserId user = new UserId(1);

        final List<Boolean> results = runSimultaneously(THREADS, thread -> repository.login(user));

        assertThat(results)
                .filteredOn(Boolean::booleanValue)
                .hasSize(1);
        assertThat(repository.statistics().loggedInUsers())
                .isEqualTo(1);
    }

    @Test
    void onlyOneOfManySimultaneousLogoutsOfTheSameUserSucceeds() throws Exception {
        final UserId user = new UserId(1);
        repository.login(user);

        final List<Boolean> results = runSimultaneously(THREADS, thread -> repository.logout(user));

        assertThat(results)
                .filteredOn(Boolean::booleanValue)
                .hasSize(1);
        assertThat(repository.statistics().loggedInUsers())
                .isZero();
    }

    @Test
    void noModificationIsLostWhenManyUsersWorkAtOnce() throws Exception {
        final int modificationsPerUser = 200;

        // every thread is one user
        runSimultaneously(THREADS, thread -> {
            final UserId user = new UserId(thread);
            repository.login(user);
            for (int i = 0; i < modificationsPerUser; i++) {
                repository.recordModification(user);
            }
            return true;
        });

        final Statistics statistics = repository.statistics();
        assertThat(statistics.loggedInUsers())
                .isEqualTo(THREADS);
        assertThat(statistics.usersModifyingData())
                .isEqualTo(THREADS);
        assertThat(statistics.modificationsPerUser().values())
                .containsOnly((long) modificationsPerUser);
    }

    @Test
    void noModificationOfTheSameUserIsLostAcrossThreads() throws Exception {
        final UserId user = new UserId(1);
        final int modificationsPerThread = 100;
        repository.login(user);

        runSimultaneously(THREADS, thread -> {
            for (int i = 0; i < modificationsPerThread; i++) {
                repository.recordModification(user);
            }
            return true;
        });

        assertThat(repository.statistics().modificationsPerUser())
                .containsEntry(user, (long) THREADS * modificationsPerThread);
    }

    @Test
    void racingLoginsLogoutsAndModificationsNeitherFailNorLoseEntries() throws Exception {
        final UserId user = new UserId(1);
        final int rounds = 100;

        // half of the threads log the user in and out, the other half modify data
        final List<Integer> recorded = runSimultaneously(THREADS, thread -> {
            int successfulModifications = 0;
            for (int i = 0; i < rounds; i++) {
                if (thread % 2 == 0) {
                    repository.login(user);
                    repository.logout(user);
                } else if (repository.recordModification(user)) {
                    successfulModifications++;
                }
            }
            return successfulModifications;
        });

        // No thread may fail (runSimultaneously rethrows), and what was reported as recorded
        // must be exactly what is stored. Modifications are kept after logout, so there is
        // nothing else to check here; atomicity of login is covered by the tests above.
        final long reported = recorded.stream().mapToLong(Integer::longValue).sum();
        final Statistics statistics = repository.statistics();
        assertThat(statistics.loggedInUsers())
                .isZero();
        assertThat(statistics.modificationsPerUser().getOrDefault(user, 0L))
                .isEqualTo(reported);
    }

    // all threads wait for the start latch, so they really run at the same time
    private <T> List<T> runSimultaneously(final int count, final ThreadTask<T> task)
            throws InterruptedException, ExecutionException {
        final CountDownLatch allReady = new CountDownLatch(count);
        final CountDownLatch start = new CountDownLatch(1);

        final List<Future<T>> futures = IntStream.range(0, count)
                .mapToObj(thread -> threads.submit((Callable<T>) () -> {
                    allReady.countDown();
                    start.await();
                    return task.run(thread);
                }))
                .toList();

        allReady.await();
        start.countDown();

        final List<T> results = new ArrayList<>(count);
        for (final Future<T> future : futures) {
            // get() rethrows what the thread threw
            results.add(future.get());
        }
        return results;
    }

    @FunctionalInterface
    private interface ThreadTask<T> {
        T run(int thread) throws Exception;
    }
}
