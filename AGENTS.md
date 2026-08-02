# AGENTS.md — LastMove Contributor Guide

## Project intent

LastMove is a desktop chess suite built with JavaFX. The initial milestone is
deliberately small: load a PGN, navigate its moves, and render each resulting
position on a reusable chessboard control. The project must remain ready to
grow into a broader chess application without mixing UI, chess rules, and
third-party-library details.

## Toolchain and commands

| Concern | Current choice |
| --- | --- |
| Java | 22 |
| Build | Maven |
| UI | JavaFX 22.0.2 |
| Dependency injection / desktop bootstrap | Spring Boot 3.3.2 |
| Chess-format engine | Chesspresso 0.9.2 |

Run these commands from the repository root:

```bash
mvn clean test
mvn clean package
mvn javafx:run
```

The CI baseline is:

```bash
mvn --batch-mode --update-snapshots clean test
```

Do not change Java, JavaFX, Spring Boot, or Maven plugin versions as part of
an unrelated feature. If a version upgrade is necessary, update the POM,
README, CI, and compatibility checks in the same change.

## Architecture

Use the project's pragmatic DDD-inspired package structure. Do not introduce
hexagonal `ports` / `adapters` packages, generic use-case wrappers, or layers
that add indirection without a concrete need.

| Package | Responsibility | Must not depend on |
| --- | --- | --- |
| `bootstrap` | Starts JavaFX and the Spring application context. | UI workflows or chess rules. |
| `domain` | Pure chess concepts, game state, value objects, and domain services. | JavaFX, Spring, Chesspresso, file I/O. |
| `application` | Coordinates user-facing workflows and exposes DTOs/events. | JavaFX controls, FXML, direct file chooser use. |
| `infrastructure` | Technical integrations: Chesspresso mapping, configuration, persistence, file/system details. | JavaFX presentation code. |
| `ui` | JavaFX controls, skins, FXML controllers, view models, styles, and UI helpers. | Chess rules, PGN parsing, Chesspresso types. |

The dependency direction is:

```text
ui -> application -> domain
infrastructure -> domain
bootstrap -> application + infrastructure + ui
```

`application` may use infrastructure-backed Spring services where the current
codebase needs it, but keep external types at the infrastructure boundary and
map them before they reach the application or UI.

## Chess domain and formats

Treat these formats as distinct concepts:

- **PGN** is the input/output representation of a game, including metadata,
  movetext, comments, annotations, and variations where supported.
- **FEN** represents one board position and its state.
- **SAN** is the human-readable notation of an individual move.

The UI requests an application workflow; it does not parse PGN, calculate
legal moves, or manipulate FEN/SAN strings directly. The domain represents the
meaningful game state. Infrastructure translates to and from Chesspresso.

Only classes under `infrastructure/chesspresso` may import `chesspresso.*`.
The correct package prefix is `chesspresso`, **not** `org.chesspresso`.

The vendored engine dependency is part of the build contract:

```text
libs/repository/com/_0xab/chesspresso/0.9.2/chesspresso-0.9.2.jar
libs/repository/com/_0xab/chesspresso/0.9.2/chesspresso-0.9.2.pom
```

Do not delete, rename, ignore, or replace these files with an unresolved
remote Maven dependency. Do not add Clojars, JitPack, or an arbitrary public
repository just to obtain Chesspresso without an explicit project decision.

## JavaFX rules

- Keep FXML controllers thin: bind properties, route events, and delegate to
  application services.
- Keep file chooser code in `ui/support`; it is presentation infrastructure.
- Do not put chess rules, PGN parsing, or Chesspresso objects in controllers,
  FXML, or custom controls.
- Reusable controls own rendering and interaction state, not application
  orchestration.
- Use CSS style classes and pseudo-class states for visual states such as
  hover, pressed, selected, disabled, legal target, last move, and check.

The reusable board belongs under `ui/component/board` and should evolve around
these roles:

```text
ChessBoardControl   public API and observable board state
ChessBoardSkin      layout, square grid, orientation, rendering coordination
ChessSquareControl  one visual square and its interaction/pseudo-class state
BoardTheme          colours and piece-set presentation options
```

The board must expose UI-friendly state and events, while an application
service validates and applies moves. It must resize cleanly, support board
orientation, and avoid embedding a particular screen's layout assumptions.

## Current delivery boundary

The repository currently establishes the project structure and UI shell. Do
not describe an unfinished workflow as implemented. The next vertical slice
should be:

1. select or receive a PGN;
2. parse and map it through the Chesspresso infrastructure;
3. create a domain game tree and initial position;
4. navigate previous/next/first/last moves through an application service;
5. update the board and move list from the resulting state.

Keep this slice end-to-end before adding engine analysis, online play, user
accounts, persistence backends, or a web API.

## Dependencies and framework boundaries

- Spring Boot is used for dependency injection and desktop lifecycle support.
  Do not add `spring-boot-starter-web`, HTTP controllers, or a server runtime
  unless the product explicitly adopts a networked feature.
- Keep JavaFX dependencies in the UI/bootstrap area and domain code free of
  JavaFX property classes.
- Prefer JDK types and small project classes before adding a dependency.
- Add a dependency only with a clear owner, purpose, license review, and test
  coverage where practical.

## Tests and change discipline

- Put unit tests in `src/test/java` following the production package where
  practical.
- Put PGN/FEN fixtures in `src/test/resources`, preferably under descriptive
  folders such as `pgn/` and `fen/`.
- Test domain and application navigation without launching JavaFX.
- Test Chesspresso mappers with representative legal games, special moves
  (castling, promotion, en passant), comments/variations if supported, and
  malformed input.
- Keep UI tests focused on observable control state; do not require a display
  for ordinary Maven tests.
- Every functional change must pass `mvn clean test` before review.

## Pull request checklist

Before opening or updating a pull request:

- [ ] `mvn clean test` passes locally.
- [ ] New code follows the package responsibilities above.
- [ ] No `chesspresso.*` import escaped `infrastructure/chesspresso`.
- [ ] UI code contains no chess-rule or file-format parsing logic.
- [ ] Vendored Chesspresso artifacts remain tracked and Maven can resolve them.
- [ ] README and relevant documentation reflect any changed command,
  dependency, or architectural decision.
- [ ] User-visible behavior and error handling have tests where feasible.

## Licensing and notices

Chesspresso is third-party software bundled in this repository. Preserve its
artifact metadata and attribution. Update `THIRD_PARTY_NOTICES.md` whenever a
vendored or distributed third-party component changes, and include applicable
license text/obligations when preparing a distributable release.
