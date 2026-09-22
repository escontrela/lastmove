# Línea base de Knightshade — paso 2

Fecha: 2026-09-22.

Build: 8435191-dirty-step2. El sufijo dirty identifica que incluye los cambios
locales del paso 1 y del arnés del paso 2; no es un commit reproducible todavía.

Entorno registrado en los CSV: OpenJDK 22+36-2370, OpenJDK 64-Bit Server VM,
macOS aarch64, 8 procesadores disponibles y máximo de heap de 2 GiB.

## Equivalencia a profundidad fija

El corpus completo contiene dos aperturas, dos tácticas y dos finales. Se
calentó dos veces a profundidad 3 y se hicieron cinco repeticiones a profundidad
8 con un worker, en ambos modos de telemetría.

Las 60 muestras de ejecución conservan la misma profundidad, jugada y score
con telemetría apagada y encendida. Los tiempos de una sola muestra corta no se
usan para estimar el coste de telemetría.

Resultado detallado: baseline-step2-fixed-depth.csv.

## Línea base temporal de cinco segundos

El conjunto representativo ejecutó apertura italiana, sacrificio de dama y final
de peones. Tras dos calentamientos, se hicieron cinco repeticiones por posición,
con 1/2/4 workers y telemetría apagada/encendida. Son 90 búsquedas medidas.

| Workers | Telemetría | NPS agregado | Profundidad media |
| ---: | --- | ---: | ---: |
| 1 | apagada | 437.436 | 12,00 |
| 1 | encendida | 439.102 | 12,00 |
| 2 | apagada | 559.724 | 12,07 |
| 2 | encendida | 570.336 | 11,93 |
| 4 | apagada | 674.573 | 12,20 |
| 4 | encendida | 677.190 | 12,07 |

Las medianas por posición, worker y modo se conservan en
baseline-step2-time-5s.csv. A modo de contraste, con telemetría apagada:

- Apertura italiana: mediana 303.153 NPS con un worker, 368.905 con dos y
  415.045 con cuatro. La profundidad mediana se mantiene en 9.
- Sacrificio de dama: 298.372, 393.622 y 399.013 NPS. La profundidad se mantiene
  en 8.
- Final de peones: 722.777, 923.334 y 1.219.582 NPS. La profundidad alcanza
  19 con uno o dos workers y 19–20 con cuatro.

No hay aún un candidato, por lo que estos resultados son una referencia y no
demuestran una ganancia de fuerza. El PGN de la partida contra Stockfish 7 no
estaba disponible; el arnés ya admite un historial FEN generado desde ese PGN.

## Artefactos

- baseline-step2-fixed-depth.csv
- baseline-step2-time-5s.csv
- knightshade-benchmark-protocol-2026-09-22.md
