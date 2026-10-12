# Knightshade: revisión de profundidad y fuerza, 2026-10-04

## Conclusión

Las modificaciones recientes son candidatos a mejora, no ganancias demostradas de Elo.
Las pruebas unitarias protegen comportamientos concretos; no miden fuerza de juego.
La prioridad es corregir errores que descartan recursos tácticos, conservar los recursos
de búsqueda al aprovechar ponder y reducir trabajo innecesario en el horizonte.
No se han ejecutado benchmarks ni partidas automáticas para esta revisión.

## Evidencia disponible

Se leyeron sin modificar `knightshade-search-summary.csv` y `knightshade-telemetry.csv`
en la raíz del proyecto. Contienen una partida con 63 búsquedas terminadas del
2 de octubre, entre 14:43 y 14:52 UTC (16:43–16:52 en Madrid).

Cada solicitud indica 5000 ms de presupuesto. Hay 53 terminaciones por tiempo y
10 por mate. Las versiones y builds figuran como `development`: no permiten demostrar
qué combinación exacta de cambios estaba ejecutándose ni hacer una comparación antes/después.
No se dispone aquí de la identidad/configuración del rival ni de su evaluación independiente.

Se agregaron únicamente los eventos `SEARCH_FINISHED`, evitando sumar de nuevo las
iteraciones acumuladas. Los eventos `PONDER_HIT` se enlazaron por `searchId`.

| Hallazgo | Valor observado | Interpretación y límite |
| --- | ---: | --- |
| Aciertos ponder | 23 | Los 23 terminan con un trabajador efectivo. |
| Terminaciones por tiempo | 34 con cuatro trabajadores; 19 con uno | La continuación ponder altera el paralelismo del turno real. No prueba por sí sola que pierda frente a empezar de cero con cuatro. |
| Nodos principales | 61 822 572 | Suma de contadores terminales. |
| Nodos de quietud | 91 527 859 | 59,69 % de `mainNodes + qNodes`; porcentaje de nodos, no de CPU. |
| Jugadas tranquilas comprobadas como posibles jaques | 200 323 186 | Se prueban mediante make/check/unmake. Oportunidad de evitar trabajo, no medición de tiempo consumido. |
| Aciertos TT / consultas | 13,97 % | No distingue capacidad, distribución de claves e historial. |
| Cortes TT / consultas | 4,45 % | Aumentar la tabla sin más no garantiza una mejora. |
| Repeticiones de búsqueda LMR / aplicaciones | 0,168 % | No demuestra que las reducciones sean seguras: las jugadas descartadas erróneamente no se observan aquí. |

Las profundidades terminales más frecuentes son 9 y 10, con 20 búsquedas cada una.
Las profundidades bajas del final incluyen mates detectados y no deben interpretarse
como una caída de rendimiento.

## 1. Error táctico confirmado en SEE y corregido

`See.leastValuableAttacker` consideraba atacantes geométricos sin comprobar la seguridad
de su rey y asignaba al rey el valor material cero para ordenar recapturas.
`QuiescenceSearch.shouldSearchCapture` utiliza SEE negativo para descartar capturas
ordinarias que no dan jaque. Por tanto, un defensor ilegal puede ocultar una captura
buena en el horizonte y también empeorar su orden en la búsqueda principal.

Dos reproducciones, sin búsqueda de rendimiento:

- `8/8/4k3/3p4/5N2/8/8/K2R4 w - - 0 1`, `f4d5`: el rey negro no puede recapturar
  porque la torre blanca protege d5.
- `4k3/8/8/3pr3/5N2/8/8/K3R3 w - - 0 1`, `f4d5`: la torre negra no puede abandonar
  la columna e porque expone a su rey.

En ambos casos la generación legal confirma que no hay recaptura en d5. SEE devolvía
-220 centipeones en lugar de +100. Las nuevas pruebas fallaron con el código anterior.
La corrección verifica la seguridad del rey tras cada recaptura candidata, considera
el rey después de las demás piezas y termina la secuencia cuando el rey recaptura
legalmente. También contabiliza una promoción a dama durante las recapturas.

Esto corrige casos concretos; SEE sigue siendo una aproximación de intercambios en
una casilla, no una búsqueda táctica completa. Las comprobaciones adicionales tienen
coste por llamada, que no se ha medido. No se atribuye una ganancia de Elo al cambio.

Validación final: `mvn clean test`, 539 pruebas, 0 fallos, 0 errores y 2 omitidas.

## 2. Siguiente prioridad: ponder que continúa en paralelo

`KnightshadeMoveEngine.resumeOrSearch` pasa `1` a `prepared.resume` y devuelve su
resultado directamente. Además, el propietario de esa continuación se construye con
un único participante y `searchContinuation` usa la búsqueda secuencial. Cambiar solo
el argumento de telemetría no añadiría trabajadores.

La solución debe transferir la raíz, historial, TT, mejor jugada y última profundidad
completa al coordinador paralelo una vez detenido y unido el trabajador especulativo.
El coordinador podrá incorporar ayudantes y continuar desde la profundidad siguiente,
con un único plazo para el turno real. Deben conservarse los casos de cancelación,
respuesta distinta y dos partidas concurrentes.

## 3. Horizonte y ordenación: gastar menos para buscar lo mismo

Actualmente la entrada a quietud pide todas las jugadas legales para identificar
capturas, promociones y jaques tranquilos. Después simula las tranquilas una a una.
Las evasiones de jaque se exploran sin el ordenador de movimientos.

Primer cambio recomendado: generar candidatos a jaque directamente, incluyendo
jaques descubiertos y enroques, y comprobar legalidad solo cuando corresponda. Verificar
que el conjunto coincide con el filtro actual antes de cambiar selectividad. Ordenar
evasiones y reutilizar SEE entre ordenación y filtro evita otro trabajo repetido.

En búsqueda principal, `MvvLvaMoveOrderer` puntúa y ordena toda la lista antes de explorar
la primera jugada. Una selección por etapas permitiría probar primero la jugada TT y
las capturas prometedoras, generando/ordenando las restantes cuando hagan falta.

## 4. Podas adicionales, después de la corrección táctica

La búsqueda tiene PVS, ventanas de aspiración, null move y LMR. No tiene poda de
futilidad inversa, poda de jugadas tardías por conteo ni un filtro de futilidad/SEE
de jugadas tranquilas en los nodos superficiales.

Son candidatos razonables para reducir el árbol, pero requieren cambios aislados,
márgenes prudentes y protecciones para jaques, promociones, mates y finales de zugzwang.
No conviene sumar varias podas y nuevos pesos a la vez. La implementación de referencia
de [Stockfish](https://github.com/official-stockfish/Stockfish/blob/master/src/search.cpp)
combina técnicas de selección y poda; sus constantes no se trasladan automáticamente
a nuestra evaluación ni garantizan la misma fuerza.

## 5. Evaluación y memoria

Los términos nuevos de debilidades tienen pesos elegidos manualmente y recorren el
tablero en cada fallo de caché. Pueden mejorar posiciones específicas y, a la vez,
añadir coste o sesgo. Conviene congelar nuevos términos hasta comparar versiones.

La TT compartida actual tiene 262144 entradas para la solicitud completa, frente a
262144 por trabajador antes del cambio. Con cuatro participantes se redujo la capacidad
agregada a una cuarta parte, aunque compartir elimina duplicación de posiciones.
Además, una solicitud ordinaria crea trabajadores y cachés de nuevo. Una capacidad
configurable y una reutilización controlada merecen revisión, preservando el aislamiento
entre historiales y partidas. No basta con conservar indiscriminadamente las cotas.

## Cómo valorar el progreso en las partidas manuales

Conservar una versión base identificable y comparar un cambio cada vez, con el mismo
rival, configuración, tiempo y aperturas, alternando colores. Guardar PGN junto con
build, configuración y telemetría permite estudiar la primera decisión que deteriora
la posición, no solo la jugada final de la derrota.

La profundidad completada ayuda a diagnosticar la búsqueda, pero puede aumentar al
podar más sin mejorar las decisiones. Para fuerza, comparar resultados y errores
tácticos de las dos versiones. Una partida o unas pocas derrotas frente a un rival
muy superior no permiten estimar Elo. Como referencia metodológica, el proyecto
[Fishtest](https://github.com/official-stockfish/fishtest) contrasta cambios mediante
partidas entre versiones; aquí no se ha lanzado ese tipo de prueba.
