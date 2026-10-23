# Verificación del Kiosco de Autoatención

Checklist de la verificación del cliente JavaFX (`/desktop`) frente a lo solicitado en el curso. Resultado de la issue #43.

> **Sobre la rúbrica del curso.** La rúbrica oficial de evaluación **no está en el repositorio**. Este checklist se basa en lo que sí está documentado: los requerimientos RF-01 a RF-14 y RNF-01 a RNF-8 de [`docs/requirements-specification.md`](../docs/requirements-specification.md), las pantallas acordadas en [`docs/screens.md`](../docs/screens.md) y [`docs/prototype/README.md`](../docs/prototype/README.md), el contrato de [`docs/api-contract.md`](../docs/api-contract.md) y las reglas de calidad del [`README.md` raíz](../README.md) (arquitectura n-capas, nombres, pruebas, excepciones, commits). Si la docente comparte la rúbrica, basta con mapear cada criterio a las filas de este documento.

## Cómo se verificó

| Método | Detalle |
| :--- | :--- |
| Lectura del código | Todas las clases de `screens/`, `ui/`, `api/`, `app/` y `dev/`, contrastadas con los RF/RNF y con el contrato REST. |
| Ejecución en modo demostración | `./mvnw javafx:run -Dkiosk.args="--demo-stub --capture=<carpeta>"` el 2026-10-20 en macOS. Recorrió las seis pantallas con la API simulada (`DemoStub`) y generó un PNG por pantalla (`1-identification` … `6-my-appointments`), revisados uno por uno. |
| Pruebas unitarias | `./mvnw test`: 63 pruebas en verde (JUnit 5) tras las correcciones. |

Leyenda de estado: **Cumple** · **Cumple con ajuste** (se corrigió algo en esta verificación o hay una limitación conocida y documentada) · **No aplica** (es responsabilidad de la API, no del kiosco).

## Recorrido del flujo

| # | Pantalla | Qué se observó | Evidencia |
| :-- | :--- | :--- | :--- |
| 1 | Identificación | Tipo de documento (DNI / CE / Pasaporte), teclado en pantalla, botón "Continuar" deshabilitado hasta escribir, aviso de Ley 29733. Si el documento existe, saluda al paciente y ofrece "Reservar una nueva cita" o "Mis citas". | `IdentificationScreen`, captura `1-identification.png` |
| 2 | Datos y seguro | Paciente conocido: datos de contacto solo lectura y cobertura registrada preseleccionada. Paciente nuevo: formulario con validaciones por campo. Tarjetas Rímac, Pacífico, La Positiva, Mapfre y "Atención particular"; plan y póliza al elegir aseguradora. | `PersonalDataAndInsuranceScreen`, captura `2-personal-data-and-insurance.png` |
| 3 | Especialidad y sede | Sedes San Isidro / La Molina con dirección, buscador sin tildes ni mayúsculas, lista de especialidades activas por sede, resumen "Seleccionado actualmente". | `SpecialtyAndLocationScreen`, captura `3-specialty-and-location.png` |
| 4 | Fecha y hora | Franja de 14 días (7 por página), días sin cupos deshabilitados, horarios agrupados por médico con su CMP, "Ver más horarios" cuando hay más de 14. | `DateAndTimeScreen`, `DateStrip`, `SlotGrid`, captura `4-date-and-time.png` |
| 5 | Confirmación | Resumen (paciente, especialidad, sede, médico, fecha y hora, consultorio, cobertura), motivo de consulta con contador 0/250, botón "Confirmar reserva". Al confirmar: código de cita, resumen y aviso de correo y recordatorio. | `ConfirmationScreen`, captura `5-confirmation.png` |
| 6 | Mis citas | Lista de citas vigentes con código, botones "Reprogramar" y "Anular" (con diálogo de confirmación y motivo opcional), enlace "Reservar una nueva cita". | `MyAppointmentsScreen`, captura `6-my-appointments.png` |

## Requerimientos funcionales

| Código | Requerimiento | Estado | Evidencia en el kiosco |
| :-- | :--- | :--- | :--- |
| RF-01 | Registro de paciente, sin duplicar documento | **Cumple** | `PersonalDataAndInsuranceScreen.register()` envía `POST /patients`; ante `409 DUPLICATE_DOCUMENT` llama a `recoverProfile()` y recupera el perfil con aviso "Ya estabas registrado". Validaciones de `Validations` (DNI 8 dígitos, celular 9 dígitos con 9, correo, nombres, fecha de nacimiento). |
| RF-02 | Identificación por número de documento | **Cumple con ajuste** | `IdentificationScreen.onNext()` consulta `GET /patients`. Ajuste: solo el código `PATIENT_NOT_FOUND` lleva al registro; cualquier otro 404 (URL mal configurada) muestra error con "Reintentar" (`ApiException.isPatientNotFound()`). |
| RF-03 | Asociación de aseguradora y plan | **Cumple con ajuste** | `InsurerCard` por aseguradora, `ComboBox` de plan y póliza; `PUT /patients/{id}/insurance` en `saveCoverage()`. Atención particular envía `planId: null`. Ajuste: el estado de cobertura se muestra como "vigente / suspendida / vencida" en lugar del código `ACTIVE`. |
| RF-04 | Consulta de especialidades activas por sede | **Cumple con ajuste** | `GET /locations` y `GET /specialties?locationId=` en `SpecialtyAndLocationScreen`, con caché por sede. Ajuste: si no hay sedes se muestra aviso en lugar de fallar. |
| RF-05 | Consulta de disponibilidad | **Cumple con ajuste** | `GET /availability` con rango de 14 días en `DateAndTimeScreen.load()`; días sin cupos deshabilitados en `DateStrip`. Ajuste: si el cupo elegido dejó de estar libre al volver al paso, se avisa al paciente. |
| RF-06 | Reserva de cita con motivo de consulta | **Cumple** | `ConfirmationScreen.book()` envía `POST /appointments` con `patientId`, `slotId`, `patientInsuranceId` y `visitReason` (5 a 250 caracteres). Muestra código `MCA-XXXXX` al confirmar. |
| RF-07 | Bloqueo de cupos concurrentes | **Cumple** | Ante `409 SLOT_NOT_AVAILABLE` (`ApiException.isScheduleConflict()`), muestra el mensaje de la API, limpia el cupo (`BookingSession.scheduleConflict()`) y regresa a "Fecha y hora" para elegir otro. La regla se aplica en la API; el kiosco la respeta y guía el reintento. |
| RF-08 | Prevención de citas duplicadas | **Cumple** | Mismo tratamiento que RF-07 para `409 DUPLICATE_APPOINTMENT`. |
| RF-09 | Reprogramación por el paciente | **Cumple** | `MyAppointmentsScreen.reschedule()` ubica sede y especialidad de la cita, `BookingSession.startReschedule()` salta a "Fecha y hora" en modo `RESCHEDULE`, y `DateAndTimeScreen.reschedule()` envía `PATCH /appointments/{id}/reschedule`. Maneja `APPOINTMENT_NOT_MODIFIABLE`. |
| RF-10 | Anulación por el paciente | **Cumple** | `MyAppointmentsScreen.confirmCancellation()` pide confirmación y motivo opcional; `cancel()` envía `PATCH /appointments/{id}/cancellation` y refresca la lista. |
| RF-11 | Confirmación automática de reserva | **No aplica** (API) | El kiosco solo informa: "Te enviamos la confirmación a tu correo … y un recordatorio 24 horas antes" en `ConfirmationScreen.success()`. El envío lo hace la API (módulo `notification`). |
| RF-12 | Recordatorios previos a la cita | **No aplica** (API) | Ídem RF-11; el consentimiento se captura con la casilla `acceptsNotifications` del registro. |
| RF-13 | Trazabilidad de notificaciones | **No aplica** (API) | El kiosco no consulta `GET /appointments/{id}/notifications`; es información interna de la clínica. |
| RF-14 | Fallos del servicio de mensajería | **No aplica** (API) | La cita se confirma en el kiosco con la respuesta `201` de la API, independientemente del envío. |

## Requerimientos no funcionales

| Código | Requerimiento | Estado | Evidencia en el kiosco |
| :-- | :--- | :--- | :--- |
| RNF-01 | Comunicación por HTTPS y protección de datos | **Cumple con ajuste** | `ApiClient` usa `java.net.http.HttpClient`, que acepta `https://` con solo cambiar `api.url` (ver [README](README.md#capa-de-conexión-a-la-api)). En desarrollo la URL por defecto es `http://localhost`. Datos del paciente: `KioskShell` borra la sesión a los 90 s de inactividad (`BookingSession.clear()` + pantallas recreadas) y nunca persiste nada en disco. |
| RNF-02 | Validación y saneamiento de entradas | **Cumple** | Validación en el cliente (`Validations`, `TextFormatter` del documento, límite de 250 caracteres del motivo) como primera barrera; la API vuelve a validar (Bean Validation). El kiosco nunca arma SQL. |
| RNF-03 | Control de solicitudes abusivas | **No aplica** (API) | Rate limiting corresponde a la API. El kiosco deshabilita el botón mientras hay una llamada en curso (`Async.loading`) y evita envíos dobles. |
| RNF-04 | Latencia de disponibilidad ≤ 3 s | **Cumple con ajuste** | El kiosco no bloquea la interfaz durante la consulta (indicador de carga) y aplica un tiempo máximo de 10 s por petición (`ApiClient.base()`); la latencia real depende de la API. |
| RNF-05 | Disponibilidad del servicio | **No aplica** (API) | Ante caída de la API el kiosco muestra "No pudimos conectar con el sistema de citas…" con "Reintentar". |
| RNF-06 | Mensajes claros en español, sin tecnicismos | **Cumple con ajuste** | `ApiException.getMessage()` siempre devuelve un texto apto para el paciente; `Banner` nunca muestra stacktrace ni códigos; mensajes de `Validations` en español. Ajustes: se eliminó el código `ACTIVE` visible en "Datos y seguro" y el `null` posible en nombres y médico. |
| RNF-07 | Consistencia ante fallos de conexión | **Cumple** | Cabecera `Idempotency-Key` generada una vez por intento (`BookingSession.bookingKey()`) y reutilizada en cada "Reintentar"; se renueva al cambiar de cupo. Probado en `BookingSessionTest.idempotencyKeyIsReusedOnRetries`. |
| RNF-8 | Documentación de la API | **No aplica** (API) | El kiosco se apoya en `docs/api-contract.md` y los DTO (`Dtos`) reflejan ese contrato; `DtosJsonTest` lee los ejemplos del documento. |

## Reglas del README raíz (rúbrica inferida)

| Regla | Estado | Evidencia |
| :--- | :--- | :--- |
| Arquitectura n-capas: el cliente no accede a la base de datos | **Cumple** | `desktop/pom.xml` no incluye ningún driver JDBC ni dependencia de PostgreSQL; la única salida de red es `ApiClient` (HTTP/JSON). Ver sección "Capa de conexión a la API" del README. |
| Separación de responsabilidades en el cliente | **Cumple** | Paquetes `app` (arranque), `screens` (pantallas y estado), `ui` (componentes reutilizables), `api` (conexión), `dev` (herramientas de desarrollo cargadas por reflexión). |
| Clases en `PascalCase`, métodos y variables en `camelCase`, constantes en `UPPER_SNAKE_CASE` | **Cumple** | Revisado en todos los archivos; identificadores en inglés y textos al paciente en español, como pide el README. |
| Sin bloques `catch` vacíos; excepciones personalizadas y logging | **Cumple** | `ApiException` como excepción propia; todos los `catch` registran con `java.util.logging` (`KioskApp`, `KioskConfig`, `Fonts`, `DemoCapture`) o convierten a `ApiException` (`ApiClient.convert`). El único `catch` que devuelve `null` (`DatePicker` de fecha de nacimiento) está comentado y delega en la validación. |
| Inyección de dependencias y bajo acoplamiento | **Cumple** | `ApiClient` se crea una vez en `KioskApp` y llega a cada pantalla por la interfaz `KioskContext`; las pantallas no conocen `KioskShell`. |
| Credenciales en variables de entorno | **No aplica** | El kiosco no guarda credenciales; solo la URL de la API (`-Dapi.url` o `kiosk.properties`). |
| Pruebas unitarias con JUnit 5 | **Cumple** | `ValidationsTest`, `DatesTest`, `BookingSessionTest`, `DtosJsonTest`, `DtosTest`, `ApiExceptionTest`, `ApiClientTest` (63 pruebas). CI en `.github/workflows/desktop-ci.yml`. |
| Commits convencionales en español e imperativo | **Cumple** | Ver `git log` de la rama `feature/desktop-kiosk-verification`. |
| Javadoc en miembros públicos | **Cumple con ajuste** | Clases y métodos relevantes documentados en español; los métodos triviales (getters de `BookingSession`, fábricas de `Screen`) se mantienen sin Javadoc por brevedad. |
| Fidelidad con el prototipo (`docs/prototype`) | **Cumple con ajuste** | Mismos tokens de color, tipografías (Onest, IBM Plex Mono), barra de pasos, teclado, tarjetas y franja de fechas. Diferencias conocidas: el prototipo separa "Datos" y "Seguro" en dos pantallas (el kiosco las une en el paso 2) y tiene una pantalla de bienvenida que el kiosco reemplaza por la identificación directa. |

## Correcciones aplicadas en esta verificación

| Commit | Qué se corrigió |
| :--- | :--- |
| `fix(desktop): tratar solo PATIENT_NOT_FOUND como paciente nuevo al identificarse` | Un 404 ajeno al contrato enviaba al registro; ahora muestra error con reintento. Pruebas en `ApiExceptionTest` y `ApiClientTest`. |
| `fix(desktop): mostrar el estado de la cobertura en español y nombres sin nulos` | Código `ACTIVE` visible al paciente y `null` posible en `fullName()`. Pruebas en `DtosTest`. |
| `fix(desktop): aceptar la fecha de nacimiento en formato dd/mm/aaaa` | El `DatePicker` usaba el formato del idioma de la máquina pese a la ayuda "dd/mm/aaaa". |
| `fix(desktop): avisar si no hay sedes disponibles al cargar el catálogo` | Lista vacía de sedes provocaba un fallo al seleccionar la primera. |
| `fix(desktop): avisar cuando el horario elegido dejó de estar libre` | El cupo se descartaba en silencio al volver a "Fecha y hora". |
| `fix(desktop): mostrar médico por asignar en Mis citas cuando falta` | La tarjeta podía imprimir `null`. |
| `fix(desktop): dar acción explícita al botón de continuar del aviso de inactividad` | El botón dependía del filtro global de actividad. |

## Observaciones sin corregir (menores)

- El teclado en pantalla es numérico; para carné de extranjería o pasaporte con letras se usa el teclado físico (el campo acepta letras y números). En el kiosco físico el documento más frecuente es el DNI.
- En "Mis citas" la barra lateral mantiene resaltado el último paso visitado, porque esa pantalla no forma parte del asistente de cinco pasos.
- El archivo `kiosk.properties` de configuración solo se busca en `%ProgramData%` (Windows, donde se instala el kiosco); en macOS/Linux se usa `-Dapi.url`.
- Reprogramar y anular no envían `Idempotency-Key` porque el contrato no la define para esos `PATCH`; ambos son seguros de repetir (la API responde con la cita actualizada o `APPOINTMENT_NOT_MODIFIABLE`).
