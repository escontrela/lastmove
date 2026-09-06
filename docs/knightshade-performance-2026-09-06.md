# Knightshade: revisión de búsqueda y eficiencia, 2026-09-06

Implementado en la rama actual (`main`), partiendo de `eeec992`. No se ha abierto la interfaz JavaFX ni se han ejecutado partidas externas. El tiempo máximo de reflexión **se mantiene**: la mejora busca analizar más dentro del mismo presupuesto.

## Cambios de búsqueda y evaluación

- La raíz conserva su mejor jugada entre profundidades y reintentos de aspiración, de modo que PVS la prueba primero.
- SEE se calcula una sola vez por jugada durante la ordenación. Las capturas materialmente perdedoras pasan detrás de las jugadas tranquilas, manteniendo las promociones prioritarias.
- El historial separa colores, tiene límites para evitar saturación y aprende también de jugadas tranquilas que fallan antes de un corte.
- TT de cuatro vías, 262144 entradas, reemplazo según profundidad y exactitud. El contador de medias jugadas forma parte de la clave de búsqueda. La tabla se limpia entre solicitudes con distintos historiales.
- Poda por distancia al mate y movimiento nulo condicionado a evaluación estática. No se permiten pases adicionales dentro de una rama nula, ni se mezclan sus resultados con la TT o las tablas por historial real.
- Quiescencia reutiliza movimientos en el horizonte y reconoce ahogado y tablas. Los jaques tranquilos tienen un horizonte local de un nivel, sin depender del antiguo contador global de 8000 nodos.
- Una posible repetición se compara como tablas con el resto de jugadas; una evaluación estática favorable no la excluye antes de buscar.
- El rey interpola entre refugio y actividad central según el material restante; el escudo de peones pierde peso en finales.

## Segunda revisión: coste por nodo

Esta segunda revisión conserva el árbol de búsqueda de la primera:

1. **Legalidad selectiva.** Sin jaque se detectan una vez las clavadas absolutas. Solo rey, piezas clavadas y en passant necesitan simulación completa. Con jaque se comprueban todas las evasiones. En passant conserva su comprobación porque puede abrir una línea al retirar dos peones.
2. **Menos objetos temporales.** Undo reutiliza un marco por profundidad activa; las coordenadas Square se comparten de forma inmutable. La casilla de cada rey se mantiene incrementalmente y los derechos de enroque se reutilizan cuando no cambian.
3. **Generación táctica directa.** La quiescencia genera capturas y promociones sin construir antes todas las jugadas tranquilas. `hasLegalMove` termina al encontrar la primera legal.
4. **Caché de evaluación estática.** 32768 entradas con comprobación de la clave completa evitan recalcular términos posicionales para posiciones ya evaluadas. La búsqueda sigue resolviendo el historial y las reglas de tablas.

El motor sigue usando un hilo. Tablero, TT, historial y caché de evaluación pertenecen a ese hilo; compartirlos directamente entre trabajadores introduciría carreras. No se han añadido bases de aperturas/finales ni cambiado las dependencias.

## Medición reproducible

Java 22, cuatro posiciones incluidas en `SearchBenchmark`: inicial, apertura italiana, posición táctica con damas y final de peones. Cada proceso calienta primero las cuatro posiciones a profundidad 3. Tres procesos por versión, ejecuciones secuenciales; tiempos de búsqueda, sin arranque de JVM ni compilación. Las cifras son diagnósticas y dependen de la máquina y de la carga.

Comparación de **primera revisión frente a versión final**, profundidad fija 6:

| Posición | Nodos en ambas | Primera revisión, mediana ms | Final, mediana ms |
| --- | ---: | ---: | ---: |
| Inicial | 86849 | 431 | 301 |
| Apertura | 54367 | 273 | 196 |
| Táctica | 109787 | 528 | 401 |
| Final de peones | 3038 | 6 | 5 |

En las tres repeticiones se conservan exactamente jugada, puntuación, profundidad y nodos por posición. La mediana del tiempo total de las cuatro posiciones baja de **1234 a 906 ms**, aproximadamente **27 % menos tiempo / 36 % más nodos por segundo**. La fila del final es demasiado breve para sacar conclusiones de rendimiento aisladas.

Con **1000 ms por posición**, también en tres procesos por versión:

| Posición | Nodos primera revisión, mediana | Nodos final, mediana | Incremento |
| --- | ---: | ---: | ---: |
| Inicial | 224168 | 308243 | +37,5 % |
| Apertura | 203501 | 297651 | +46,3 % |
| Táctica | 219334 | 306238 | +39,6 % |
| Final de peones | 596407 | 743019 | +24,6 % |

La mediana del total pasa de 1244224 a 1655548 nodos, **aproximadamente un 33 % más con el mismo tiempo**. En esta muestra las profundidades completas siguen siendo 7, 7, 6 y 16; se progresa más dentro de la iteración siguiente, pero no se puede afirmar que se gane un nivel completo en todas las posiciones.

La primera revisión ya había reducido la mediana del tiempo total de la muestra a profundidad 5 de 611 a 400 ms frente a `eeec992`; esa comparación también cambiaba búsqueda y evaluación, por lo que no representa una comparación del mismo árbol.

Comandos desde la raíz, sin arrancar la aplicación:

```sh
mvn clean test
java -cp target/classes src/test/java/com/knightshade/engine/benchmark/SearchBenchmark.java 6
# Un segundo por posición, conservando el mismo presupuesto en ambas versiones:
java -cp target/classes src/test/java/com/knightshade/engine/benchmark/SearchBenchmark.java 6 1000
```

## Validación

`mvn clean test`: **458 tests, 0 fallos, 0 errores, 2 omitidos**. De ellos, **68 tests del motor**, todos correctos.

Se incluyen perft inicial y Kiwipete a profundidad 3, comparación entre legalidad optimizada y make/unmake completo durante hasta 800 posiciones de partidas aleatorias reproducibles, captura al paso que descubre una torre, promociones, crecimiento y reutilización de Undo, evaluación cacheada frente a términos sin caché, colisiones de TT, tablas, cancelación y regresiones existentes de sacrificios de dama.

No se estima una ganancia de ELO a partir de estos tiempos. Las ponderaciones de evaluación y heurísticas requieren validación posterior en torneos, incluido el objetivo de superar el nivel 7 indicado por el usuario.
