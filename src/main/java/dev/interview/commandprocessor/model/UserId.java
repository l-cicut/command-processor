package dev.interview.commandprocessor.model;

public record UserId(int value) {

    public UserId {
        if (value < 0) {
            throw new IllegalArgumentException("user id must not be negative, but got " + value);
        }
    }

    public static UserId parse(final String text) {
        try {
            return new UserId(Integer.parseInt(text));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("user id must be an integer, but got '%s'".formatted(text), e);
        }
    }

    @Override
    public String toString() {
        return Integer.toString(value);
    }
}
