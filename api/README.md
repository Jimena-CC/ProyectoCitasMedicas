# Módulo Backend - API REST MediCitas Anglo

API en **Spring Boot 4.1.1 / Java 21** que atiende al Kiosko de Autoatención: identificación y registro de pacientes, catálogos, disponibilidad de cupos y reserva, reprogramación y anulación de citas con sus notificaciones. Incluye el módulo de seguridad del personal (admisión, médicos y administración) con JWT y el registro de auditoría de las citas.

El contrato completo (endpoints, JSON y códigos de error) está en [`docs/api-contract.md`](../docs/api-contract.md) y el modelo de datos en [`docs/diagrams/03-entity-relationship-diagram.puml`](../docs/diagrams/03-entity-relationship-diagram.puml).

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
- 14 días de agenda (lunes a sábado, 07:00 a 13:00, cupos de 20 minutos), con cerca del 30 % ya ocupado.

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
echo 'SECURITY_JWT_SECRET=una-clave-secreta-de-al-menos-32-caracteres' > .env
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
| `POST /api/v1/auth/login`, `/api/v1/patients/**`, `/api/v1/appointments/**`, `/api/v1/availability/**`, `/api/v1/specialties/**`, `/api/v1/locations/**`, `/api/v1/insurers/**`, `/api/v1/notifications/**`, `/actuator/health`, `/v3/api-docs/**`, `/swagger-ui/**` | Público (kiosko y documentación) |
| `GET /api/v1/auth/me` | Cualquier usuario autenticado |
| `/api/v1/doctor/**` | `DOCTOR` |
| `/api/v1/front-desk/**` | `FRONT_DESK` o `ADMIN` |
| `/api/v1/admin/**` | `ADMIN` |
| Cualquier otra ruta | Autenticado |

Los prefijos del kiosko se abren completos porque el paciente nunca inicia sesión: **todo endpoint nuevo del personal debe publicarse bajo `/api/v1/doctor/**`, `/api/v1/front-desk/**` o `/api/v1/admin/**`**; si se colocara bajo `/patients` o `/appointments` quedaría público sin querer.

Respuestas de error, con el mismo formato `ErrorResponse` del contrato: `401 UNAUTHORIZED` sin token, con token vencido, manipulado o emitido por otro sistema (se valida la firma, la vigencia y el emisor `medicitas-api`), `403 FORBIDDEN` con un rol que no corresponde, `401 INVALID_CREDENTIALS` si el usuario o la contraseña no coinciden (sin decir cuál) y `403 USER_INACTIVE` si el usuario fue desactivado. El estado y el rol del usuario se comprueban en **cada petición con token**, no solo al iniciar sesión: la autoridad `ROLE_<rol>` se construye con el rol guardado en `app_user` (el claim `role` del token es solo informativo), así que desactivar a un usuario o cambiarle el rol surte efecto de inmediato aunque su token no haya vencido.

**Auditoría.** Cada reserva, reprogramación y anulación escribe una fila en `audit_log` (`APPOINTMENT_BOOKED`, `APPOINTMENT_RESCHEDULED`, `APPOINTMENT_CANCELLED`) con el usuario responsable, la IP de origen y un detalle legible en español, sin nombres técnicos (por ejemplo «Cita MCA-7K2Q9 reservada en el cupo 12 (2026-10-21 09:00, Carla Benavides Ortiz) para el paciente 5 desde el kiosko»); cuando actúa el paciente desde el kiosko el usuario queda en nulo. El inicio de sesión también se audita (`USER_LOGGED_IN`). Las mismas operaciones guardan en `appointment_history` el usuario del personal que las realizó, y una reserva hecha con token se registra con canal `FRONT_DESK`.

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
  - auditoría de citas (`AppointmentAuditTest`): reservar, reprogramar y anular generan sus filas en `audit_log`, con o sin usuario.
- **Esquema PostgreSQL** (`PostgresSchemaTest`): levanta un PostgreSQL 15 embebido, sin Docker, ejecuta `initial_schema.sql` y arranca la API con `ddl-auto=validate` y los datos de demostración.

## Estructura

```text
com.medicitas.api
├── appointment   Cita, historial y reglas de reserva / reprogramación / anulación
├── audit         Registro de auditoría (audit_log) y su servicio
├── catalog       Sede, especialidad, médico, aseguradora y plan
├── common        Errores del contrato, reloj de la clínica, OpenAPI
├── demo          Datos de demostración (incluye los usuarios por rol)
├── notification  Notificaciones, proveedor de mensajería simulado y tarea programada
├── patient       Paciente y seguro
├── schedule      Cupo y consulta de disponibilidad
└── security      Usuarios y roles, login con JWT, reglas de acceso y usuario actual
```
