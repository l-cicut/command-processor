package dev.interview.commandprocessor.persistence;

import org.h2.jdbcx.JdbcConnectionPool;

import javax.sql.DataSource;

/** Embedded H2 with a connection pool. The only class that knows about H2. */
public final class Database implements AutoCloseable {

    private final JdbcConnectionPool connectionPool;

    private Database(final JdbcConnectionPool connectionPool) {
        this.connectionPool = connectionPool;
    }

    public static Database inMemory(final String name) {
        // DB_CLOSE_DELAY=-1 keeps the data until the JVM exits, not only while a connection is open
        return new Database(JdbcConnectionPool.create("jdbc:h2:mem:" + name + ";DB_CLOSE_DELAY=-1", "sa", ""));
    }

    public DataSource dataSource() {
        return connectionPool;
    }

    @Override
    public void close() {
        connectionPool.dispose();
    }
}
