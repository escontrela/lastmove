# LastMove

**Open-source chess suite for playing, studying, analyzing, and replaying games.**

LastMove is a desktop chess application built around a reusable JavaFX board. Its first milestone is deliberately focused: open a PGN game, navigate its main line and variations, and show every position on the board. The same foundation will later support study tools, analysis engines, puzzles, game management, online play, and community features.

LastMove follows a pragmatic DDD-inspired structure: domain models chess concepts and rules, application coordinates workflows, infrastructure hosts technical integrations such as Chesspresso, and ui contains JavaFX presentation code. The project intentionally avoids port-and-adapter abstractions at this stage.

## Overview

LastMove treats a chess game as structured data rather than only a visual board. It works with the standard chess formats used by players and tools:

* **PGN** for complete games, headers, comments, annotations, and variations.
* **FEN** for a precise board position.
* **SAN** for human-readable move notation.

Chesspresso is used as the initial technical library for parsing and traversing games. Its code is isolated from the core model so that the application can evolve without binding every layer to a third-party API.

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

### First milestone

* Import a PGN file.
* Display its game metadata and move list.
* Move forward and backward through the game.
* Navigate variations.
* Render the current position in a reusable JavaFX board control.
* Show FEN and SAN for the selected move.

### Planned

* PGN editing and export.
* Position setup from FEN.
* Training exercises and puzzles.
* UCI engine analysis.
* Opening explorer and game collections.
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
| `domain`         | Represents chess concepts such as games, moves, positions, players, variations, FEN, SAN, and PGN. It contains no JavaFX or Spring code. |
| `application`    | Coordinates workflows such as loading a game, navigating a move tree, and exporting a PGN.                                               |
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
│   ├── game/                           # Game, GameState, Move, MoveTree, Variation, Player
│   ├── notation/                       # Fen, SanMove, PgnGame
│   └── service/                        # FenService, PgnService, GameNavigationService
├── application/
│   ├── service/                        # GameLoadService, GameReplayService, GameExportService
│   ├── dto/                            # Input and output data for UI workflows
│   └── event/                          # GameLoadedEvent, CurrentMoveChangedEvent
├── infrastructure/
│   ├── config/                         # Spring beans and application configuration
│   ├── chesspresso/                    # Chesspresso readers and mappers
│   ├── persistence/                    # Future local persistence
│   └── support/                        # File and platform utilities
└── ui/
    ├── controller/                     # FXML screen controllers
    ├── component/board/                # Reusable ChessBoardControl and square controls
    ├── event/                          # UI-only events
    ├── model/                          # Board and move-list view models
    ├── screen/                         # Screen lifecycle and navigation
    └── support/                        # CSS, icon, and JavaFX-thread utilities
```

## Chess Formats

| Format | Use in LastMove                                                            |
| ------ | -------------------------------------------------------------------------- |
| PGN    | Import and export full games, tags, comments, annotations, and variations. |
| FEN    | Restore, display, copy, and share an exact board position.                 |
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

## UI Foundation

The first screen contains three reusable areas:

* A central `ChessBoardControl` to render the current position and user interaction states.
* A move-list panel for the main line and variations of the loaded PGN.
* An information panel for headers, comments, FEN, SAN, and future engine output.

Visual controls own rendering and interaction only. A board control receives a view model or a game position; it does not parse PGN, validate chess rules, or call persistence services.

## Development Guidelines

* Keep chess rules and game concepts in `domain`.
* Keep JavaFX imports inside `bootstrap` and `ui` whenever possible.
* Keep Chesspresso imports inside `infrastructure/chesspresso`.
* Keep controllers small: coordinate UI events and delegate work to `application/service`.
* Add unit tests for notation, move navigation, and mappers before adding new UI behavior.
* Prefer immutable value objects and records where they make the domain clearer.

## Roadmap

* [ ] Import and replay PGN games.
* [ ] Reusable `ChessBoardControl` JavaFX component.
* [ ] Move list with variation navigation.
* [ ] FEN viewer and position setup.
* [ ] PGN editing and export.
* [ ] UCI engine analysis.
* [ ] Training, puzzles, online play, and community features.

## License

This project is licensed under the [MIT License](LICENSE).
