# Arquitectura de sesiones y análisis PGN

Este documento resume las clases introducidas para el flujo de análisis: abrir un PGN, crear una
partida desde la posición inicial o un FEN, recorrer jugadas y mantener varias sesiones en memoria.

## Flujo principal

```text
PgnAnalysisScreenController
  -> GameLoadService -> ImportedPgnGame
  -> GameSessionService
  -> GameSessionRepository
    -> InMemoryGameSessionRepository
      -> GameSession
        -> Ply tree + PositionSnapshot

GameSessionService.attemptMove(...)
  -> GameMoveService.validate(...)
    -> ChesspressoMoveValidator
  -> GameSession.apply(result)
```

La UI no contiene reglas de ajedrez ni utiliza tipos de Chesspresso. Sólo conserva el `SessionId`
de la sesión renderizada y delega en los servicios de aplicación.

## Dominio

### `GameSession`

Agregado mutable que representa una sesión de análisis abierta. Es la fuente de verdad del estado
vivo de una partida y de su árbol de variantes.

**Atributos principales**

- `id: SessionId`: identidad estable de la sesión.
- `title: String`: nombre visible que se persiste junto con el agregado.
- `origin: GameSessionOrigin`: origen (`PGN`, `INITIAL_POSITION` o `FEN`).
- `initialPosition: PositionSnapshot`: posición raíz, anterior al primer ply.
- `currentPosition: PositionSnapshot`: posición que está viendo y editando el usuario.
- `pliesById: Map<UUID, Ply>`: índice del árbol completo de jugadas.
- `currentPlyId: UUID?`: cursor de navegación; vacío cuando se está en la posición inicial.
- `result: GameResult?`: resultado derivado cuando hay mate o ahogado.

**Métodos principales**

- `apply(MoveExecutionResult)`: añade un movimiento aceptado como hijo del cursor. Si se había
  vuelto hacia atrás, crea una variante y nunca borra la continuación existente.
- `previous()`, `next()`, `first()`: navegación de la línea seleccionada.
- `select(Ply)`: coloca el cursor en una rama concreta ya existente.
- `currentLine()`: línea desde la raíz hasta el cursor.
- `notationLine()`: línea completa para la lista de Moves; incluye los plies futuros de la rama
  elegida.
- `gameState()`: devuelve el estado de lectura derivado del snapshot vigente.

### `Ply`

Nodo de un medio-movimiento dentro del árbol de una `GameSession`.

**Atributos principales**

- `id` y `parentId`: identidad del nodo y relación con el ply padre.
- `move: MoveDescriptor`: descripción semántica del movimiento (origen, destino, SAN, captura,
  enroque, promoción, etc.).
- `resultingPosition: PositionSnapshot`: estado completo inmediatamente después de la jugada.
- `moveNumber`, `movingColor`: datos para notación y presentación.
- `variations: List<Ply>`: continuaciones alternativas desde esa posición.

`Ply` conserva el snapshot resultante para navegar sin recalcular todas las jugadas anteriores.

### `PositionSnapshot`

Value object inmutable y neutral respecto al motor. Describe completamente una posición que la UI
puede renderizar y que el validador puede reconstruir.

**Atributos principales**

- `pieces: List<PositionPiece>`.
- `activeColor: PieceColor`.
- `castlingRights: CastlingRights`.
- `enPassantTarget: Optional<Square>`.
- `halfmoveClock`, `fullmoveNumber`.
- `lastMove: Optional<MoveDescriptor>`.
- `check`, `mate`, `stalemate`.

### `MoveExecutionResult`

Resultado inmutable de validar una petición de movimiento. La validación y la mutación de la sesión
son pasos separados.

**Atributos principales**

- `accepted` y `rejectionReason`.
- `newSnapshot`: posición resultante; en un rechazo es la posición vigente sin cambios.
- `move` y `capturedPiece` opcionales.
- `check`, `mate`, `stalemate`.
- `legalDestinationsNextTurn`: destinos legales disponibles tras el movimiento.

`GameSession.apply(result)` exige un resultado aceptado y protege las invariantes del agregado.

### `GameSessionState` y `CastlingRights`

- `GameSessionState` es una vista de lectura derivada de `currentPosition`: turno, enroques,
  en-passant, relojes, jaque, mate, ahogado y resultado. No duplica estado mutable.
- `CastlingRights` contiene los cuatro permisos de enroque y ofrece `none()` e `initial()`.

### Importación PGN: `ImportedPgnGame` e `ImportedPly`

- `ImportedPgnGame` agrupa los metadatos `PgnGame` y las variantes raíz.
- `ImportedPly` agrupa un `MoveExecutionResult` aceptado y sus continuaciones.

Son el formato de transición neutral entre el lector PGN de infraestructura y el agregado
`GameSession`. Así un PGN se carga como árbol, no como una lista plana.

## Servicios de aplicación

### `GameSessionService`

Caso de uso principal para la vida de las sesiones de análisis.

**Dependencias**: `GameSessionRepository`, `GameMoveService`.

**Métodos principales**

- `createInitialSession()`: crea y activa una sesión desde la posición estándar.
- `createFenSession(Fen)`: crea y activa una sesión desde FEN.
- `createPgnSession(PgnGame | ImportedPgnGame)`: crea, activa e importa el árbol PGN.
- `listSessions()`, `sessionSummary(SessionId)`: soporte para selectores de sesión propios de
  cada pantalla.
- `attemptMove(SessionId, MoveCommand)`: valida el movimiento y aplica el resultado a esa sesión.
- `previous`, `next`, `first`, `select`: navegación.
- `currentPosition`, `moveHistory`, `notationLine`: datos para renderizar tablero y notación.

Devuelve `GameSessionSummary` al exponer sesiones a la UI, de modo que no entrega el agregado
mutable directamente.

### `GameSessionRepository` e `InMemoryGameSessionRepository`

`GameSessionRepository` es el contrato de aplicación para guardar y recuperar sesiones.
`InMemoryGameSessionRepository`, en infraestructura, es la implementación actual limitada al
proceso; una implementación PostgreSQL futura sustituirá sólo esta clase.

**Atributos principales**

- `sessions: Map<SessionId, GameSession>` en la implementación en memoria.

**Métodos principales**

- `save(session)`.
- `findById(sessionId)`.
- `findAllByMostRecent()`.

No existe una sesión activa global. Cada pantalla mantiene su propio `SessionId` seleccionado.

### `GameMoveService`

Servicio de aplicación sin estado para la validación de movimientos.

**Dependencia**: `ChesspressoMoveValidator`.

**Métodos principales**

- `startingPosition()`.
- `snapshotFor(Fen)`.
- `validate(PositionSnapshot, MoveCommand)`.

No conoce `SessionId`, no consulta el catálogo y no modifica `GameSession`.

### `GameLoadService`

Caso de uso de entrada de un PGN desde archivo o texto.

**Dependencia**: `ChesspressoPgnReader`.

- `importPgn(PgnImportRequest)`: lee el PGN y lo transforma a `ImportedPgnGame`.

El controlador PGN decide después crear la sesión mediante `GameSessionService.createPgnSession`;
importar un archivo y abrirlo como sesión son responsabilidades independientes.

## Infraestructura Chesspresso

### `ChesspressoMoveValidator`

Adaptador sin estado para reglas de ajedrez. Recibe un snapshot, reconstruye una posición temporal
de Chesspresso, busca y aplica un movimiento legal sólo en esa instancia y devuelve
`MoveExecutionResult` neutral.

Gestiona captura, enroque, promoción, en-passant, jaque, mate, ahogado y destinos legales. No
mantiene sesiones ni expone tipos `chesspresso.*` fuera de infraestructura.

### `ChesspressoPgnReader`

Adaptador de importación PGN.

- `readImportedFirst(...)`: analiza el primer juego desde texto, stream o fichero y recorre sus
  continuaciones para producir `ImportedPgnGame` / `ImportedPly`.
- `readFirst(...)`: versión que devuelve únicamente los metadatos `PgnGame`.

## Presentación

`PgnAnalysisScreenController` conserva `boardSessionId`, sin escribir ningún estado global, llama a los casos de uso y renderiza:

- el tablero con `currentPosition`;
- la línea completa con `notationLine` y el ply actual seleccionado;
- sesiones abiertas en el rail izquierdo y en el modal de sesiones;
- las entradas Open PGN, RESET y FEN.

No valida movimientos, no analiza PGN y no almacena partidas por sí mismo.
