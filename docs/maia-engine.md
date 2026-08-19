# Integración de Maia Chess

Maia es una familia de motores de ajedrez basados en redes neuronales entrenadas para jugar como
humanos de una determinada fuerza. Cada perfil de Maia no es un motor clásico: es **Leela Chess Zero
(`lc0`) ejecutando un fichero de pesos distinto**. LastMove integra Maia como un motor UCI más,
reutilizando por completo el adaptador UCI existente, sin un adaptador específico.

- **Paquete de integración:** `com.escontrela.lastmove.infrastructure.engine.maia`
- **Ejecutable:** `lc0` (Leela Chess Zero)
- **Perfiles:** `Maia 1100`, `Maia 1500`, `Maia 1700`, `Maia 1900`
- **Sin cambios** en el adaptador UCI (`UciProcessEngine` / `UciEngineConfiguration`)

---

## 1. Modelo mental

Maia no añade un protocolo nuevo. El "motor" es `lc0` hablando UCI por streams estándar; la única
diferencia entre perfiles es el argumento `--weights=<fichero>` con el que se arranca. Por eso:

```text
ComputerMoveEngineProvider (por perfil)
        │
        ▼
UciProcessEngine  ──  lc0 --weights=maia-1900.pb.gz --threads=4
```

Cada partida crea un proceso `lc0` independiente (el mismo modelo de ciclo de vida que Sunfish), y
la posición se envía con `position fen ...` seguida de `go movetime ...`. No se necesita
`ucinewgame` porque un proceso recién arrancado no arrastra estado de partidas anteriores.

---

## 2. Componentes

| Clase | Responsabilidad |
| --- | --- |
| `MaiaEngineProfile` | Enum con los perfiles y su fichero de pesos (`id`, `displayName`, `version`, `weightsFileName`). |
| `MaiaRuntimeDetails` | Value object con el `lc0` resuelto y el fichero de pesos absoluto. |
| `MaiaExecutableResolver` | Resuelve `lc0` (override configurado → `PATH` → rutas típicas) y valida el fichero de pesos. |
| `MaiaComputerMoveEngineProvider` | Construye el comando UCI y delega en `UciProcessEngine`. |
| `MaiaComputerEngineHealthCheck` | Probe de jugada legal desde la posición inicial (patrón Sunfish). |
| `MaiaEngineConfiguration` | `@Configuration` que registra un provider y un health check por perfil. |

### 2.1 Descubrimiento del ejecutable

`MaiaExecutableResolver` resuelve `lc0` en este orden:

1. **Override del usuario** (persistido en settings con el id de familia `maia`), si existe y es
   ejecutable. Si existe pero no es ejecutable, se informa el error en lugar de ignorarlo.
2. **Descubrimiento automático**: entradas de `PATH`, `~/.local/bin/lc0`, `/opt/homebrew/bin/lc0`
   (Apple Silicon), `/usr/local/bin/lc0` y `/usr/bin/lc0`.

El fichero de pesos se resuelve contra una **ubicación configurable** en Setup (puede ser un
directorio o un fichero directamente):

- **Directorio**: cada perfil espera su fichero canónico dentro (`maia-1100.pb.gz`,
  `maia-1500.pb.gz`, `maia-1700.pb.gz`, `maia-1900.pb.gz`).
- **Fichero**: un único fichero de pesos (p. ej. `42850.pb.gz`) compartido por todos los perfiles.

El valor por defecto de la ubicación de pesos es la propiedad
`lastmove.engine.maia.models-directory` (`~/.local/share/maia`).

---

## 3. Configuración

La pantalla **Setup** añade una tarjeta "Maia (Leela Chess Zero)" con dos campos y un botón de
prueba:

| Campo | Descripción |
| --- | --- |
| `lc0 executable` (opcional) | Ruta al binario `lc0`; en blanco se descubre automáticamente en `PATH`. |
| `Weights file or directory` | Fichero `.pb.gz` o directorio con los ficheros de pesos de Maia. |

Ambos valores se persisten con `ComputerEngineSettingsService` (`maiaExecutable()` /
`updateMaiaExecutable()` / `clearMaiaExecutable()` y `maiaWeightsLocation()` /
`updateMaiaWeightsLocation()`). El botón "Test connection" ejecuta el probe de
`MaiaComputerEngineHealthCheck`.

```properties
lastmove.engine.maia.models-directory=${user.home}/.local/share/maia
lastmove.engine.maia.threads=4
```

| Clave | Descripción | Valor recomendado |
| --- | --- | --- |
| `models-directory` | Ubicación **por defecto** de los pesos cuando el usuario no la configura. | `~/.local/share/maia` |
| `threads` | Hilos para el backend CPU. | `4` |

### Backend de lc0

El backend (`--backend=...`) se deja en su valor por defecto (`auto`). lc0 selecciona el mejor
backend disponible por plataforma: `metal` en Apple Silicon, `cudnn`/`opencl`/`blas` según el
hardware en Windows/Linux. No se fuerza un backend específico para no romper instalaciones sin GPU.

---

## 4. Ciclo de vida

- **Inicio** (`provider.create()`): se resuelve `lc0` + pesos y se construye el comando; el proceso
  real se lanza en `start()` (`uci` → `uciok` → `isready` → `readyok`).
- **Carga del modelo**: arrancar `lc0` y cargar la red puede tardar más que un motor clásico; el
  timeout de arranque es de **60 s** (frente a los 5 s por defecto del adaptador). Esto es
  configuración del provider, no un cambio en el adaptador.
- **Partida**: cada `chooseMove` envía `position fen ...` + `go movetime ...` y espera `bestmove`.
- **Reutilización**: el proceso se mantiene vivo durante toda la partida; `ucinewgame` no se usa.
- **Cierre**: `close()` envía `stop`/`quit` y fuerza la terminación si no responde (comportamiento
  estándar de `UciProcessEngine`).
- **Errores**: si `lc0` muere o no responde, `UciProcessEngine` lanza `ComputerEngineException`, que
  `ComputerGameService` traduce a fase `ENGINE_ERROR` con mensaje al usuario.

---

## 5. Integración con la UI

El selector de oponentes de `HumanVsComputerSetupOverlay` no se modificó: `ComputerGameService.availableEngines()`
recoge todos los `ComputerMoveEngineProvider` registrados por Spring, así que los perfiles Maia
aparecen solos:

```text
Knightshade v3
Maia 1100
Maia 1500
Maia 1700
Maia 1900
Sunfish 2026
```

El selector muestra `displayName + version`, por lo que cada perfil se renderiza como `Maia 1100`,
etc. El orden entre perfiles queda determinado por `id` (segundo criterio de ordenación añadido a
`availableEngines()`).

La pantalla **Setup** sí añade una tarjeta para configurar el ejecutable `lc0` y la ubicación de los
pesos (ver [Configuración](#3-configuración)).

---

## 6. Pruebas

| Prueba | Verifica |
| --- | --- |
| `MaiaExecutableResolverTest` | Resolución del ejecutable configurado/descubierto, rechazo de no-ejecutables, pesos ausentes, fichero de pesos directo y fallo claro sin candidatos. |
| `MaiaComputerMoveEngineProviderTest` | `create()` produce un `UciProcessEngine` con descriptor de perfil, instancias independientes, y fallo temprano sin pesos. |
| `ComputerEngineSettingsServiceTest` | El override de `lc0` (id `maia`) se persiste, se lee y se borra; la ubicación de pesos se persiste y se lee con su valor por defecto. |
| `MaiaRealEngineIntegrationTest` | Opt-in (`-Dlastmove.maia.integration=true`) contra un `lc0` real: handshake, jugada legal y cierre. |

---

## 7. Licencias

- **Leela Chess Zero (`lc0`)** se distribuye bajo **GPLv3**.
- **Los pesos de Maia** se distribuyen bajo **CC BY-NC-SA 4.0 (uso no comercial)**.

LastMove no redistribuye `lc0` ni los pesos: solo resuelve rutas hacia instalaciones locales. Deben
reflejarse ambas licencias en `THIRD_PARTY_NOTICES.md` si en el futuro se empaquetan estos artefactos.

---

## 8. Extensiones futuras

Añadir un perfil nuevo es trivial: una entrada en `MaiaEngineProfile` y dos métodos `@Bean` en
`MaiaEngineConfiguration`. Para motores UCI que necesiten opciones no expresables por línea de
comandos (p. ej. `Komodo`, `Berserk`), el siguiente paso natural es generalizar
`UciEngineConfiguration` con un mapa de opciones `setoption` y una bandera opt-in `ucinewgame`,
manteniendo los defaults idénticos al comportamiento actual para no afectar a Sunfish ni Knightshade.
