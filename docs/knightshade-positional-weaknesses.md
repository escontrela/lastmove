# Knightshade: debilidades posicionales

Revisión del 2026-10-02. La evaluación incorpora `WeakPawnTerm` y `OutpostTerm` junto
con los términos existentes de material, seguridad del rey, movilidad y estructura.
No hay un cambio binario entre «atacar» y «maniobrar»: la búsqueda compara ambas
posibilidades según la posición. La evaluación no genera un plan explícito de varias
jugadas; proporciona incentivos que la búsqueda puede utilizar para elegirlas.

## Peones como objetivos

El término de estructura ya penaliza peones aislados y doblados. El nuevo término
se centra en su explotación:

- Un peón sin defensa de otro peón recibe 8 centipeones de penalización cuando lo
  ataca una pieza rival distinta de un peón, incluido el rey en finales.
- Se añaden 4 si tampoco puede obtener apoyo mediante avances rectos de sus peones
  vecinos y 8 si no lo defiende ninguna pieza propia.
- Se penaliza con 8 un candidato a peón retrasado: carece de apoyo de peones actual
  o futuro, su casilla de avance está vacía y atacada por un peón rival, y no tiene
  una captura disponible. Se excluye el avance a promoción.

Son incentivos moderados, añadidos a las valoraciones existentes. No se vuelve a
penalizar un peón aislado solo por estar aislado si no hay presión ni impedimento
para avanzar. Una defensa con piezas reduce la penalización; una defensa con otro
peón elimina este término adicional.

## Puestos avanzados

Se consideran las columnas b–g y las filas cuarta a sexta desde el punto de vista
de cada bando. La casilla debe estar defendida por un peón propio y fuera de los
ataques actuales y potenciales de los peones enemigos al avanzar.

Un caballo que ocupa una casilla así recibe 24 centipeones; un alfil, 14. Preparar
la ocupación recibe un incentivo menor: 6 para el caballo y 4 para el alfil si
alcanza una de esas casillas vacías, sin ataque enemigo actual, en un desplazamiento
geométrico. Cada pieza obtiene como máximo una bonificación de preparación.

No se premia un agujero inaccesible por sí mismo, ni una casilla que un peón rival
pueda disputar mediante un avance posterior. Los peones que ya han sobrepasado
la casilla no pueden retroceder para disputarla.

## Coste y límites

Ambos términos reutilizan `EvaluationAttacks`, el mapa de influencia que se calcula
una vez en cada evaluación no cacheada. Los nuevos mapas son máscaras de bits en
el mismo espacio de trabajo por trabajador; no se genera otra lista de jugadas.

Los ataques son geométricos, como en movilidad y seguridad del rey: no resuelven
clavadas ni demuestran que una captura sea favorable. Los ataques potenciales de
peones suponen avances rectos e ignoran bloqueos, por lo que son conservadores al
conceder puestos avanzados; no anticipan cambios de columna por capturas. La
búsqueda legal y táctica sigue decidiendo si una maniobra o captura conviene.

Las pruebas cubren defensas actuales y futuras, líneas bloqueadas, peones retrasados,
salidas por captura, puestos avanzados ocupados y alcanzables, simetría entre colores
y coherencia de la caché tras hacer/deshacer movimientos. Los pesos iniciales quedan
pendientes de validación jugando contra Stockfish 7. No se han ejecutado benchmarks.
