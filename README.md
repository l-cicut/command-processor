# Command Processor

A console application. It reads commands from standard input, one per line, and a
background thread writes them into a database.

Plain Java 21 without a framework. The only runtime dependency is H2, used through JDBC.

## Run

You need JDK 21 and Maven.

```bash
mvn package
java -jar target/command-processor.jar
```

Then type commands:

```
LOGIN(1)
User 1 logged in.
DATA_MODIFY(1)
Data modification of user 1 recorded.
DATA_MODIFY(2)
DATA_MODIFY ignored: user 2 is not logged in.
STATS()
Logged-in users: 1
Users modifying data: 1
  1: 1 modification(s)
EXIT()
Shutting down.
```

## Commands

| Command | What it does |
|---|---|
| `LOGIN(id)` | Logs the user in. Ignored if the user is already logged in. |
| `LOGOUT(id)` | Logs the user out. Ignored if the user is not logged in. |
| `DATA_MODIFY(id)` | Records one modification. Ignored if the user is not logged in. |
| `STATS()` | Prints the number of logged-in users and the modifications per user. |
| `EXIT()` | Finishes the commands already entered and stops. |

Results go to STDOUT. A line that is not a valid command is reported on STDERR and
skipped; the application keeps running. End of input stops it the same way as `EXIT()`.

## How it works

```
STDIN -> CommandConsole -> CommandParser -> AsyncCommandProcessor -> CommandHandler -> SessionRepository -> H2
```

The main thread only reads and parses lines. Each valid command goes into the queue of
a single worker thread, which runs it against the database and prints the result.

`EXIT()` goes through the same queue, so everything typed before it is processed first.
On shutdown the application waits up to 30 seconds for the queue to empty. If that is
not enough, it drops the rest and prints how many commands were dropped.

The code is split into five packages:

| Package | Contents |
|---|---|
| `command` | The `Command` types and the parser that turns a line into a command or a failure. |
| `console` | `CommandConsole`, the loop that reads the input. |
| `processing` | `AsyncCommandProcessor` (queue and worker thread) and `CommandHandler` (what each command does and prints). |
| `persistence` | `SessionRepository` with its JDBC implementation, and the H2 setup. |
| `model` | `UserId`. |

`App` wires these together.

## Decisions

**One worker thread.** The assignment asks for asynchronous processing, not parallel.
With more workers `DATA_MODIFY(1)` could overtake `LOGIN(1)`. If more throughput were
needed, I would route all commands of one user to the same worker.

**No locks.** The repository has no state. Every change is a single SQL statement, so
the check and the change cannot be separated:

- The primary key of `user_session` rejects a second login.
- A modification is inserted with `INSERT ... SELECT` from `user_session`, so nothing
  is inserted for a user who is not logged in.

This is also why the repository has no `isLoggedIn()` method. The application calls it
from one thread, but `ConcurrentUsersTest` calls it from 16.

**Invalid input is not an exception.** The parser returns `Success` or `Failure`
instead of throwing, because a typo is an expected situation.

**JDBC, not jOOQ.** In a real project I would use jOOQ with generated classes. Here
there are only a few statements and the assignment is about JDK knowledge. The
repository is behind an interface, so it can be replaced.

## How I read the assignment

- `user_id` is a non-negative integer. `LOGIN(lubos)` and `LOGIN(-1)` are invalid.
- `STATS(1)` and `EXIT(1)` are invalid, because these commands take no argument.
- Modifications are kept as history and logout does not delete them. `STATS()`
  therefore also lists users who made a modification and have logged out since.
  The number "Users modifying data" counts them too, so it can be higher than the
  number of logged-in users. The task says "currently modifying"; I read it as "has
  modified data since the application started". Counting only logged-in users would
  be one JOIN on the session table.
- The database is in memory, so the data is gone when the application ends.

## Known limits

- The queue is not bounded. That is fine for a console, but very large piped input
  would need a limit.
- `STATS()` runs two queries without a transaction. With one worker thread the numbers
  always match; with concurrent writers they could come from slightly different moments.

## Tests

```bash
mvn test
```

| Test | What it covers |
|---|---|
| `CommandParserTest` | Valid and invalid lines. |
| `JdbcSessionRepositoryTest` | The login, logout and modification rules against a real H2. |
| `AsyncCommandProcessorTest` | Order of commands, a failing command, shutdown. |
| `CommandConsoleTest` | The whole application, from input text to output text. |
| `ConcurrentUsersTest` | Logins, logouts and modifications from many threads at once. |
