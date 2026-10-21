Carpeta destinada al código fuente en Java desarrollado para la solución de escritorio.

## Kiosko de Autoatención (JavaFX)

Requisitos: JDK 21 o superior. Las dependencias (JavaFX, AtlantaFX, Jackson) las descarga Maven.

### Pruebas

```bash
cd desktop
./mvnw test
```

### Ejecutar el kiosko

Contra la API real (por defecto `http://localhost:8080/api/v1`):

```bash
./mvnw javafx:run
```

Apuntando a otra URL de la API:

```bash
./mvnw javafx:run -Dapi.url=http://localhost:8080/api/v1
```

Con la API simulada en memoria, sin necesidad de levantar la API ni PostgreSQL:

```bash
./mvnw javafx:run -Dkiosk.args="--demo-stub"
```

En pantalla completa (modo kiosko):

```bash
./mvnw javafx:run -Dkiosk.args="--kiosk"
```

Capturas de pantalla de todas las pantallas del flujo, para revisar el diseño:

```bash
./mvnw javafx:run -Dkiosk.args="--demo-stub --capture=/ruta/de/salida/"
```

Las capturas que ilustran la documentación viven en `docs/screens/img/` y se regeneran así, desde la raíz del repositorio:

```bash
./scripts/run-kiosk.sh --demo-stub --capture=docs/screens/img/
```

El documento que las muestra, pantalla por pantalla, es [`docs/screens.md`](../docs/screens.md).

### Ejecutable

La clase principal es `com.medicitas.kiosk.app.KioskLauncher` (propiedad `exec.mainClass` del `pom.xml`).

### Verificación

El checklist de verificación del kiosco frente a los RF/RNF, el prototipo y las reglas del repositorio está en [`VERIFICATION.md`](VERIFICATION.md), con las correcciones aplicadas y las observaciones pendientes.

## Capa de conexión a la API

En el curso se pide una "clase de conexión a base de datos". En este proyecto el kiosco **nunca abre una conexión JDBC**: la base de datos PostgreSQL solo la conoce la API (`/api`). El equivalente de esa clase en el cliente es el paquete `com.medicitas.kiosk.api`, que abre conexiones **HTTP** y conversa en **JSON** con la API REST, más la interfaz `KioskContext`, que entrega ese cliente a cada pantalla.

| Clase | Rol | Equivalente en un proyecto con acceso directo a BD |
| :--- | :--- | :--- |
| `ApiClient` | Abre la conexión (`java.net.http.HttpClient`), arma cada petición (`GET /patients`, `POST /appointments`…), serializa y deserializa JSON con Jackson y devuelve `CompletableFuture`. Tiempo de conexión 5 s y de respuesta 10 s. | La clase `Conexion` / `DataSource` y los DAO. |
| `Dtos` | Registros (`record`) que reflejan el contrato de [`docs/api-contract.md`](../docs/api-contract.md): `Patient`, `Insurer`, `Availability`, `Appointment`, `ApiError`… | Las entidades o modelos que se llenan desde un `ResultSet`. |
| `ApiException` | Único tipo de error que ven las pantallas. Guarda `status`, `code` y `fields`, y su `getMessage()` siempre es un texto en español apto para el paciente (RNF-06). | `SQLException` traducida a una excepción propia. |
| `KioskConfig` | Resuelve a qué servidor conectarse (`api.url`): propiedad `-Dapi.url`, luego `%ProgramData%\MediCitasKiosk\kiosk.properties`, luego `http://localhost:8080/api/v1`. | El archivo de propiedades con la cadena de conexión. |
| `KioskContext` | Interfaz que el contenedor (`KioskShell`) implementa y que recibe cada pantalla: `api()`, `session()`, navegación, avisos y diálogos. | Inyección del `DataSource` a cada formulario. |

### Separación de responsabilidades

```
desktop/src/main/java/com/medicitas/kiosk
├── app       KioskLauncher, KioskApp         arranque: fuentes, tema AtlantaFX, crea ApiClient y KioskShell
├── screens   KioskShell (KioskContext)       contenedor: barra de pasos, avisos, botones, inactividad (90 s)
│             IdentificationScreen …          una clase por pantalla del asistente, más MyAppointmentsScreen
│             BookingSession                  estado de la visita del paciente (se borra al terminar)
│             Validations, Dates              reglas de validación y textos de fecha, sin JavaFX (probables por unidad)
├── ui        Banner, Stepper, Keypad,        componentes visuales reutilizables; Async une CompletableFuture
│             DateStrip, SlotGrid, Async…     con el hilo de JavaFX
├── api       ApiClient, Dtos,                conexión a la API: la única salida de red del kiosco
│             ApiException, KioskConfig
└── dev       DemoStub, DemoCapture           API simulada y capturas; se cargan por reflexión solo con --demo-stub
```

- **`screens`** decide *qué* mostrar y *cuándo* llamar a la API; no sabe de HTTP ni de JSON.
- **`ui`** decide *cómo* se ve un componente; no conoce el estado de la visita ni la API (salvo `Async`, que solo traduce el resultado al hilo de JavaFX).
- **`api`** decide *cómo* hablar con el servidor; no conoce pantallas ni controles.
- **`app`** solo arranca y conecta las piezas; **`dev`** nunca se referencia en tiempo de compilación desde el flujo normal.

```
┌──────────────────── Kiosko (JavaFX) ────────────────────┐
│  screens ──▶ KioskContext.api() ──▶ api.ApiClient       │
│     ▲                                   │ Dtos (JSON)   │
│     └── Async (hilo JavaFX) ◀── ApiException ◀──┘       │
└──────────────────────────────┬──────────────────────────┘
                               │ HTTP/JSON  api.url = http://localhost:8080/api/v1
                               ▼
                    ┌──── API (Spring Boot) ────┐
                    │ Controladores → Servicios  │
                    │        → Repositorios      │
                    └─────────────┬──────────────┘
                                  │ JDBC (solo aquí)
                                  ▼
                              PostgreSQL
```

El mismo diagrama, en PlantUML, está en [`docs/diagrams/13-kiosk-api-layer.puml`](../docs/diagrams/13-kiosk-api-layer.puml).

### Cómo llega `ApiClient` a las pantallas

1. `KioskApp.start()` crea **una sola instancia**: `new KioskShell(ApiClient.fromProperties())`.
2. `KioskShell` implementa `KioskContext` y se pasa a sí mismo al constructor de cada pantalla (`new IdentificationScreen(this)`).
3. Cada pantalla guarda el contexto en `Screen.ctx` y llama `ctx.api().findPatient(...)`, `ctx.api().bookAppointment(...)`, etc. Ninguna pantalla construye un `ApiClient` ni conoce `KioskShell`.

### Errores y reintentos

- Toda llamada termina en un `ApiException`: `ApiClient.convert()` lee el cuerpo `{ code, message, fields }` del contrato; si no hay conexión usa `ApiException.noConnection()` ("No pudimos conectar con el sistema de citas…"); si el cuerpo no es JSON, un mensaje genérico. **Nunca llega un stacktrace ni un código SQL a la pantalla.**
- Las pantallas reciben el error ya desenvuelto (`ApiException.from()`) y lo muestran con `ctx.showError(error, retry)`. Si pasan un `Runnable` de reintento, el `Banner` ofrece el botón **Reintentar**; se usa cuando no hay conexión o la operación es segura de repetir.
- Conflictos de agenda (`SLOT_NOT_AVAILABLE`, `DUPLICATE_APPOINTMENT`) se detectan con `isScheduleConflict()` y devuelven al paciente a "Fecha y hora".
- Para reservar, `BookingSession.bookingKey()` genera una `Idempotency-Key` por intento y la reutiliza en cada reintento (RNF-07): aunque la red se corte después de que la API registró la cita, repetir la petición no duplica nada.

### Asincronía

`ApiClient` devuelve `CompletableFuture` y usa `HttpClient.sendAsync`, así que **nunca bloquea el hilo de JavaFX**. `ui.Async.execute(llamada, botón, onSuccess, onFailure)` deshabilita el botón con un indicador de carga, espera el resultado en el hilo del `HttpClient` y entrega `onSuccess` u `onFailure` con `Platform.runLater`, ya en el hilo de JavaFX. Las pantallas solo escriben esas dos funciones.

### Configuración de `api.url`

| Orden | Fuente | Cuándo |
| :-- | :--- | :--- |
| 1 | `-Dapi.url=http://servidor:8080/api/v1` | Desarrollo (`./mvnw javafx:run -Dapi.url=…`, `scripts/run-kiosk.sh` con `API_URL`). `--demo-stub` la fija a la API simulada. |
| 2 | `%ProgramData%\MediCitasKiosk\kiosk.properties` con `api.url=…` | Kiosco instalado en Windows: TI lo configura una vez. |
| 3 | `http://localhost:8080/api/v1` | Valor por defecto de `ApiClient.DEFAULT_URL`. |

Para HTTPS (RNF-01) basta con apuntar `api.url` a una URL `https://`; `HttpClient` valida el certificado con el almacén del JDK.

## Para la presentación

- El kiosco es un **cliente de la API**: no tiene driver JDBC, usuario ni contraseña de PostgreSQL. Si la base de datos cambia, el kiosco no se entera.
- La "clase de conexión" es **`ApiClient`**: una sola instancia creada al arrancar y compartida por todas las pantallas a través de **`KioskContext`** (inyección de dependencias).
- Los datos viajan como **JSON** y se convierten a **`record`s de `Dtos`**, que son copia fiel del contrato `docs/api-contract.md`.
- Todo error llega como **`ApiException`** con un mensaje en español listo para el paciente; la pantalla decide si ofrece **Reintentar**.
- Las llamadas son **asíncronas** (`CompletableFuture` + `Async`): la pantalla muestra un indicador de carga y nunca se congela.
- Reservar es **idempotente**: una `Idempotency-Key` por intento evita citas duplicadas si se corta la red (RNF-07).
- **Cinco paquetes, cinco responsabilidades:** `app` arranca, `screens` decide, `ui` dibuja, `api` conecta, `dev` simula.
- La sesión del paciente vive en memoria (`BookingSession`) y se borra a los 90 segundos de inactividad.
