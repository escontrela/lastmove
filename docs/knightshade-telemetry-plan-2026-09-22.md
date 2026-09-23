# Knightshade: análisis de telemetría y plan de mejora

Fecha: 2026-09-22. Código revisado: HEAD `8435191` y árbol de trabajo actual.
Fuente: `/Users/davidpe/Downloads/knightshade-telemetry.csv` y captura de Blood Pressure.
Alcance: análisis y planificación; no se modifica el motor.

## Decisión propuesta

Priorizar una optimización conservadora de `QuiescenceSearch`: evitar generar y
validar listas completas de movimientos cuando el nodo ya puede terminar por
stand-pat, manteniendo la detección exacta de ahogado. Primero corregir la
identificación y los contadores de las búsquedas para poder medir el resultado.

La oportunidad está sustentada por el volumen de quietud y por trabajo evitable
en el código. El CSV no demuestra cuánto tiempo ahorrará ni una ganancia de Elo.
No permite atribuir las tablas a un error concreto de evaluación.

## Integridad y método

- Hay 587 muestras y una sola etiqueta `sessionStartedAt`.
- Se segmentan por la fila terminal `TIME_LIMIT` o `CANCELLED`: resultan 60
  búsquedas, 58 limitadas por tiempo y dos etiquetadas como canceladas. Esta regla
  sirve para este archivo; el formato futuro debe tener `searchId` y tipo de evento.
- Se toma únicamente la última fila de cada búsqueda para sumar contadores.
  Las filas de profundidad son acumulativas, no costes independientes.
- Las primeras 27 búsquedas contienen 25 turnos de unos cinco segundos y dos
  búsquedas de dos milisegundos con puntuaciones 999997 y 999999. Son valores de
  mate según `Scores.MATE`, no centipeones ordinarios. Después aparece otra
  apertura: `e2e4`, `g1f3`, `b1c3`, `f1c4`.
- El segundo bloque tiene 33 búsquedas de cinco segundos. El usuario confirmó
  que corresponde a la partida de tablas contra Stockfish 7. La segmentación se
  dedujo del archivo y se contrastó con esa confirmación.
- `timestamp` se exporta como inicio de sesión + tiempo de la búsqueda, de modo
  que retrocede al cambiar de búsqueda. No es una cronología real de la partida.
- `timeLimitCount`, `completedCount` y `cancelledCount` repiten los totales de
  sesión en cada fila. No se suman. Los 527 `COMPLETED` son eventos de profundidad.
- Faltan FEN, PGN, historial rival, PV, configuración exacta del oponente y versión
  del motor exportador. El código actual ya exporta una columna NPS ausente en
  este CSV; la correspondencia con el binario que produjo los datos no es exacta.

## Resultados calculados

Ratios ponderadas mediante suma de numeradores / suma de denominadores. NPS es
la suma de contadores de nodos dividida por la suma del tiempo de las búsquedas.

| Métrica | Archivo completo | Partida de tablas confirmada |
| --- | ---: | ---: |
| Búsquedas | 60 | 33 |
| Tiempo acumulado de búsqueda | 290,005 s | 165 s |
| mainNodes | 66.864.278 | 46.287.245 |
| qNodes | 108.600.197 | 69.356.990 |
| qNodes / (mainNodes + qNodes) | 61,89 % | 59,97 % |
| NPS agregado | 605.039 | 700.874 |
| Mediana profundidad completada | 9 | 9 |
| Aciertos TT / consultas | 8,53 % | 9,08 % |
| Cortes TT / consultas | 3,30 % | 3,61 % |
| Cortes null-move / intentos | 85,52 % | 85,74 % |
| Aciertos caché evaluación / accesos | 24,07 % | 25,56 % |
| Reintentos de aspiración acumulados | 209 | 84 |
| Trabajo posterior a última profundidad completa | 30,31 % | 27,33 % |

El último porcentaje se obtiene restando los nodos de la última iteración completa
de los nodos terminales y dividiendo las sumas. Es trabajo de la siguiente
iteración que no llegó a publicarse; no implica que deba eliminarse ni que ese
porcentaje se convierta automáticamente en ahorro.

`mainNodes` incluye entradas al horizonte que luego llaman a quietud. Por tanto,
estas ratios describen contadores de llamadas, no posiciones únicas ni porcentaje
de CPU. Los cuatro trabajadores aportan nodos agregados, incluido trabajo repetido.

En el segundo bloque la proporción de quietud llega al 78,53 %. Al final se repite
la mejor jugada de torre `b7b5` / `b5b7` y la última evaluación es +161 desde el
lado que mueve. Sin movimientos rivales y estados completos no se puede concluir
triple repetición ni afirmar que había una victoria desaprovechada.

## Hallazgos en el código

### 1. Quietud: trabajo anticipado que puede evitarse

En `search/QuiescenceSearch.java`, `searchInternal` genera todos los movimientos
legales cuando `quietChecks=true`, filtra capturas y solo después evalúa stand-pat.
En las llamadas restantes genera las capturas también antes de stand-pat.
Si la evaluación permite cortar, parte de ese trabajo no se utiliza.

La comprobación de ahogado debe preservarse: ya existe una regresión explícita
`recognizesStalemateBeforeStandPatEvenWithANarrowWindow`. No basta con mover un
`return beta` por delante de la generación. `LegalMoveGenerator.hasLegalMove`
puede validar solo hasta encontrar una jugada legal, pero actualmente materializa
toda la lista pseudo-legal y hace make/unmake; su ventaja debe medirse.

Además, SEE puede calcularse tanto al ordenar una captura como al decidir si
explorarla. `See.ge` llama a la evaluación completa, que copia 64 casillas.
Reutilizar ese resultado por nodo es una segunda optimización posible, condicionada
a un perfil que confirme su coste. Mantener la política actual para promociones,
jaques y capturas de dama/torre.

### 2. Telemetría: defectos confirmados en el árbol revisado

- `ParallelRootSearch` establece `cancelled=true` en el `finally` de limpieza y
  después calcula `stopReason` usando esa misma señal. Una terminación normal por
  mate o profundidad queda clasificada como `CANCELLED` si no venció el tiempo.
  Es consistente con las dos filas de mate del CSV; no demuestra cancelación real.
- En `IterativeDeepeningSearch`, `lmrApplications` se incrementa antes de descartar
  movimientos que dan jaque. Cuenta candidatos, no solo reducciones ejecutadas.
  El cociente observado de re-búsquedas (0,21 % global) no justifica endurecer LMR.
- Las confirmaciones PVS de raíz de `searchRootMove` y de la raíz secuencial no
  incrementan `pvsResearches`; falta parte del coste.
- `effectiveWorkers=4` se publica también en profundidades 1 y 2, ejecutadas solo
  por el coordinador. Es capacidad prevista, no utilización medida.
- La ruta directa de telemetría secuencial debe revisarse también: decide la causa
  usando la señal externa y puede ignorar el plazo interno.

### 3. Otras hipótesis, de menor prioridad

La TT es privada por trabajador, se consulta también al horizonte y no se llena
con resultados de quietud. El porcentaje de aciertos global no demuestra que sea
pequeña. Separar consultas interiores/horizonte y medir ocupación y reemplazos
antes de ampliar memoria o compartirla. Reutilizar tablas entre turnos exigiría
resolver la dependencia del historial de repeticiones y del contador de 50 jugadas.

No hay evidencia suficiente para retocar pesos de evaluación, desactivar jaques
en quietud, aumentar LMR o introducir una preferencia artificial contra tablas.

## Plan de implementación

### Paso 1 — Hacer reproducible la medición

Archivos: `SearchTelemetrySnapshot`, `SearchStats`, `ParallelRootSearch`,
`IterativeDeepeningSearch`, `KnightshadeTelemetryService`, `BloodPressureWindowService`
y sus tests. Los dos últimos contenían cambios locales al comenzar la revisión;
comprobar su estado actualizado antes de implementar e integrar sin sobrescribir
trabajo concurrente.

1. Separar eventos `ITERATION_COMPLETED` y `SEARCH_FINISHED`; capturar la causa
   terminal antes de la limpieza de trabajadores. Conservar cancelación externa,
   plazo, mate y profundidad como situaciones distinguibles.
2. Añadir gameId/searchId, FEN raíz, lado, número de jugada, instante real de inicio,
   instante de muestra, límites, versión/build y tipo de score (cp/mate). Exportar
   PGN o historial reproducible asociado para reconstruir repeticiones.
3. Contar LMR después de decidir `reduced`, PVS en todas las rutas y distinguir
   capacidad de trabajadores de actividad real. Mantener snapshots acumulativos
   y derivar incrementos por iteración de forma explícita.
4. Añadir contadores opcionales de entradas de quietud, stand-pat cutoffs,
   comprobaciones de ahogado, listas generadas, quiet checks, SEE y podas SEE.
   Usar JFR para CPU/asignaciones, evitando cronometrar cada nodo.

Para perfilar la referencia se usará JFR a nivel de proceso, no instrumentación
por nodo. Ejecutar SearchBenchmark con
XX:StartFlightRecording=filename=target/knightshade-baseline.jfr,settings=profile
y después jfr summary target/knightshade-baseline.jfr. La comparación
candidato/baseline conservará JDK, equipo, argumentos y corpus. Revisar los
eventos jdk.ExecutionSample y jdk.ObjectAllocationInNewTLAB antes de alterar
SEE, TT o generación de jugadas.

Aceptación: una fila terminal por búsqueda; mate/profundidad no son cancelación;
tiempo agotado y cancelación real tienen tests; timestamps e IDs sobreviven a dos
partidas consecutivas; telemetría encendida/apagada conserva resultados secuenciales.

### Paso 2 — Línea base y corpus

Ampliar `src/test/java/com/knightshade/engine/benchmark/SearchBenchmark.java` con
posiciones de apertura, táctica y finales. Incorporar el PGN de esta partida cuando
esté disponible, incluyendo historial al reproducirla. El CSV no basta para ello.

Ejecutar baseline y candidato con el mismo JDK, equipo, memoria y configuración:
profundidad fija con un trabajador para equivalencia; límite de 5 s con 1/2/4
trabajadores para rendimiento real. Calentar JVM, repetir al menos cinco veces,
alternar orden de versiones y registrar mediana/dispersión. Medir on/off de
telemetría por separado. Guardar build exacto y resultados antes de tocar el núcleo.

### Paso 3 — Optimización conservadora de quietud

Archivos principales: `QuiescenceSearch`, y `LegalMoveGenerator` solo si el perfil
justifica mejorar su consulta de existencia de movimientos.

1. Preservar primero paradas, repeticiones, regla de 50 jugadas y evasiones de jaque.
2. Fuera de jaque, calcular stand-pat antes de materializar listas completas.
3. Si stand-pat alcanza beta, comprobar existencia de jugada legal: devolver cero
   si es ahogado y beta en otro caso. Evitar la generación completa en esta ruta.
4. Si no corta, generar las listas necesarias y mantener el control de ahogado y
   el mismo orden y conjunto de capturas, promociones y jaques que hoy.
5. Si la consulta `hasLegalMove` resulta costosa, implementar una salida temprana
   real en generación/validación; no penalizar todos los nodos con una segunda
   generación completa. Medir el balance por posiciones.
6. Evaluar reutilización local de SEE en una entrega separada, únicamente si JFR
   la sitúa entre los costes relevantes. No añadir una caché global compartida.

Tests: ahogado con stand-pat sobre beta y bajo beta, mate y evasiones, promoción y
subpromoción, en passant, capturas que dan jaque, sacrificios de piezas mayores,
repetición/50 jugadas y restauración de tablero/historial tras cancelación.
Comparar score y jugada a profundidad fija con un trabajador sobre el corpus.

Aceptación: `mvn clean test` pasa; no hay regresión táctica; se reducen generaciones
o asignaciones en la ruta de corte; tiempo mediano a profundidad fija mejora más
que el ruido medido, sin regresión sistemática por tipo de posición. Un 5 % puede
servir como objetivo inicial de ingeniería, no como ganancia prometida. A cinco
segundos medir profundidad completa y estabilidad, además de NPS.

### Paso 4 — Validar fuerza y decidir siguientes mejoras

Torneo baseline/candidato con aperturas emparejadas y colores intercambiados,
mismo tiempo, hash y trabajadores. Una tanda inicial de 200 parejas sirve para
detectar regresiones gruesas; no prueba mejoras pequeñas. Ampliar muestra y
reportar intervalo de confianza o usar un protocolo secuencial predefinido antes
de afirmar ganancia de Elo. Repetir contra Stockfish 7 con su configuración exacta
como contraste adicional. No aceptar un cambio solo por producir más nodos.

Si el perfil señala otro cuello de botella, repriorizar antes de optimizar SEE o
TT. Una política de tiempo adaptativa requiere reloj restante e incremento y una
evaluación propia; consumir cinco segundos por jugada no es por sí mismo un fallo.

## Referencias

- CSV original, filas 241–245: evaluaciones de mate; fila 246: inicio del segundo
  bloque inferido (filas numeradas incluyendo cabecera).
- Código local: `src/main/java/com/knightshade/engine/search/QuiescenceSearch.java`,
  `ParallelRootSearch.java`, `IterativeDeepeningSearch.java`; paquetes `movegen`,
  `ordering`, `see` y `transposition` del motor.
- [Stockfish, implementación oficial actual de búsqueda](https://github.com/official-stockfish/Stockfish/blob/master/src/search.cpp):
  su qsearch también comprueba stand-pat antes del selector de movimientos.
  Referencia conceptual consultada el 2026-09-22; no describe Stockfish 7 ni
  sustituye las reglas y regresiones específicas de Knightshade.
