package dev.interview.commandprocessor.persistence;

import dev.interview.commandprocessor.model.UserId;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * No locks here. Every change is a single SQL statement on its own connection, and the
 * primary key of user_session makes a double login impossible.
 * Modifications are kept as history, logout does not delete them.
 */
public final class JdbcSessionRepository implements SessionRepository {

    private static final String CREATE_USER_SESSION = """
            CREATE TABLE IF NOT EXISTS user_session (
                user_id      INTEGER   PRIMARY KEY,
                logged_in_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
            )""";

    private static final String CREATE_DATA_MODIFICATION = """
            CREATE TABLE IF NOT EXISTS data_modification (
                id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                user_id     INTEGER   NOT NULL,
                modified_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
            )""";

    private static final String INSERT_SESSION = "INSERT INTO user_session (user_id) VALUES (?)";

    private static final String DELETE_SESSION = "DELETE FROM user_session WHERE user_id = ?";

    // inserts nothing when the user has no session, so the check and the insert are one statement
    private static final String INSERT_MODIFICATION = """
            INSERT INTO data_modification (user_id)
            SELECT user_id FROM user_session WHERE user_id = ?""";

    private static final String COUNT_SESSIONS = "SELECT COUNT(*) FROM user_session";

    private static final String COUNT_MODIFICATIONS_PER_USER = """
            SELECT user_id, COUNT(*) AS modifications
            FROM data_modification
            GROUP BY user_id
            ORDER BY user_id""";

    private final DataSource dataSource;

    public JdbcSessionRepository(final DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public void initializeSchema() {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute(CREATE_USER_SESSION);
            statement.execute(CREATE_DATA_MODIFICATION);
        } catch (SQLException e) {
            throw new PersistenceException("Could not create the tables", e);
        }
    }

    @Override
    public boolean login(final UserId userId) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(INSERT_SESSION)) {
            statement.setInt(1, userId.value());
            statement.executeUpdate();
            return true;
        } catch (SQLIntegrityConstraintViolationException e) {
            // duplicate primary key, the user is already logged in
            return false;
        } catch (SQLException e) {
            throw new PersistenceException("Could not log in user " + userId, e);
        }
    }

    @Override
    public boolean logout(final UserId userId) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(DELETE_SESSION)) {
            statement.setInt(1, userId.value());
            return statement.executeUpdate() == 1;
        } catch (SQLException e) {
            throw new PersistenceException("Could not log out user " + userId, e);
        }
    }

    @Override
    public boolean recordModification(final UserId userId) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(INSERT_MODIFICATION)) {
            statement.setInt(1, userId.value());
            return statement.executeUpdate() == 1;
        } catch (SQLException e) {
            throw new PersistenceException("Could not record a modification of user " + userId, e);
        }
    }

    @Override
    public Statistics statistics() {
        try (Connection connection = dataSource.getConnection()) {
            return new Statistics(countSessions(connection), countModificationsPerUser(connection));
        } catch (SQLException e) {
            throw new PersistenceException("Could not read statistics", e);
        }
    }

    private static int countSessions(final Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(COUNT_SESSIONS);
                ResultSet rows = statement.executeQuery()) {
            rows.next();
            return rows.getInt(1);
        }
    }

    private static Map<UserId, Long> countModificationsPerUser(final Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(COUNT_MODIFICATIONS_PER_USER);
                ResultSet rows = statement.executeQuery()) {
            final Map<UserId, Long> modificationsPerUser = new LinkedHashMap<>();
            while (rows.next()) {
                modificationsPerUser.put(new UserId(rows.getInt("user_id")), rows.getLong("modifications"));
            }
            return modificationsPerUser;
        }
    }
}
