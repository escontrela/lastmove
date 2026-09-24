# Knightshade: comprobación local de telemetría y ponderación

Fecha de ejecución: 2026-09-24. Registro generado con `SearchBenchmark`, profundidad fija 7,
cinco repeticiones, dos calentamientos hasta profundidad 3, un participante y suite representative
(apertura italiana, táctica de sacrificio de dama y final de peones). El listener de la variante
`telemetry=on` recibe snapshots en memoria y no hace I/O.

Entorno: Java `22+36-2370`, OpenJDK 64-bit Server VM, macOS/aarch64, 8 procesadores disponibles,
heap máximo informado de 2 GiB. Los datos crudos se escribieron en
`/tmp/knightshade-telemetry-benchmark-2026-09-24-depth7.csv`.

| Posición | Profundidad y nodos (off/on) | Mediana elapsed off/on | Mediana NPS off/on |
| --- | ---: | ---: | ---: |
| Apertura italiana | 7 / 119 053 en ambos modos | 336 / 336 ms | 354 324 / 354 324 |
| Táctica | 7 / 420 846 en ambos modos | 1 249 / 1 252 ms | 336 946 / 336 138 |
| Final de peones | 7 / 5 461 en ambos modos | 7 / 7 ms | 780 142 / 780 142 |

En esta muestra no cambió la profundidad, el movimiento, el score ni los nodos entre modos. La
diferencia de mediana fue 0 ms en apertura y final, y +3 ms (+0,24 %) en la táctica. El final es
demasiado corto para medir latencia con precisión. Esto no demuestra coste cero ni sustituye una
prueba estadística larga; sí comprueba que el observer no añade trabajo por nodo ni cambia el árbol
en modo secuencial. El límite operativo sigue siendo investigar una regresión sostenida superior
al 2 %.

Este benchmark compara la emisión normal de snapshots con un listener no-op; no mide el acierto de
ponderación, la carga de JavaFX ni I/O de exportación. El punto de decisión se cubre con pruebas de
integración: una predicción validada incrementa HIT, una respuesta distinta incrementa MISS, y la
profundidad se agrega únicamente cuando se adopta una continuación completa. Para que esas dos
tarjetas avancen en una partida real, deben estar activos tanto Blood Pressure como Ponder en
Settings → Knightshade; las muestras aparecen al llegar la respuesta rival, no mientras el motor
está especulando.
