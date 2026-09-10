# Búsqueda paralela de Knightshade

La entrada es `KnightshadeEngine`; la coordinación está en `search/ParallelRootSearch.java`.
El núcleo recursivo sigue siendo `IterativeDeepeningSearch.pvSearch`, accesible desde cada
trabajador mediante `searchRootMove`. No se duplican las reglas de búsqueda profunda.

## Configuración y ciclo de vida

- Por defecto: `min(4, Runtime.getRuntime().availableProcessors())` participantes totales.
- Constructor explícito: `new KnightshadeEngine(1)`, `new KnightshadeEngine(2)`, etc.
- La propiedad JVM `-Dknightshade.threads=N` configura el constructor por defecto (1–32).
- N incluye el hilo coordinador. Los N−1 ayudantes son hilos de plataforma para trabajo de CPU.
- Las profundidades 1 y 2 se ejecutan en el coordinador para ahorrar costes de despacho.
- El límite de tiempo es único para toda la solicitud, incluida la preparación de trabajadores.
- El pool pertenece a una solicitud. Se espera su terminación antes de devolver el resultado,
  incluso ante un error o una interrupción; no quedan tareas ejecutándose en segundo plano.
- `KnightshadeMoveEngine` conserva su executor de un hilo virtual para recibir solicitudes;
  no necesita gestionar directamente los ayudantes internos.

El modo de un participante delega en la búsqueda secuencial existente. Es la referencia para
comparar resultados deterministas a profundidad fija. Las solicitudes concurrentes a la fachada
pública no comparten los trabajadores ni las cachés.

## Coordinación de variantes

En cada intento de aspiración se ordenan las jugadas y se explora primero la variante principal
con ventana completa. Si no produce un corte beta, el coordinador y los ayudantes toman
alternativas de un índice atómico. Cada alternativa lee el alpha vigente y empieza con una
ventana nula. Si mejora ese límite sin alcanzar beta, se confirma con ventana completa antes
de publicar el resultado. Leer un alpha que acaba de quedar desactualizado puede producir
trabajo extra, pero no justifica publicar una mejora sin confirmarla.

El mejor resultado y la actualización de alpha se publican bajo sincronización. Un corte beta
activa la parada cooperativa de ese intento. El coordinador espera todos los futuros antes de
ensanchar la ventana o comenzar la siguiente profundidad. El orden de finalización puede
variar, y las heurísticas selectivas locales pueden dar jugadas y puntuaciones distintas del
modo secuencial: no se garantiza identidad bit a bit con varios participantes.

| Estado | Propiedad / sincronización |
| --- | --- |
| Board, pila Undo y casillas de reyes | Copia independiente por trabajador |
| TT y caché del evaluador | Privadas por trabajador; persisten entre iteraciones de la solicitud |
| Repeticiones, killers e historial | Privados por trabajador |
| Siguiente alternativa y alpha | Atómicos |
| Mejor jugada y puntuación raíz | Publicación sincronizada |
| Corte del intento y cancelación de la solicitud | Banderas atómicas distintas |
| Contadores de nodos | Privados, sumados tras esperar a los trabajadores |

No se comparte la TT en esta versión. Se evita así que otra variante publique una clave o
puntuación mientras un trabajador la lee, a costa de memoria adicional y menor reutilización
de transposiciones entre variantes. La memoria crece con el número de participantes.

## Parada y errores

Todos consultan el mismo plazo y señal externa; un resultado parcial nunca sustituye la última
profundidad completa. Si la solicitud se detiene antes de terminar profundidad 1, se devuelve
una jugada legal de reserva con profundidad 0. La señal externa debe admitir consultas concurrentes,
como ya hace `AtomicBoolean::get` en el adaptador.

Un fallo en un trabajador activa la cancelación del resto, se espera su terminación y se propaga
la excepción. Al interrumpir el coordinador se cancelan los ayudantes y se preserva la marca de
interrupción al retornar. Las copias de trabajo se descartan al acabar la solicitud.

## Verificación sin interfaz

```sh
mvn clean test
# Profundidad fija 6, 1/2/4/6 participantes:
java -cp target/classes src/test/java/com/knightshade/engine/benchmark/SearchBenchmark.java 6 0 1
java -cp target/classes src/test/java/com/knightshade/engine/benchmark/SearchBenchmark.java 6 0 2
java -cp target/classes src/test/java/com/knightshade/engine/benchmark/SearchBenchmark.java 6 0 4
java -cp target/classes src/test/java/com/knightshade/engine/benchmark/SearchBenchmark.java 6 0 6
# Un segundo para cada posición, cuatro participantes:
java -cp target/classes src/test/java/com/knightshade/engine/benchmark/SearchBenchmark.java 6 1000 4
```

`ParallelRootSearchTest` comprueba equivalencia del modo de un hilo, resultados a poca
profundidad, mate/ahogado, repetición, reserva legal, solicitudes concurrentes y límite de
tiempo. Mediante barreras fuerza a dos trabajadores a estar activos a la vez para comprobar
cancelación e interrupción durante la tercera profundidad. También inyecta un fallo y verifica
que los hilos observados han terminado antes de devolver el control. Las regresiones existentes
de sacrificios de dama se ejecutan con el motor paralelo por defecto.

El número de nodos agregado incluye trabajo especulativo y transposiciones repetidas entre
trabajadores. El rendimiento debe evaluarse junto con profundidad completada y resultados de
torneo; no permite deducir una ganancia de ELO por sí solo.

## Resultados locales, 2026-09-07

`mvn clean test`: **469 tests, 0 fallos, 0 errores, 2 omitidos**. No se abrió la aplicación.

Benchmark en Java 22, ocho procesadores disponibles, cuatro posiciones, 1000 ms por posición,
tres procesos por configuración y calentamiento previo a profundidad 3. Las ejecuciones se
hicieron secuencialmente, sin la suite de tests en paralelo. Mediana del total de nodos de las
cuatro posiciones por ejecución:

| Participantes | Nodos agregados | Respecto a 1 | Profundidades completas observadas: inicial / apertura / táctica / final |
| ---: | ---: | ---: | --- |
| 1 | 1676804 | referencia | 7 / 7 / 6 / 16 |
| 2 | 1959586 | +17 % | 7–8 / 7 / 6 / 15–16 |
| 4 | 2431049 | +45 % | 7–8 / 7 / 6 / 16 |
| 6 | 2739317 | +63 % | 8 / 7 / 6 / 16 |

Esto mide trabajo realizado dentro del mismo plazo, no una aceleración lineal ni una ganancia
garantizada en cada posición. Por ejemplo, dos participantes completaron una profundidad menos
en el final en una ejecución. Se conserva el valor por defecto de hasta cuatro participantes
como punto de partida para torneo; seis quedan disponibles mediante configuración. Estos
resultados no respaldan las estimaciones previas de 2–5 veces de aceleración o de 50–150 ELO.
