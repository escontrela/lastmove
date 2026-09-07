# Knightshade Engine

Motor de ajedrez clásico embebido en LastMove. No usa redes neuronales ni aprendizaje automático: se basa exclusivamente en búsqueda con poda, evaluación heurística y optimización de la fuerza bruta, siguiendo la línea de Crafty, Fruit o las primeras versiones de Stockfish.

- **Paquete raíz:** com.knightshade.engine
- **Adaptador en LastMove:** com.escontrela.lastmove.infrastructure.engine.knightshade
- **Versión actual:** v3 (descriptor Knightshade v3)
- **Fuerza estimada:** ~1200 ELO

Esta versión conserva la descripción de arquitectura original y explica la intuición que hay detrás. Knightshade no crea un tablero nuevo por cada variante: mantiene un estado compacto, prueba una jugada, explora su respuesta, deshace exactamente el cambio y continúa. Su fuerza procede de recorrer ese árbol de variantes de la forma más selectiva posible.

---

## 1. Objetivo y fronteras

Knightshade es un componente **independiente del dominio de LastMove**. Cumple tres reglas:

1. **No importa** Spring, JavaFX, Chesspresso ni agregados/servicios de LastMove.
2. Solo reutiliza un *kernel* de value objects inmutables y libres de framework del dominio de LastMove: Square, PieceColor, PieceType y CastlingRights.
3. Se comunica con el exterior mediante una única interfaz pública (Engine), con el mismo contrato lógico que un motor UCI (position fen → bestmove) pero **sin proceso externo**.

El adaptador de LastMove es la única pieza que conoce ambos mundos y los traduce entre sí.

La frontera mantiene al motor fácilmente testeable: búsqueda, generación y evaluación pueden recibir un FEN y devolver una jugada sin levantar UI, Spring ni una partida de dominio completa.

---

## 2. Arquitectura interna

~~~
com.knightshade.engine
├── api/            # Contrato público (Engine, SearchLimits, SearchResult, StopSignal)
├── board/          # Posición, FEN, Zobrist, make/unmake, ataques
├── movegen/        # Generación de movimientos legales
├── evaluation/     # Evaluadores e interfaces
├── ordering/       # Ordenación de movimientos (MVV-LVA, killers, history)
├── search/         # Algoritmos de búsqueda
├── see/            # Static Exchange Evaluation
├── transposition/  # Tabla de transposición
├── time/           # Gestión del tiempo
└── KnightshadeEngine  # Ensamblado por defecto
~~~

~~~mermaid
classDiagram
    direction TB
    class Engine { <<interface>> }
    class Search { <<interface>> }
    class Position { <<interface>> }
    class Evaluator { <<interface>> }
    class MoveGenerator { <<interface>> }
    class MoveOrderer { <<interface>> }
    class KnightshadeEngine
    class Board
    class LegalMoveGenerator
    class PositionalEvaluator
    class ParallelRootSearch
    class IterativeDeepeningSearch
    class QuiescenceSearch
    class TranspositionTable
    class Zobrist
    KnightshadeEngine --> Engine : implements
    KnightshadeEngine --> ParallelRootSearch
    ParallelRootSearch --> Search : implements
    ParallelRootSearch --> IterativeDeepeningSearch : trabajadores aislados
    IterativeDeepeningSearch --> Search : implements
    IterativeDeepeningSearch --> MoveGenerator
    IterativeDeepeningSearch --> Evaluator
    IterativeDeepeningSearch --> MoveOrderer
    IterativeDeepeningSearch --> QuiescenceSearch
    IterativeDeepeningSearch --> TranspositionTable
    Board --> Position : implements
    Board --> Zobrist
    LegalMoveGenerator --> MoveGenerator : implements
    PositionalEvaluator --> Evaluator : implements
~~~

Search depende de abstracciones: necesita una posición, jugadas y una evaluación; no conoce la pantalla ni los agregados de LastMove. Evaluator recibe una vista Position de solo lectura, por lo que no puede modificar por accidente la posición que está analizando Search.

---

## 3. Representación del tablero (board)

### 3.1 Qué es un estado

Un estado no es solamente la colocación de las piezas. También incluye:

~~~
Board
├── piezas en 64 casillas
├── sideToMove
├── castlingRights
├── enPassantTarget
├── contadores de la regla de 50 movimientos
├── zobristKey
└── pila Undo
~~~

Dos tableros que se ven iguales pueden representar posiciones distintas. Por ejemplo, si una torre salió de su esquina y volvió, el derecho de enroque se ha perdido aunque las piezas estén en los mismos cuadros. Otras diferencias decisivas son el jugador al que toca mover y el objetivo en passant.

### 3.2 Mailbox

El tablero es un int[64] (mailbox), índice rank * 8 + file (a1 = 0, h8 = 63). Cada casilla contiene un entero que codifica la pieza (Piece):

- Bits 0–2: tipo (1 peón, 2 caballo, 3 alfil, 4 torre, 5 dama, 6 rey).
- Bit 3: color (0 blanco, 1 negro).
- 0 = casilla vacía.

~~~
índices:  56 57 58 59 60 61 62 63   ← rango 8
          48 49 50 51 52 53 54 55
          ...
           8  9 10 11 12 13 14 15
           0  1  2  3  4  5  6  7   ← rango 1
           a  b  c  d  e  f  g  h
~~~

Consultar una casilla es una lectura directa del array. Las coordenadas inmutables Square se reutilizan desde una caché de 64 casillas propia del motor. No se crean objetos de pieza ni colecciones temporales por cada nodo de búsqueda. El mailbox es simple de depurar y suficientemente claro para v0–v3.

Está **oculto tras la interfaz Position**, de modo que una posible migración a bitboards en v5 no tocará search ni evaluation. Los bitboards aceleran algunos cálculos masivos de ataques, pero su coste de complejidad no es necesario para la arquitectura actual.

### 3.3 Board, make/unmake y Undo

Position es una vista de solo lectura. Board es el workspace mutable que la implementa y añade:

- make(Move) / unmake(): aplica y deshace con una pila Undo.
- makeNullMove() / unmakeNullMove(): pasa turno para null-move pruning.
- isSquareAttacked, kingSquare e inCheck: detección de ataques.
- zobristKey(): clave hash incremental.

La búsqueda mantiene un único Board mientras desciende por una variante:

~~~mermaid
flowchart TD
    A[Estado inicial] --> B[make e2-e4]
    B --> C[make e7-e5]
    C --> D[make g1-f3]
    D --> E[Evaluar o seguir buscando]
    E --> F[unmake]
    F --> G[unmake]
    G --> H[unmake]
    H --> A
~~~

La pila Undo reutiliza un marco mutable por profundidad activa y crece cuando hace falta, evitando crear un objeto por cada movimiento probado. Antes de mutar el estado, make guarda en Undo únicamente lo que no se puede reconstruir solo mirando la jugada: pieza capturada, derechos de enroque, objetivo en passant, contadores y hash anterior. También trata capturas en passant, promoción y el movimiento de torre del enroque.

~~~
Estado S: peón en e2, hash H0
make(e2-e4): guardar metadatos; vaciar e2; ocupar e4; target e3; hash H1
unmake(): restaurar e2, target anterior y H0
~~~

Copiar 64 casillas para cada hijo sería correcto pero muy caro. Guardar un Undo pequeño y restaurarlo es mucho más barato. La invariante fundamental es que, después de explorar una rama, Board queda idéntico al estado que tenía al entrar.

### 3.4 Generación de movimientos (movegen)

LegalMoveGenerator genera primero movimientos **pseudo-legales** (peón con doble paso/en passant/promoción, saltos de caballo/rey, deslizantes de alfil/torre/dama, enroques). Si hay jaque, comprueba todas las evasiones con make/unmake. Sin jaque, calcula las piezas clavadas al rey y solo necesita make/unmake para ellas, para el rey y para capturas al paso; las demás jugadas pseudo-legales no pueden exponer al rey. El enroque verifica además que el rey no cruce ni aterrice en casilla atacada.

generateCaptures genera directamente capturas y promociones (incluidas las promociones sin captura), sin construir las jugadas tranquilas que se descartarían. hasLegalMove termina la comprobación de legalidad al encontrar la primera jugada válida. Board mantiene la casilla de cada rey de forma incremental para evitar recorrer las 64 casillas en cada comprobación de jaque.

Separar los movimientos pseudo-legales de la comprobación final simplifica las reglas: una pieza primero se mueve según su geometría, y Board valida después que el rey no quedó expuesto.

### 3.5 Zobrist hashing

Zobrist genera claves deterministas (semilla fija) para pieza×casilla, color al mover, 16 combinaciones de enroque y 9 estados de en passant. Board mantiene la clave **incrementalmente** (XOR de los deltas en make/unmake), lo que hace las búsquedas en la TT O(1).

Una clave Zobrist es una huella de 64 bits. Para mover una pieza basta con eliminar por XOR la clave de origen, añadir la de destino y cambiar las claves de los metadatos afectados. No hace falta recorrer el tablero entero.

~~~
hash anterior
  XOR clave(pieza, origen)
  XOR clave(pieza, destino)
  XOR clave(lado al mover)
= hash nuevo
~~~

Aplicar dos veces el mismo XOR lo cancela, una propiedad ideal para deshacer. La colisión teórica existe, pero con 64 bits es una herramienta práctica estándar para la tabla de transposición.

---

## 4. Búsqueda (search)

El motor activo es ParallelRootSearch, que coordina trabajadores con el núcleo recursivo de IterativeDeepeningSearch. Con `knightshade.threads=1` se usa la búsqueda secuencial original. El motor explora un árbol: cada nodo es un estado Board y cada arista una jugada legal. Al llegar a una hoja estima la posición y propaga ese resultado de vuelta hacia la raíz.

~~~mermaid
flowchart TD
    R[Posición raíz] --> A[Jugada A]
    R --> B[Jugada B]
    R --> C[Jugada C]
    A --> A1[Respuesta rival]
    A --> A2[Respuesta rival]
    B --> B1[Respuesta rival]
    C --> C1[Respuesta rival]
~~~

No puede buscar el árbol completo: con unas 30 jugadas por posición, solo seis plies ya producen cientos de millones de hojas. Las técnicas siguientes reducen drásticamente las ramas necesarias sin cambiar el objetivo: elegir la mejor jugada raíz dentro del tiempo.

### 4.1 Negamax y alfa-beta

Negamax usa la simetría de la evaluación: lo bueno para un lado es malo para el otro. En vez de escribir una función que maximiza para blancas y otra que minimiza para negras, cada llamada niega el resultado de su hijo.

~~~
score = -negamax(hijo, profundidad - 1, -beta, -alpha)
~~~

Alpha es el mejor resultado que el lado actual ya puede garantizar. Beta es el límite a partir del que el rival nunca permitiría continuar. Cuando score >= beta, el rival tiene una alternativa que evita esta línea y se produce un corte beta: ya no vale la pena analizar sus hermanos.

~~~mermaid
flowchart TD
    A[Primera variante: +80] --> B[alpha = +80]
    B --> C[Otra variante ya no puede superar +50]
    C --> D[Podar el resto de esa variante]
~~~

Alfa-beta es correcto, pero solo es realmente eficaz si la mejor jugada se prueba pronto. Por eso la ordenación de movimientos es una parte esencial de la fuerza del motor.

### 4.2 Iterative Deepening

Explora profundidad 1, 2, 3… hasta agotar el tiempo (TimeManager) o encontrar mate forzado. Garantiza que siempre haya una jugada lista y que la búsqueda pueda detenerse limpiamente entre iteraciones.

La repetición inicial es útil: se conserva explícitamente la mejor jugada raíz de la iteración anterior para probarla primero, junto con las entradas para la tabla de transposición. Si llega StopSignal, se devuelve la última profundidad completamente terminada.

### 4.3 Principal Variation Search (PVS)

Negamax con poda alfa-beta: el primer hijo se busca con ventana completa; los demás con ventana nula (-alpha-1, -alpha), re-buscando con ventana completa solo si supera alpha.

PVS pregunta a los hijos tardíos: “¿puedes ser mejor que lo que ya tengo?”. Si no pueden, el coste es pequeño. Si uno parece mejorar alpha, se confirma con una ventana completa para obtener el valor exacto.

Desde profundidad 3, ParallelRootSearch busca primero la mejor jugada de la iteración anterior y reparte las alternativas restantes entre el coordinador y hasta N−1 hilos de plataforma. Cada trabajador conserva su propio Board, mapa de repeticiones, TT, evaluador, killers e historial durante esa solicitud. Se comparten alpha, el índice de la siguiente variante, el resultado raíz y las señales de parada mediante operaciones atómicas y publicación sincronizada. Cada intento espera a todos los trabajadores antes de reutilizar su estado. Cancelación, interrupción o fallo detienen toda la solicitud; solo una profundidad completada puede convertirse en el resultado final. Véase [búsqueda paralela](knightshade-parallel-search.md).

### 4.4 Aspiration Windows

En lugar de empezar cada profundidad con [-INF, +INF], se centra la ventana en la puntuación de la iteración anterior ± delta (25 cp), ensanchando el delta al doble ante cada fallo alto/bajo.

Si la profundidad anterior da +32 cp, la siguiente puede intentar [+7, +57]. Si sale fuera, se reintenta con una ventana más ancha; no se acepta una estimación truncada. En posiciones estables esta técnica permite más cortes de alfa-beta.

### 4.5 Null Move Pruning

En nodos de ventana nula, con profundidad suficiente, sin jaque, con material no-peón y evaluación estática al menos beta, se “pasa” el turno y se busca con profundidad reducida R = 2 + depth/4. Se excluyen ventanas de mate y no se vuelve a aplicar esta poda dentro de una rama iniciada por un pase. Esa rama tampoco usa resultados de TT ni declara tablas a partir del historial real. Si aun así se supera beta, se poda (evita líneas en las que el oponente puede hacer algo útil). El chequeo de material excluye los finales de peones, aunque no elimina todos los posibles zugzwangs con piezas.

La intuición es: si incluso regalando el turno la posición sigue siendo suficiente para que el rival rechace la variante, una jugada real probablemente también lo será. No se aplica en situaciones delicadas porque en un zugzwang pasar puede ser mucho mejor que cualquier movimiento legal.

### 4.6 Late Move Reductions (LMR)

A las jugadas tranquilas (no capturas/promociones/killers) a partir de la 4ª posición de la lista se les reduce 1 ply; si la búsqueda reducida supera alpha, se re-busca a profundidad completa.

Una lista bien ordenada deja normalmente las ideas menos prometedoras al final. LMR las explora primero con un presupuesto menor y solo invierte profundidad completa en una sorpresa que parezca buena.

### 4.7 Quiescence Search

En las hojas, para evitar el “efecto horizonte”, se extiende la búsqueda solo con capturas (y promociones):

- stand-pat: se acepta la evaluación estática si ya supera beta.
- Capturas ordenadas por MVV-LVA, **filtrando las perdedoras con SEE** (See.ge(board, move, 0)).
- Si el bando está en jaque, se buscan **todas** las evasiones legales (no solo capturas).
- La entrada desde la búsqueda principal también examina jaques tranquilos durante un nivel; este permiso ya no depende de un contador global de 8000 nodos.
- En esa entrada se reutiliza la lista legal para capturas y jaques. En las siguientes hojas se generan solo capturas/promociones y se comprueba que exista alguna jugada legal antes de aceptar stand-pat si la lista táctica está vacía.
- Ahogado, repetición y contador de 50 movimientos se reconocen dentro de quiescencia; el mate tiene precedencia sobre el contador. El historial se restaura al salir de cada rama.
- En la raíz, una repetición se compara con las demás alternativas como tablas: una evaluación estática favorable ya no basta para excluirla antes de buscar.

Sin quiescence, una hoja justo antes de una recaptura parece artificialmente favorable. La quiescence continúa lo tácticamente ruidoso hasta que la evaluación sea más estable, sin reabrir todos los movimientos tranquilos.

### 4.8 Tabla de transposición (transposition)

Cache por clave Zobrist y contador de medias jugadas (2¹⁸ entradas, cubetas de cuatro vías con preferencia por profundidad y resultados exactos) que guarda (mejorMovimiento, depth, score, tipo). Tipos: EXACT, LOWER_BOUND, UPPER_BOUND. Las puntuaciones de mate se ajustan a distancia al moverlas dentro/fuera de la tabla (Scores.toTable/fromTable). Cada consulta o inserción examina como máximo cuatro entradas, sin cadenas de sondeo. La tabla se limpia al comenzar cada búsqueda porque el historial de partida puede haber cambiado; se reutiliza entre profundidades de esa búsqueda. Las hojas de quiescencia no desplazan entradas profundas y una actualización sin jugada conserva la jugada previamente almacenada para esa clave.

Distintas órdenes de jugadas pueden alcanzar exactamente el mismo estado. La TT evita volver a calcular su subárbol. Además, el mejor movimiento guardado se intenta primero y ayuda a producir cortes.

| Tipo | Significado |
| --- | --- |
| EXACT | la puntuación es exacta |
| LOWER_BOUND | la puntuación es al menos ese valor |
| UPPER_BOUND | la puntuación es como máximo ese valor |

### 4.9 Ordenación de movimientos (ordering)

MvvLvaMoveOrderer puntúa cada jugada por prioridad:

1. Movimiento de la TT (si existe).
2. Capturas/promociones por MVV-LVA (víctima·10 − atacante), degradando las perdedoras según SEE.
3. Killers primario/secundario (por ply).
4. Movimientos tranquilos por history heuristic.

KillerMoves guarda dos refutaciones tranquilas por ply. HistoryTable separa los colores ([2][64][64]), limita sus valores a ±16384 y aplica actualizaciones de gravedad: recompensa la jugada que provoca un corte y penaliza las jugadas tranquilas anteriores que no lo consiguieron. SEE se calcula una sola vez por jugada durante la ordenación; las capturas perdedoras se prueban después de las tranquilas.

MVV-LVA favorece capturar una pieza valiosa con una pieza barata. Los killers recuerdan movimientos tranquilos que ya refutaron una variante; history acumula qué origen-destino ha producido cortes. Son prioridades, no pruebas tácticas.

~~~mermaid
flowchart TD
    A[Movimientos legales] --> B{¿Movimiento TT?}
    B -- sí --> C[Primero]
    B -- no --> D{¿Captura o promoción?}
    D -- sí --> E[MVV-LVA + SEE]
    D -- no --> F{¿Killer?}
    F -- sí --> G[Alta prioridad]
    F -- no --> H[History heuristic]
    C --> I[PVS y alfa-beta]
    E --> I
    G --> I
    H --> I
~~~

---

## 5. Evaluación (evaluation)

Interfaz Evaluator (centipawns, positivo = blanco mejor). El evaluador activo es **PositionalEvaluator** (v3), que suma:

| Componente | Descripción |
| --- | --- |
| **Material** | PieceValues: P=100, N=320, B=330, R=500, Q=900 |
| **Piece-Square Tables** | Bonos por pieza/casilla, reflejados para negras; el rey interpola entre refugio y actividad central según material restante |
| **Movilidad** | Nº de casillas atacadas por N/B/R/Q menos una línea base, ponderado por pieza |
| **Seguridad del rey** | Escudo de peones delante del rey, ponderado por fase para desaparecer en finales de peones |

PositionalEvaluator mantiene además una caché exacta de 32768 evaluaciones por clave Zobrist, con comprobación de la clave completa. Al visitar de nuevo una posición se evita repetir el cálculo de los ocho términos. Los contadores de tablas y el historial se resuelven en búsqueda, fuera de esta evaluación estática. La caché pertenece al mismo hilo que el evaluador.

Existen además MaterialEvaluator (v0) y PieceSquareEvaluator (v1) como pasos intermedios testeados.

Un ejemplo de lectura de una puntuación:

~~~
material                 +100
tablas de casilla         +25
movilidad                 +18
seguridad del rey         -12
--------------------------------
total                    +131 cp
~~~

+131 cp significa aproximadamente una ventaja de 1,31 peones según este evaluador. No es una probabilidad de ganar: es la señal numérica con la que búsqueda compara alternativas.

### Static Exchange Evaluation (see)

See evalúa el balance material neto de una captura simulando la secuencia de recapturas en una **copia local** del tablero, eligiendo siempre el **atacante de menor valor** y permitiendo “no recapturar” (max(0, …)). Un resultado negativo marca captura perdedora (usada en quiescence y ordenación).

SEE responde si una captura parece sostenible materialmente:

~~~
peón captura caballo
  → peón rival recaptura
  → torre recaptura
  → siguiente atacante de menor valor recaptura
  → cada lado puede decidir parar
~~~

No sustituye la búsqueda completa; una clavada, un mate o una táctica posicional puede invalidar la intuición material. Sí elimina muchas capturas perdedoras obvias antes de gastar nodos en ellas.

---

## 6. Flujo de integración con LastMove

~~~mermaid
sequenceDiagram
    participant UI as HumanVsComputerScreen
    participant SVC as ComputerGameService
    participant AD as KnightshadeMoveEngine
    participant ENG as KnightshadeEngine
    participant SRCH as ParallelRootSearch
    SVC->>AD: chooseMove(ComputerMoveRequest)
    AD->>AD: FEN desde PositionSnapshot
    AD->>ENG: search(FEN, SearchLimits, StopSignal)
    ENG->>ENG: FenParser.parse → Board
    ENG->>SRCH: search(Board, limits, stop)
    SRCH-->>ENG: SearchResult
    ENG-->>AD: SearchResult
    AD-->>SVC: MoveCommand
    SVC-->>UI: estado actualizado
~~~

### 6.1 Cómo usa el modelo de dominio

El motor **reutiliza** cuatro value objects del dominio de LastMove como *kernel* compartido (son inmutables y no dependen de ningún framework):

| Tipo del dominio | Uso en el motor |
| --- | --- |
| Square | Coordenadas de casilla; conversión a/desde el índice mailbox |
| PieceColor | Color de pieza y del bando al mover |
| PieceType | Tipo de pieza, promoción y captura |
| CastlingRights | Derechos de enroque y actualización en make |

El motor **no** importa Fen, PositionSnapshot, MoveCommand, ChessGame ni ningún agregado/servicio del dominio: define sus propios Move, Position/Board y su propio parser FEN (FenParser).

### 6.2 Traducción en el adaptador

El adaptador (infrastructure/engine/knightshade) es infraestructura de LastMove y traduce entre ambos mundos:

- **Entrada:** PositionSnapshot → FEN (vía FenService.fromSnapshot) → Board (vía FenParser).
- **Salida:** Move del motor → MoveCommand del dominio (from, to, promotion como Optional).

KnightshadeMoveEngine implementa ComputerMoveEngine, ejecuta el coordinador de búsqueda en un hilo virtual (CompletableFuture.supplyAsync) y traduce cancelSearch() a StopSignal (AtomicBoolean), que todos los trabajadores consultan entre nodos. ParallelRootSearch crea y cierra sus propios hilos de plataforma por solicitud; el executor del adaptador sigue serializando sus solicitudes.

### 6.3 Registro automático en la UI

KnightshadeMoveEngineProvider es un @Component que implementa ComputerMoveEngineProvider. ComputerGameService.availableEngines() recoge todos los providers vía inyección de List<ComputerMoveEngineProvider>, por lo que **Knightshade aparece automáticamente en el selector de HumanVsComputerSetupOverlay** sin tocar la pantalla. También existe KnightshadeComputerEngineHealthCheck (probe de jugada legal) siguiendo el patrón de Sunfish.

---

## 7. Evolución por versiones

| Versión | Búsqueda | Evaluación | Optimización |
| --- | --- | --- | --- |
| v0 | Minimax | Material | Make/unmake, ordenación por capturas |
| v1 | Alfa-beta, quiescence | + PST | MVV-LVA, killers |
| v2 | Iterative deepening, PVS | — | Zobrist, TT, history, gestión de tiempo |
| v3 | Aspiration windows, null move, LMR | + movilidad, seguridad del rey | SEE |

Cada versión es funcional y testedada; las clases de búsqueda intermedias (MinimaxSearch, AlphaBetaSearch) se conservan como pasos de referencia.

---

## 8. Traza

KnightshadeMoveEngine registra en el log (slf4j, nivel INFO):

~~~
Knightshade search started: fen='…' maxTimeMs=500
Knightshade chose e2e4 score=12 depth=7 nodes=153212 elapsedMs=480 totalMs=485
~~~

La traza muestra la jugada elegida, la puntuación, la última profundidad completa, los nodos y el tiempo. Es útil para separar un problema de evaluación de un límite de tiempo demasiado corto.

---

## 9. Trabajo futuro

- Desarrollo, centro, estructura de peones, peones pasados y pareja de alfiles ya están implementados; el rey ahora adapta su evaluación a la fase.
- Pendientes: libro de aperturas, tablebases, bitboards y búsqueda paralela. La búsqueda actual sigue siendo de un hilo; sus tableros, heurísticas y TT no deben compartirse entre hilos sin un diseño específico de sincronización o aislamiento.
- Medición de esta revisión y comando de benchmark sin UI: [revisión de rendimiento](knightshade-performance-2026-09-06.md).

Las mejoras de v4 aumentan conocimiento ajedrecístico. Las de v5 se orientan sobre todo a procesar más nodos por segundo. Las interfaces actuales permiten evolucionar cualquiera de esos caminos sin acoplar el motor a la UI ni al dominio de LastMove.
