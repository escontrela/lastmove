# Optimización conservadora de quietud — paso 3

Fecha: 2026-09-22.

El candidato `8435191-dirty-step3` se midió con la misma JVM, heap de 2 GiB,
equipo y corpus que la línea base `8435191-dirty-step2`. La base y el candidato
se ejecutaron en tandas consecutivas con orden alternado: base primero y
candidato después. Las muestras crudas y el entorno quedan guardados en los CSV.

## Cambio aplicado

Fuera de jaque, la búsqueda calcula stand-pat antes de materializar movimientos.
Cuando alcanza beta consulta si existe una jugada legal: devuelve tablas ante
ahogado y beta en cualquier otra posición. La consulta de legalidad ahora recorre
las piezas y abandona al hallar la primera jugada legal, sin construir la lista
completa del tablero. Si stand-pat no corta, se conserva la generación, el orden
y el conjunto previo de capturas, promociones y quiet checks.

Las rutas de jaque, repetición y cincuenta jugadas se mantienen antes de
stand-pat. No se ha introducido una caché SEE: queda condicionada a que una traza
JFR la sitúe entre los costes relevantes.

## Equivalencia fija

A profundidad 8, un worker, cinco repeticiones, corpus completo y ambos modos de
telemetría, las 60 muestras del candidato coinciden con la base en profundidad,
jugada y score. Las medianas sin telemetría bajan en cinco posiciones medibles:

| Posición | Base | Candidato | Cambio |
| --- | ---: | ---: | ---: |
| Apertura inicial | 554 ms | 489 ms | -11,7 % |
| Apertura italiana | 1.265 ms | 1.115 ms | -11,9 % |
| Sacrificio de dama | 3.498 ms | 3.263 ms | -6,7 % |
| Final de torres | 244 ms | 220 ms | -9,8 % |
| Final de peones | 22 ms | 19 ms | -13,6 % |

La posición de mate táctico termina en menos de un milisegundo en ambas versiones
y no sirve para comparar tiempo. La mejora supera la dispersión registrada en las
posiciones largas; la de final de peones es demasiado corta para una conclusión
fina, aunque no presenta regresión.

## Búsqueda de cinco segundos

Con cinco repeticiones, conjunto representativo, 1/2/4 workers y ambos modos de
telemetría, todas las medianas de NPS por posición aumentaron. El NPS agregado y
la profundidad media fueron:

| Workers | Telemetría | Base NPS | Candidato NPS | Cambio | Profundidad base/candidato |
| ---: | --- | ---: | ---: | ---: | ---: |
| 1 | apagada | 437.437 | 497.188 | +13,7 % | 12,00 / 12,00 |
| 1 | encendida | 439.103 | 495.457 | +12,8 % | 12,00 / 12,00 |
| 2 | apagada | 559.724 | 639.456 | +14,2 % | 12,07 / 12,13 |
| 2 | encendida | 570.337 | 631.700 | +10,8 % | 11,93 / 12,00 |
| 4 | apagada | 674.573 | 754.602 | +11,9 % | 12,20 / 12,07 |
| 4 | encendida | 677.190 | 736.121 | +8,7 % | 12,07 / 12,00 |

La profundidad se mantiene estable. La pequeña variación con cuatro workers está
dentro de una iteración y no se acompaña de una regresión sistemática de NPS.

La segunda tanda invirtió el orden (candidato y después base), con la generación
anterior aislada fuera del árbol de trabajo. El NPS agregado de esa base posterior
fue 435.813/430.495 (1 worker), 582.070/572.647 (2) y 672.808/652.903 (4),
apagada/encendida respectivamente. El candidato continúa por encima en los seis
casos, entre +9,8 % y +15,1 %, y en todas las medianas por posición. La mejora
observada excede el objetivo inicial de ingeniería del 5 %, sin implicar por sí
sola una ganancia de fuerza Elo.

## Artefactos

- `baseline-step2-fixed-depth.csv`
- `baseline-step2-time-5s.csv`
- `candidate-step3-fixed-depth.csv`
- `candidate-step3-time-5s.csv`
- `baseline-step2-after-candidate-time-5s.csv`
