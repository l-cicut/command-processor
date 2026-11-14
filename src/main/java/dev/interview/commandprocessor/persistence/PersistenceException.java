package dev.interview.commandprocessor.persistence;

public class PersistenceException extends RuntimeException {

    public PersistenceException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
