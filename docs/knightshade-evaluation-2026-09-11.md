# Knightshade: revisión de evaluación, 2026-09-11

## Diagnóstico

La búsqueda de producción ya incluye profundización iterativa, PVS, aspiración, TT, movimiento
nulo, LMR, extensiones de jaque, quiescencia y trabajadores separados en la raíz. Añadir esas
técnicas de nuevo no resuelve la diferencia de fuerza. Se encontraron tres omisiones concretas
en la evaluación: movilidad sobre casillas dominadas por peones enemigos, seguridad del rey
limitada al escudo de peones y ausencia de valoración específica de columnas para torres.

## Implementación

- Movilidad: conserva los pesos anteriores, pero excluye ocupación propia y control de peones
  rivales. Las líneas deslizantes terminan en el primer ocupante, aunque esa casilla se excluya
  del área de movilidad. No se confunden estos ataques geométricos con movimientos legales.
- Seguridad del rey: cuenta piezas enemigas que alcanzan el rey o su anillo inmediato. La
  penalización crece con su coordinación y contactos; se limita a 300 centipeones antes de
  escalar por fase y se reduce a la mitad sin dama atacante. El escudo existente se conserva.
- Torres: 12 centipeones por columna semiabierta, 24 por abierta y 20 por séptima fila cuando
  el rey rival sigue en su última fila. Son pesos iniciales, pendientes de ajuste por partidas.
- Eficiencia: máscaras precalculadas para caballos, reyes y peones; un espacio reutilizable
  de ataques por evaluador alimenta movilidad y seguridad del rey. Cada trabajador conserva
  su propia instancia. No hay nuevas asignaciones de ese espacio en cada nodo.
- Búsqueda: la ordenación y sus cálculos SEE se realizan después del intento de movimiento
  nulo; se evita ese trabajo si ya se ha encontrado un corte.

No se incorporan libros de aperturas, tablas de finales, redes, dependencias ni cambios de
presupuesto de tiempo o número de hilos. La integración usa el evaluador de producción y
afecta tanto a la búsqueda secuencial como a los trabajadores paralelos.

Como referencia conceptual se consultó la [evaluación de Stockfish 7](https://github.com/official-stockfish/Stockfish/blob/sf_7/src/evaluate.cpp),
que contempla área de movilidad, ataques al entorno del rey y columnas de torres. El código
y los pesos de esta revisión son propios; no se han incorporado archivos ni tablas de Stockfish.

## Validación y límites

Las pruebas nuevas contrastan influencia geométrica con la detección independiente de ataques
del tablero durante partidas legales reproducibles, incluidas posiciones con enroque, en passant
y promociones. También comprueban simetría por intercambio de colores, control de peones,
presión sobre el rey, actividad de torres y concordancia entre evaluación compartida/cacheada
y términos independientes después de movimientos y deshacer.

La evaluación sigue siendo heurística: no calcula por sí sola ataques forzados ni movilidad
legal de piezas clavadas. Las ponderaciones no están ajustadas mediante un torneo. Las pruebas
funcionales y los benchmarks no permiten afirmar una ganancia de ELO ni que ya supere a
Stockfish 7. Para comprobar ese objetivo hacen falta partidas con colores alternos y los mismos
límites de tiempo, hardware y configuración del rival (versión 7 o nivel de habilidad 7).
