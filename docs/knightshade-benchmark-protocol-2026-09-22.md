# Protocolo de benchmark de Knightshade

El arnés SearchBenchmark registra muestras individuales y resúmenes con mediana
y desviación estándar. Cada CSV incluye el identificador de build, JDK, VM,
sistema operativo, arquitectura, procesadores y memoria máxima de la JVM.

## Corpus

El corpus integrado contiene dos aperturas, dos posiciones tácticas y dos finales.
El conjunto representative selecciona una de cada fase: opening-italian,
tactical-queen-sacrifice y endgame-pawns.

Cuando esté disponible el PGN de Knightshade contra Stockfish 7, convertir su
línea oficial en posiciones FEN cronológicas y guardar un fichero de historial
con este formato:

    match-knightshade-stockfish7|match
    <FEN inicial>
    ...
    <FEN de la posición que se busca>

La última FEN es la raíz y todas las FEN, incluida esa última, se entregan al
motor como historial de posiciones para reproducir reglas de repetición.

## Ejecución

Compilar antes de cada tanda con mvn test-compile.

La equivalencia fija con un trabajador comprueba jugada, score y profundidad con
la telemetría apagada y encendida:

    java -Dknightshade.build=BUILD_ID -cp target/test-classes:target/classes \
      com.knightshade.engine.benchmark.SearchBenchmark \
      --label baseline --suite all --depth 8 --threads 1 --repetitions 5 \
      --warmups 2 --telemetry both --output docs/benchmarks/baseline-fixed-depth.csv

La medición real usa cinco segundos, tres fases, 1/2/4 trabajadores y los dos
modos de telemetría:

    java -Dknightshade.build=BUILD_ID -cp target/test-classes:target/classes \
      com.knightshade.engine.benchmark.SearchBenchmark \
      --label baseline --suite representative --time-ms 5000 --threads 1,2,4 \
      --repetitions 5 --warmups 2 --telemetry both \
      --output docs/benchmarks/baseline-time-5s.csv

Para un candidato, repetir exactamente los dos comandos con un build y etiqueta
distintos. Alternar el orden por tanda: baseline/candidato/baseline/candidato,
o candidato/baseline/candidato/baseline en la siguiente. No comparar una
telemetría encendida con una apagada ni mezclar corpus, JVM, memoria o equipo.

El arnés admite --history-fen path/al/historial.fen para añadir la partida
reproducible al corpus. Ejecutar JFR en una tanda aparte para CPU y asignaciones;
no añadir cronómetros por nodo.
