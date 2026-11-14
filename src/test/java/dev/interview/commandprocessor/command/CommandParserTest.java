package dev.interview.commandprocessor.command;

import dev.interview.commandprocessor.model.UserId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class CommandParserTest {

    private final CommandParser parser = new CommandParser();

    static Stream<Arguments> validLines() {
        final UserId user = new UserId(42);
        return Stream.of(
                Arguments.of("LOGIN(42)", new Command.Login(user)),
                Arguments.of("LOGOUT(42)", new Command.Logout(user)),
                Arguments.of("DATA_MODIFY(42)", new Command.DataModify(user)),
                Arguments.of("STATS()", new Command.Stats()),
                Arguments.of("EXIT()", new Command.Exit()),
                Arguments.of("LOGIN(0)", new Command.Login(new UserId(0))),
                Arguments.of("LOGIN(2147483647)", new Command.Login(new UserId(Integer.MAX_VALUE))),
                Arguments.of("login(42)", new Command.Login(user)),
                Arguments.of("  LOGIN( 42 )  ", new Command.Login(user)));
    }

    @ParameterizedTest
    @MethodSource("validLines")
    void parsesValidLine(final String line, final Command expected) {
        assertThat(parser.parse(line))
                .isEqualTo(new ParseResult.Success(expected));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {
            "",
            "   ",
            "LOGIN",
            "LOGIN 42",
            "LOGIN (42)",
            "LOGIN(42",
            "LOGIN(42) trailing",
            "FLY(42)",
            "LOGIN()",
            "LOGIN(lubos)",
            "LOGIN(-1)",
            "LOGIN(1.5)",
            "LOGIN(4 2)",
            "LOGIN(2147483648)",
            "LOGIN(42;DROP TABLE user_session)",
            "STATS(42)",
            "EXIT(1)"
    })
    void rejectsInvalidLine(final String line) {
        assertThat(parser.parse(line))
                .isInstanceOf(ParseResult.Failure.class);
    }

    // The messages below are what the user sees, so they are checked word for word.

    @Test
    void reportsEmptyInput() {
        assertThat(parser.parse("   "))
                .isInstanceOfSatisfying(ParseResult.Failure.class, failure ->
                        assertThat(failure.reason())
                                .isEqualTo("empty input"));
    }

    @Test
    void reportsWrongFormat() {
        assertThat(parser.parse("LOGIN 42"))
                .isInstanceOfSatisfying(ParseResult.Failure.class, failure ->
                        assertThat(failure.reason())
                                .isEqualTo("'LOGIN 42' is not in the form COMMAND(argument), e.g. LOGIN(1) or STATS()"));
    }

    @Test
    void reportsUnknownCommand() {
        assertThat(parser.parse("FLY(42)"))
                .isInstanceOfSatisfying(ParseResult.Failure.class, failure ->
                        assertThat(failure.reason())
                                .isEqualTo("unknown command 'FLY', supported: [LOGIN, LOGOUT, DATA_MODIFY, STATS, EXIT]"));
    }

    @Test
    void reportsMissingUserId() {
        assertThat(parser.parse("LOGIN()"))
                .isInstanceOfSatisfying(ParseResult.Failure.class, failure ->
                        assertThat(failure.reason())
                                .isEqualTo("LOGIN: a user id is required"));
    }

    @Test
    void reportsUserIdThatIsNotAnInteger() {
        assertThat(parser.parse("LOGIN(lubos)"))
                .isInstanceOfSatisfying(ParseResult.Failure.class, failure ->
                        assertThat(failure.reason())
                                .isEqualTo("LOGIN: user id must be an integer, but got 'lubos'"));
    }

    @Test
    void reportsNegativeUserId() {
        assertThat(parser.parse("LOGIN(-1)"))
                .isInstanceOfSatisfying(ParseResult.Failure.class, failure ->
                        assertThat(failure.reason())
                                .isEqualTo("LOGIN: user id must not be negative, but got -1"));
    }

    @Test
    void reportsArgumentForCommandThatTakesNone() {
        assertThat(parser.parse("STATS(42)"))
                .isInstanceOfSatisfying(ParseResult.Failure.class, failure ->
                        assertThat(failure.reason())
                                .isEqualTo("STATS: takes no argument, but got '42'"));
    }
}
