# LastMove

**Open-source chess suite for playing, studying, analyzing, and replaying games.**

LastMove is a desktop chess application built around a reusable JavaFX board. The current analysis workspace can import a complete PGN tree, start a study from the standard position or a FEN, navigate moves, create non-destructive variations, and switch between studies retained in memory. The domain also models progressive linear games, consent-based takebacks, clocks, and conversion of a played game into an independent study.

LastMove follows a pragmatic DDD-inspired structure: domain models chess concepts and rules, application coordinates workflows, infrastructure hosts technical integrations such as Chesspresso, and ui contains JavaFX presentation code. The project intentionally avoids port-and-adapter abstractions at this stage.

## Overview

LastMove treats a chess game as structured data rather than only a visual board. It works with the standard chess formats used by players and tools:

* **PGN** for complete games, headers, comments, annotations, and variations.
* **FEN** for a precise board position.
* **SAN** for human-readable move notation.

Chesspresso is the current rules and PGN integration. It is isolated behind engine-neutral domain types and `ChessRulesEngine`, allowing a future native implementation without changing the game or analysis aggregates.

## Technology Stack

| Technology    |                Version | Purpose                                                |
| ------------- | ---------------------: | ------------------------------------------------------ |
| Java          |                     22 | Language and runtime                                   |
| JavaFX        |                 22.0.2 | Desktop UI, FXML layouts, reusable controls            |
| Spring Boot   |                  3.3.2 | Dependency injection and desktop application lifecycle |
| Chesspresso   |                  0.9.2 | Chess-game and notation support                        |
| Maven         |                   3.8+ | Build and dependency management                        |
| JUnit Jupiter |                 5.10.2 | Automated testing                                      |
| Jackson       | Managed by Spring Boot | Serialization and future local data support            |
| Caffeine      | Managed by Spring Boot | Local in-memory caching                                |
| RichTextFX    |                 0.11.7 | Rich text support for notation and annotations         |

Spring Boot is used only as a dependency-injection container and lifecycle manager for the desktop application. LastMove does **not** start a web server and does not include `spring-boot-starter-web`.

## Features

### Implemented foundation

* Import a PGN file.
* Build the complete PGN move tree before navigation and display the preferred line immediately.
* Start an analysis session from the standard initial position or a FEN.
* Move forward and backward without deleting the loaded line.
* Create and select non-destructive variations.
* Render the current position in a reusable JavaFX board control.
* Retain multiple analysis sessions in memory and switch between them from the screen or modal.
* Execute progressive games through `ChessGame.move(...)` using an injected rules engine.
* Submit progressive moves either by board coordinates (`MoveCommand`) or SAN (`"Nf3"`).
* Track clocks and apply opponent-approved takebacks.
* Convert a progressive game into an independent `AnalysisSession` through `GameRecord`.

### Planned

* PGN editing and export.
* Training exercises and puzzles.
* UCI engine analysis.
* Opening explorer and game collections.
* Persistent repositories for studies and played games.
* Online play, profiles, and community features.

## Project Structure

```text
src/main/java/com/escontrela/lastmove/
├── bootstrap/       Application startup and JavaFX/Spring integration.
├── domain/          Chess concepts, rules, game model, and notation.
├── application/     Workflow services, DTOs, and application events.
├── infrastructure/  Technical configuration, Chesspresso, and file support.
└── ui/              JavaFX controllers, screens, components, and view models.
```

### Package responsibilities

| Package          | Responsibility                                                                                                                           |
| ---------------- | ---------------------------------------------------------------------------------------------------------------------------------------- |
| `bootstrap`      | Starts JavaFX, creates the Spring context, and handles application shutdown.                                                             |
| `domain`         | Represents progressive games, analysis trees, positions, players, FEN, SAN, and PGN. It contains no JavaFX or Spring code.             |
| `application`    | Coordinates PGN loading, creation/navigation of analysis sessions, conversion from played games, and repository access.                  |
| `infrastructure` | Contains technical implementations: Spring configuration, local files, and the Chesspresso integration.                                  |
| `ui`             | Contains JavaFX-only code: FXML controllers, screen navigation, CSS, visual controls, and presentation state.                            |

The UI must not implement chess rules. Controllers delegate actions to application services and update view models.

## Detailed Source Layout

```text
src/main/java/com/escontrela/lastmove/
├── bootstrap/
│   ├── LastMoveApplication.java        # Spring Boot configuration
│   ├── LastMoveLauncher.java           # Main entry point
│   ├── JavaFxApplication.java          # JavaFX Application lifecycle
│   └── JavaFxSpringContext.java        # Controlled Spring context access
├── domain/
│   ├── common/                         # Square, piece color/type, shared chess values
│   ├── game/                           # ChessGame, clocks, takebacks, GameRecord and rules contract
│   ├── analysis/                       # AnalysisSession, its tree and played-game conversion
│   ├── notation/                       # Fen, SanMove, PgnGame
│   └── service/                        # Stateless domain services such as FenService
├── application/
│   ├── service/                        # GameLoadService and AnalysisSessionService
│   ├── repository/                     # Analysis-session persistence contract
│   └── dto/                            # Input and output data for UI workflows
├── infrastructure/
│   ├── config/                         # Spring beans and application configuration
│   ├── chesspresso/                    # ChesspressoRulesEngine, readers and mappers
│   ├── session/                        # In-memory analysis-session repository
│   └── persistence/                    # Reserved for future local persistence
└── ui/
    ├── controller/                     # FXML screen controllers
    ├── component/board/                # Reusable ChessBoardControl and square controls
    ├── event/                          # UI-only events
    ├── model/                          # Board and presentation view models
    ├── screen/                         # Screen lifecycle and navigation
    └── support/                        # CSS, icon, and JavaFX-thread utilities
```

## Chess Formats

| Format | Use in LastMove                                                            |
| ------ | -------------------------------------------------------------------------- |
| PGN    | Import game headers, main line, result, starting position, and variations.  |
| FEN    | Create a study from an exact position and all rule-relevant state.          |
| SAN    | Display moves in the standard notation familiar to chess players.          |

`infrastructure/chesspresso` is the only package that depends directly on Chesspresso. Domain and UI classes use LastMove's own model rather than exposing Chesspresso classes across the application.

## Getting Started

### Prerequisites

* JDK 22
* Maven 3.8 or newer

### Build and test

```bash
mvn clean test
mvn clean package
```

### Run the desktop application

```bash
mvn javafx:run
```

## Analysis Workspace

The PGN analysis screen contains three working areas:

* A left session list ordered from most recently created to oldest.
* A central `ChessBoardControl` that renders the active session and emits neutral move requests.
* A right notation list with the complete preferred line and previous/next navigation.

Open PGN, RESET, and FEN each create a new analysis session. The controller stores only the active
session identifier for this screen; the repository and application service have no global active
selection.

Visual controls own rendering and interaction only. A board control receives a view model or a game position; it does not parse PGN, validate chess rules, or call persistence services.

## Domain Model

`ChessGame` is a progressive, single-line aggregate. It owns its current position, official plies,
players, clocks, and terminal result. `AnalysisSession` owns a cursor and an `AnalysisTree`, where
each `AnalysisNode` wraps a tree-neutral `Ply`.

Moves are validated through `ChessRulesEngine`. The current infrastructure implementation is
`ChesspressoRulesEngine`; domain, application, and UI never receive Chesspresso objects.

For a detailed class inventory and flows, see:

* [Architecture summary](docs/pgn-analysis-session-architecture.md)
* [Current ChessGame and AnalysisSession model](docs/proposed-chess-game-analysis-model.md)

## Development Guidelines

* Keep chess rules and game concepts in `domain`.
* Keep JavaFX imports inside `bootstrap` and `ui` whenever possible.
* Keep Chesspresso imports inside `infrastructure/chesspresso`.
* Keep controllers small: coordinate UI events and delegate work to `application/service`.
* Add domain and application tests for game invariants, move navigation, and mappings before adding new UI behavior.
* Prefer immutable value objects and records where they make the domain clearer.

## Roadmap

* [x] Import and replay PGN games.
* [x] Reusable `ChessBoardControl` JavaFX component.
* [x] Complete move list with non-destructive variations.
* [x] Analysis sessions from initial position and FEN.
* [x] In-memory session switching.
* [x] Progressive `ChessGame`, takebacks, and conversion to analysis.
* [ ] PGN editing and export.
* [ ] Persistent study and played-game repositories.
* [ ] UCI engine analysis.
* [ ] Training, puzzles, online play, and community features.

## License

This project is licensed under the MIT License.
