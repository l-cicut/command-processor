package dev.interview.commandprocessor.command;

import java.util.Arrays;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CommandParser {

    // (\w+) - command name
    // \(    - opening bracket
    // (.*)  - argument
    // \)    - closing bracket
    private static final Pattern COMMAND_PATTERN = Pattern.compile("(\\w+)" + "\\(" + "(.*)" + "\\)");

    public ParseResult parse(final String line) {
        if (line == null || line.isBlank()) {
            return new ParseResult.Failure("empty input");
        }

        final Matcher matcher = COMMAND_PATTERN.matcher(line.strip());
        if (!matcher.matches()) {
            return new ParseResult.Failure(
                    "'%s' is not in the form COMMAND(argument), e.g. LOGIN(1) or STATS()".formatted(line.strip()));
        }

        final String name = matcher.group(1);
        final Optional<CommandType> type = CommandType.fromName(name);
        if (type.isEmpty()) {
            return new ParseResult.Failure(
                    "unknown command '%s', supported: %s".formatted(name, Arrays.toString(CommandType.values())));
        }

        final String argument = matcher.group(2).strip();
        return type.get().parse(argument);
    }
}
