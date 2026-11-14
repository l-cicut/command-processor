package dev.interview.commandprocessor.command;

import dev.interview.commandprocessor.model.UserId;

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Each constant knows how to build its command from the text between the brackets.
 * To add a command, add a constant here and a case in CommandHandler.
 */
public enum CommandType {

    LOGIN(withUserId(Command.Login::new)),
    LOGOUT(withUserId(Command.Logout::new)),
    DATA_MODIFY(withUserId(Command.DataModify::new)),
    STATS(withoutArgument(Command.Stats::new)),
    EXIT(withoutArgument(Command.Exit::new));

    private static final Map<String, CommandType> BY_NAME = Arrays.stream(values())
            .collect(Collectors.toUnmodifiableMap(Enum::name, Function.identity()));

    private final Function<String, Command> factory;

    CommandType(final Function<String, Command> factory) {
        this.factory = factory;
    }

    public static Optional<CommandType> fromName(final String name) {
        return Optional.ofNullable(BY_NAME.get(name.toUpperCase(Locale.ROOT)));
    }

    public ParseResult parse(final String argument) {
        try {
            return new ParseResult.Success(factory.apply(argument));
        } catch (IllegalArgumentException e) {
            return new ParseResult.Failure(name() + ": " + e.getMessage());
        }
    }

    private static Function<String, Command> withUserId(final Function<UserId, Command> constructor) {
        return argument -> {
            if (argument.isEmpty()) {
                throw new IllegalArgumentException("a user id is required");
            }
            return constructor.apply(UserId.parse(argument));
        };
    }

    private static Function<String, Command> withoutArgument(final Supplier<Command> constructor) {
        return argument -> {
            if (!argument.isEmpty()) {
                throw new IllegalArgumentException("takes no argument, but got '%s'".formatted(argument));
            }
            return constructor.get();
        };
    }
}
