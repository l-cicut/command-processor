package dev.interview.commandprocessor.persistence;

import dev.interview.commandprocessor.model.UserId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcSessionRepositoryTest {

    private static final UserId USER = new UserId(1);
    private static final UserId OTHER_USER = new UserId(2);

    private Database database;
    private JdbcSessionRepository repository;

    @BeforeEach
    void setUp() {
        // fresh database for every test
        database = Database.inMemory("test-" + UUID.randomUUID());
        repository = new JdbcSessionRepository(database.dataSource());
        repository.initializeSchema();
    }

    @AfterEach
    void tearDown() {
        database.close();
    }

    @Test
    void startsEmpty() {
        assertThat(repository.statistics())
                .isEqualTo(new Statistics(0, Map.of()));
    }

    @Test
    void logsUserIn() {
        assertThat(repository.login(USER))
                .isTrue();

        assertThat(repository.statistics().loggedInUsers())
                .isEqualTo(1);
    }

    @Test
    void refusesSecondLoginOfSameUser() {
        repository.login(USER);

        assertThat(repository.login(USER))
                .isFalse();
        assertThat(repository.statistics().loggedInUsers())
                .isEqualTo(1);
    }

    @Test
    void logsUserOut() {
        repository.login(USER);

        assertThat(repository.logout(USER))
                .isTrue();
        assertThat(repository.statistics().loggedInUsers())
                .isZero();
    }

    @Test
    void refusesLogoutOfUserWhoIsNotLoggedIn() {
        assertThat(repository.logout(USER))
                .isFalse();
    }

    @Test
    void allowsLoginAgainAfterLogout() {
        repository.login(USER);
        repository.logout(USER);

        assertThat(repository.login(USER))
                .isTrue();
    }

    @Test
    void recordsEveryModificationAsSeparateEntry() {
        repository.login(USER);

        assertThat(repository.recordModification(USER))
                .isTrue();
        assertThat(repository.recordModification(USER))
                .isTrue();
        assertThat(repository.recordModification(USER))
                .isTrue();

        assertThat(repository.statistics().modificationsPerUser())
                .containsExactly(Map.entry(USER, 3L));
    }

    @Test
    void ignoresModificationOfUserWhoIsNotLoggedIn() {
        assertThat(repository.recordModification(USER))
                .isFalse();

        assertThat(repository.statistics())
                .isEqualTo(new Statistics(0, Map.of()));
    }

    @Test
    void countsOnlyUsersWithModificationsAsModifying() {
        repository.login(USER);
        repository.login(OTHER_USER);
        repository.recordModification(OTHER_USER);

        final Statistics statistics = repository.statistics();

        assertThat(statistics.loggedInUsers())
                .isEqualTo(2);
        assertThat(statistics.usersModifyingData())
                .isEqualTo(1);
        assertThat(statistics.modificationsPerUser())
                .containsExactly(Map.entry(OTHER_USER, 1L));
    }

    @Test
    void modificationsStayAfterLogout() {
        repository.login(USER);
        repository.recordModification(USER);
        repository.logout(USER);

        assertThat(repository.statistics())
                .isEqualTo(new Statistics(0, Map.of(USER, 1L)));
    }

    @Test
    void modificationsAddUpAcrossSessions() {
        repository.login(USER);
        repository.recordModification(USER);
        repository.logout(USER);
        repository.login(USER);
        repository.recordModification(USER);

        assertThat(repository.statistics())
                .isEqualTo(new Statistics(1, Map.of(USER, 2L)));
    }
}
