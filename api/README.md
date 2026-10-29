# Módulo Backend - API REST MediCitas Anglo

API en **Spring Boot 4.1.1 / Java 21** que atiende al Kiosko de Autoatención: identificación y registro de pacientes, catálogos, disponibilidad de cupos y reserva, reprogramación y anulación de citas con sus notificaciones. Incluye el módulo de seguridad del personal (admisión, médicos y administración) con JWT, el registro de auditoría y el panel de administración (usuarios, catálogo, agendas, auditoría y reportes).

El contrato completo (endpoints, JSON y códigos de error) está en [`docs/api-contract.md`](../docs/api-contract.md) y el modelo de datos en [`docs/diagrams/physical-model/physical-model.puml`](../docs/diagrams/physical-model/physical-model.puml).

## Requisitos

- **Java 21** (JDK). Maven no es necesario: el wrapper `./mvnw` lo descarga.
- Docker solo si quieres usar PostgreSQL.

## Ejecutar la demo (sin Docker)

```bash
cd api
./mvnw spring-boot:run
```

Arranca con el perfil `h2`: una base en memoria que se recrea en cada arranque y se llena con **datos de demostración**:

- las sedes San Isidro y La Molina, 12 especialidades y 20 médicos ficticios;
- 4 aseguradoras con 2 planes cada una (copagos inventados);
- 14 días de agenda (lunes a sábado, 07:00 a 13:00, cupos de 20 minutos), con cerca del 30 % ya ocupado;
- para los reportes, 14 días de agenda pasada de seis médicos (08:00 a 10:00) con citas ya cerradas: atendidas, no asistidas y anuladas.

| Documento | Paciente | Situación |
|---|---|---|
| DNI `45871236` | Lucía Paredes Quispe | Rímac Seguros · Red Médica (vigente), con la cita `MCA-7K2Q9` reservada |
| DNI `70123456` | Diego Salazar Rojas | Atención particular, sin citas |

- Swagger UI: http://localhost:8080/swagger-ui.html
- Especificación OpenAPI: http://localhost:8080/v3/api-docs

Prueba rápida:

```bash
curl "http://localhost:8080/api/v1/patients?documentType=DNI&documentNumber=45871236"
```

## Ejecutar con PostgreSQL (Docker)

Desde la raíz del repositorio, con la clave JWT definida en un archivo `.env` (o en el entorno):

```bash
cp .env.example .env   # y cambia el valor de SECURITY_JWT_SECRET
docker compose up --build -d
```

- `SECURITY_JWT_SECRET` es obligatoria: `docker compose` se detiene si no está definida y, fuera del perfil `h2`, la API no arranca sin ella.
- `postgres-db` ejecuta [`docs/database/initial_schema.sql`](../docs/database/initial_schema.sql) la primera vez que crea el volumen.
- `api-service` arranca con `SPRING_PROFILES_ACTIVE=postgres` y `ddl-auto=validate`: si una entidad no coincide con el script, la API no arranca.
- En el primer arranque, con la base vacía, se cargan los mismos datos de demostración. Para desactivarlo usa `CLINIC_DEMO_DATA=false`. Como la agenda se genera a partir de la fecha de ese arranque, borra el volumen (`docker compose down -v`) si quieres regenerarla.

## Seguridad y usuarios de prueba

Los endpoints del kiosko son públicos y **no cambian**: el paciente nunca inicia sesión. El personal de la clínica sí: inicia sesión con usuario y contraseña, recibe un token JWT (firmado con una clave HMAC, vigente 8 horas por defecto) y lo envía en cada petición protegida con la cabecera `Authorization: Bearer <token>`. Las contraseñas se guardan como hash BCrypt en `app_user`.

Usuarios que crea `DemoDataLoader` (perfil `h2`, o `postgres` en el primer arranque; si una base creada con una versión anterior ya tiene datos pero `app_user` está vacía, el cargador crea únicamente estos usuarios en el siguiente arranque, sin tocar el resto):

| Usuario | Contraseña | Rol | Uso |
|---|---|---|---|
| `admin` | `admin123` | `ADMIN` | Administración: usuarios, catálogos y auditoría |
| `admision` | `admision123` | `FRONT_DESK` | Central de citas / admisión |
| `dr.<apellido>` (p. ej. `dr.benavides`, `dr.villanueva`, `dr.huaman`, `dr.galvez`) | `doctor123` | `DOCTOR` | Un usuario por cada médico de demostración, vinculado por `doctor_id`; el apellido va en minúsculas y sin tildes |

Inicio de sesión y uso del token:

```bash
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}'
# {"token":"eyJ...","expiresAt":"2026-10-20T16:00:00-05:00","user":{"id":1,"username":"admin","role":"ADMIN","fullName":"admin","doctorId":null}}

curl http://localhost:8080/api/v1/auth/me -H "Authorization: Bearer eyJ..."
curl http://localhost:8080/api/v1/admin/users -H "Authorization: Bearer eyJ..."
```

En Swagger UI, el botón **Authorize** acepta el token (esquema `bearerAuth`).

Rutas y roles (`SecurityConfig`):

| Ruta | Acceso |
|---|---|
| `POST /api/v1/auth/login`, `/api/v1/patients/**`, `/api/v1/appointments/**`, `/api/v1/availability/**`, `/api/v1/specialties/**`, `/api/v1/locations/**`, `/api/v1/insurers/**`, `/actuator/health`, `/v3/api-docs/**`, `/swagger-ui/**` | Público (kiosko y documentación) |
| `GET /api/v1/auth/me` | Cualquier usuario autenticado |
| `/api/v1/doctor/**` | `DOCTOR` |
| `/api/v1/front-desk/**` | `FRONT_DESK` o `ADMIN` |
| `/api/v1/admin/**` | `ADMIN` |
| Cualquier otra ruta | Autenticado |

Los prefijos del kiosko se abren completos porque el paciente nunca inicia sesión: **todo endpoint nuevo del personal debe publicarse bajo `/api/v1/doctor/**`, `/api/v1/front-desk/**` o `/api/v1/admin/**`**; si se colocara bajo `/patients` o `/appointments` quedaría público sin querer.

Respuestas de error, con el mismo formato `ErrorResponse` del contrato: `401 UNAUTHORIZED` sin token, con token vencido, manipulado o emitido por otro sistema (se valida la firma, la vigencia y el emisor `medicitas-api`), `403 FORBIDDEN` con un rol que no corresponde, `401 INVALID_CREDENTIALS` si el usuario o la contraseña no coinciden (sin decir cuál) y `403 USER_INACTIVE` si el usuario fue desactivado. El estado y el rol del usuario se comprueban en **cada petición con token**, no solo al iniciar sesión: la autoridad `ROLE_<rol>` se construye con el rol guardado en `app_user` (el claim `role` del token es solo informativo), así que desactivar a un usuario o cambiarle el rol surte efecto de inmediato aunque su token no haya vencido.

**Administración (issue #46).** El rol `ADMIN` gestiona bajo `/api/v1/admin/**` los usuarios (alta, perfil, médico vinculado, estado y contraseña; un administrador no puede desactivarse a sí mismo), las especialidades con sus sedes y médicos (lo que se desactiva deja de verse en el kiosko), la agenda de cada médico (generación de cupos sin duplicados y bloqueo de cupos libres), la consulta paginada de la auditoría y los reportes de citas por estado, tasa de inasistencia y ocupación, exportables a CSV y a Excel. El detalle de cada endpoint está en las secciones 7 y 8 del [contrato](../docs/api-contract.md).

**Auditoría.** Cada reserva, reprogramación y anulación escribe una fila en `audit_log` (`APPOINTMENT_BOOKED`, `APPOINTMENT_RESCHEDULED`, `APPOINTMENT_CANCELLED`) con el usuario responsable, la IP de origen y un detalle legible en español, sin nombres técnicos (por ejemplo «Cita MCA-7K2Q9 reservada en el cupo 12 (2026-10-21 09:00, Carla Benavides Ortiz) para el paciente 5 desde el kiosko»); cuando actúa el paciente desde el kiosko el usuario queda en nulo. El inicio de sesión también se audita (`USER_LOGGED_IN`), igual que cada cambio del panel de administración (`USER_*`, `SPECIALTY_*`, `DOCTOR_*`, `SLOTS_GENERATED`, `SLOT_BLOCKED`, `SLOT_UNBLOCKED`). Las mismas operaciones guardan en `appointment_history` el usuario del personal que las realizó, y una reserva hecha con token se registra con canal `FRONT_DESK`.

## Endpoints

Base: `/api/v1`. Resumen; el detalle está en el [contrato](../docs/api-contract.md).

| Método | Ruta | Uso |
|---|---|---|
| GET | `/patients?documentType&documentNumber` | Identificar paciente (RF-02) |
| POST | `/patients` | Registrar paciente (RF-01) |
| PUT | `/patients/{id}/insurance` | Asociar plan o atención particular (RF-03) |
| GET | `/locations`, `/specialties?locationId`, `/insurers` | Catálogos (RF-03, RF-04) |
| GET | `/availability?specialtyId&locationId&from&to` | Cupos libres, hasta 14 días (RF-05) |
| POST | `/appointments` + cabecera `Idempotency-Key` | Reservar (RF-06 a RF-08, RNF-07) |
| GET | `/patients/{id}/appointments?upcoming=true` | Mis citas |
| PATCH | `/appointments/{id}/reschedule` | Reprogramar (RF-09) |
| PATCH | `/appointments/{id}/cancellation` | Anular (RF-10) |
| GET | `/appointments/{id}/notifications` | Estado de las notificaciones (RF-13) |
| POST | `/auth/login` | Inicio de sesión del personal; devuelve el token Bearer |
| GET | `/auth/me` | Usuario autenticado (requiere token) |
| GET | `/admin/users` | Listar usuarios del personal (rol `ADMIN`) |
| GET | `/doctor/agenda?date` | Agenda del día del médico autenticado; por defecto hoy (rol `DOCTOR`, CU-011) |
| PATCH | `/doctor/appointments/{id}/attendance` | Registrar asistencia o inasistencia (`ATTENDED` / `NO_SHOW`) de una cita reservada de hoy o pasada (rol `DOCTOR`, CU-012) |
| GET | `/front-desk/patients?documentType&documentNumber` | Buscar paciente desde ventanilla (`FRONT_DESK` o `ADMIN`, CU-013) |
| POST | `/front-desk/patients` | Registrar paciente desde ventanilla (RF-01) |
| GET | `/front-desk/patients/{id}/insurance` | Validar aseguradora: coberturas con `valid` y mensaje; si ninguna es válida, atención particular (CU-014) |
| PUT | `/front-desk/patients/{id}/insurance` | Asociar o actualizar seguro, o dejar como particular (RF-03) |
| GET | `/front-desk/patients/{id}/appointments?upcoming` | Citas del paciente |
| POST | `/front-desk/appointments` + cabecera `Idempotency-Key` | Reservar con canal `FRONT_DESK` (CU-015, RF-07, RF-08) |
| PATCH | `/front-desk/appointments/{id}/reschedule` | Reprogramar desde ventanilla (CU-016) |
| PATCH | `/front-desk/appointments/{id}/cancellation` | Anular desde ventanilla con motivo obligatorio (CU-016) |
<!-- Issue #45: filas de /doctor/** y /front-desk/**. La disponibilidad para ventanilla es el endpoint público /availability. -->
| GET / POST | `/admin/users`, `/admin/users/{id}` + PUT, `/admin/users/{id}/password` (PUT) | Gestión de usuarios: alta, edición de perfil/médico/estado y contraseña (`ADMIN`) |
| GET | `/admin/locations` | Todas las sedes, incluidas inactivas (`ADMIN`) |
| GET / POST / PUT | `/admin/specialties`, `/admin/specialties/{id}` | Especialidades con sus sedes, duración de cita y estado (`ADMIN`) |
| GET / POST / PUT | `/admin/doctors`, `/admin/doctors/{id}` | Médicos con especialidad y estado (`ADMIN`) |
| GET | `/admin/slots?doctorId&from&to` | Cupos de un médico con su estado (`ADMIN`) |
| POST | `/admin/slots/generate` | Generar cupos por médico, sede, consultorio, rango, horario y días, sin duplicar (`ADMIN`) |
| PATCH | `/admin/slots/{id}/block`, `/admin/slots/{id}/unblock` | Bloquear un cupo libre / desbloquearlo (`ADMIN`) |
| GET | `/admin/audit?entity&action&userId&from&to&page&size` | Auditoría paginada (`ADMIN`) |
| GET | `/admin/reports/appointments`, `/no-show-rate`, `/occupancy` (`?from&to`) | Citas por estado, tasa de inasistencia y ocupación (`ADMIN`) |
| GET | `/admin/reports/export?report&from&to&format` | Descargar un reporte en CSV UTF-8 (por defecto) o Excel con `format=xlsx` (`ADMIN`) |

## Configuración

| Propiedad | Por defecto | Descripción |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | `h2` | `h2` (demo en memoria) o `postgres` |
| `SPRING_DATASOURCE_URL` / `_USERNAME` / `_PASSWORD` | `localhost:5432/citas_medicas_db` | Conexión del perfil `postgres` |
| `CLINIC_DEMO_DATA` | `true` | Carga datos de demostración si la base está vacía (perfil `postgres`) |
| `clinic.time-zone` | `America/Lima` | Zona horaria de la clínica ("hoy", disponibilidad y recordatorios) |
| `messaging.simulate-failure` | `false` | El proveedor simulado rechaza todos los envíos (demuestra RF-14) |
| `messaging.max-attempts` | `3` | Intentos antes de marcar una notificación como `FAILED` |
| `messaging.retry-minutes` | `10` | Espera entre intentos |
| `messaging.reminder-hours-ahead` | `24` | Anticipación del recordatorio |
| `messaging.job-interval-ms` | `300000` | Frecuencia de la tarea de recordatorios (proceso 07) |
| `SECURITY_JWT_SECRET` (`security.jwt.secret`) | solo en `h2`: clave de desarrollo | Clave HMAC (mínimo 32 caracteres) con la que se firman los tokens. Únicamente el perfil `h2` (`application-h2.properties`) trae una clave de desarrollo; con `postgres` o cualquier otro perfil la variable es obligatoria y la API no arranca sin ella (`docker compose` la toma de un archivo `.env`) |
| `SECURITY_JWT_EXPIRATION_MINUTES` (`security.jwt.expiration-minutes`) | `480` | Vigencia del token |
| `SECURITY_CORS_ALLOWED_ORIGINS` (`security.cors.allowed-origins`) | `http://localhost:5173` | Orígenes del navegador autorizados (paneles web del personal), separados por coma |
| `SECURITY_AUDIT_TRUST_FORWARDED_FOR` (`security.audit.trust-forwarded-for`) | `false` | Si la API corre detrás de un proxy inverso de confianza, registra en la auditoría la IP de `X-Forwarded-For`; apagado, usa la IP de la conexión para que ningún cliente pueda falsificarla |

Ejemplo: `./mvnw spring-boot:run -Dspring-boot.run.arguments=--messaging.simulate-failure=true`

## Pruebas

```bash
./mvnw test
```

- **Unitarias** (`AppointmentServiceTest`, Mockito): reglas de reserva, cruce de horarios, idempotencia y anulación.
- **Unitarias de seguridad y auditoría** (`AuthServiceTest`, `CurrentUserServiceTest`, `AppUserTest`, `AuditServiceTest`): emisión y claims del JWT, credenciales incorrectas, usuario inactivo, resolución del usuario actual, IP de origen.
- **Integración** (`@SpringBootTest` + MockMvc sobre H2):
  - documento duplicado y formato de errores;
  - reserva con notificaciones;
  - dos reservas simultáneas del mismo cupo (solo una se acepta);
  - cita duplicada y reintento idempotente;
  - reprogramación y anulación que liberan cupos;
  - caída del servicio de mensajería;
  - acceso por rol (`SecurityAccessTest`): 401 sin token, 403 con rol incorrecto, login correcto e incorrecto, kiosko público;
  - auditoría de citas (`AppointmentAuditTest`): reservar, reprogramar y anular generan sus filas en `audit_log`, con o sin usuario;
  <!-- Issue #45 -->
  - agenda y asistencia del médico (`DoctorAgendaApiTest`): cada médico ve solo su agenda, fecha por defecto hoy, marcar `ATTENDED`/`NO_SHOW` actualiza estado, historial `ATTENDANCE` con usuario, auditoría `ATTENDANCE_REGISTERED` y cancela notificaciones pendientes; no se marca una cita futura, anulada ni de otro médico; 401/403 por rol;
  - ventanilla (`FrontDeskApiTest`): búsqueda y registro de pacientes, validación de aseguradora vigente/suspendida/vencida/sin seguro, reserva con canal `FRONT_DESK` auditada con el usuario `admision`, cupo tomado (409) y cita duplicada (409), reprogramación y anulación con motivo obligatorio (400 sin motivo), `DOCTOR` recibe 403 y `ADMIN` sí opera.
  - administración (`AdminUsersApiTest`, `AdminCatalogApiTest`, `AdminSlotsApiTest`, `AdminAuditApiTest`): usuarios, especialidades y médicos con su reflejo en el kiosko, generación y bloqueo de cupos, consulta de auditoría, y 403 para los demás roles;
  - reportes (`ReportApiTest`): cifras exactas sobre citas con estados conocidos y exportación CSV.
  - exportación a Excel (`ReportXlsxApiTest`, `ReportServiceXlsxTest`, `XlsxWriterTest`): libro válido por reporte, mismas filas que el CSV, texto nunca como fórmula y permisos.
- **Unitarias de médico y admisión** (`DoctorAgendaServiceTest`, `FrontDeskServiceTest`, Mockito): reglas de fecha, estado y pertenencia de la cita; validez de coberturas; delegación en los servicios del kiosko.
- **Unitarias de administración y reportes** (`SlotGenerationServiceTest`, `ReportServiceTest`): cálculo de horarios sin duplicados y reglas de generación; citas por estado, tasa de inasistencia, ocupación, redondeo de tasas y CSV.
- **Esquema PostgreSQL** (`PostgresSchemaTest`): levanta un PostgreSQL 15 embebido, sin Docker, ejecuta `initial_schema.sql` y arranca la API con `ddl-auto=validate` y los datos de demostración.

## Estructura

```text
com.medicitas.api
├── admin         Panel de administración: usuarios, catálogo, agendas (generación y bloqueo) y consulta de auditoría
├── appointment   Cita, historial y reglas de reserva / reprogramación / anulación
├── audit         Registro de auditoría (audit_log) y su servicio
├── catalog       Sede, especialidad, médico, aseguradora y plan
├── common        Errores del contrato, reloj de la clínica, OpenAPI
├── demo          Datos de demostración (incluye los usuarios por rol)
├── notification  Notificaciones, proveedor de mensajería simulado y tarea programada
├── patient       Paciente y seguro
├── report        Reportes de citas, inasistencia y ocupación con exportación CSV y Excel (Apache POI)
├── schedule      Cupo y consulta de disponibilidad
├── security      Usuarios y roles, login con JWT, reglas de acceso y usuario actual
├── doctor        Agenda del día y registro de asistencia del médico (Issue #45)
└── frontdesk     Operaciones de ventanilla: pacientes, seguro y citas con canal FRONT_DESK (Issue #45)
```
