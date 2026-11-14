package dev.interview.commandprocessor.command;

// Wrong input is nothing exceptional here, so the parser returns it instead of throwing.
public sealed interface ParseResult {

    record Success(Command command) implements ParseResult {
    }

    record Failure(String reason) implements ParseResult {
    }
}
