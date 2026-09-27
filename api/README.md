# Módulo Backend - API REST MediCitas Anglo

API en **Spring Boot 4.1.1 / Java 21** que atiende al Kiosko de Autoatención: identificación y registro de pacientes, catálogos, disponibilidad de cupos y reserva, reprogramación y anulación de citas con sus notificaciones.

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

Desde la raíz del repositorio:

```bash
docker compose up --build -d
```

- `postgres-db` ejecuta [`docs/database/initial_schema.sql`](../docs/database/initial_schema.sql) la primera vez que crea el volumen.
- `api-service` arranca con `SPRING_PROFILES_ACTIVE=postgres` y `ddl-auto=validate`: si una entidad no coincide con el script, la API no arranca.
- En el primer arranque, con la base vacía, se cargan los mismos datos de demostración. Para desactivarlo usa `CLINIC_DEMO_DATA=false`. Como la agenda se genera a partir de la fecha de ese arranque, borra el volumen (`docker compose down -v`) si quieres regenerarla.

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

Ejemplo: `./mvnw spring-boot:run -Dspring-boot.run.arguments=--messaging.simulate-failure=true`

## Pruebas

```bash
./mvnw test
```

- **Unitarias** (`AppointmentServiceTest`, Mockito): reglas de reserva, cruce de horarios, idempotencia y anulación.
- **Integración** (`@SpringBootTest` + MockMvc sobre H2):
  - documento duplicado y formato de errores;
  - reserva con notificaciones;
  - dos reservas simultáneas del mismo cupo (solo una se acepta);
  - cita duplicada y reintento idempotente;
  - reprogramación y anulación que liberan cupos;
  - caída del servicio de mensajería.
- **Esquema PostgreSQL** (`PostgresSchemaTest`): levanta un PostgreSQL 15 embebido, sin Docker, ejecuta `initial_schema.sql` y arranca la API con `ddl-auto=validate` y los datos de demostración.

## Estructura

```text
com.medicitas.api
├── appointment   Cita, historial y reglas de reserva / reprogramación / anulación
├── catalog       Sede, especialidad, médico, aseguradora y plan
├── common        Errores del contrato, reloj de la clínica, OpenAPI
├── demo          Datos de demostración
├── notification  Notificaciones, proveedor de mensajería simulado y tarea programada
├── patient       Paciente y seguro
└── schedule      Cupo y consulta de disponibilidad
```
