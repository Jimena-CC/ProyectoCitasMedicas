# Contrato de la API - Kiosko de Autoatención

Contrato REST entre el **Kiosko (JavaFX)** y la **API MediCitas (Spring Boot)** para las 5 pantallas funcionales de la Fase 1, más los endpoints de autenticación del personal (sección 6). El modelo de datos que lo respalda es el [Entity Relationship Diagram](diagrams/03-entity-relationship-diagram.puml).

Los identificadores del contrato (rutas, campos, estados y códigos de error) están en inglés. Los textos dirigidos al paciente —el campo `message` de cada error— van en español.

- **Base URL:** `http://localhost:8080/api/v1`
- **Formato:** JSON en UTF-8. Fechas `yyyy-MM-dd`, horas `HH:mm`, instantes ISO-8601 con zona `-05:00`.
- **Documentación viva:** `/swagger-ui.html` (springdoc-openapi).

## Pantallas y endpoints

| # | Pantalla del kiosko | Endpoints | Requerimientos |
|---|---|---|---|
| 1 | Identificación | `GET /patients` | RF-02 |
| 2 | Datos y seguro | `POST /patients`, `GET /insurers`, `PUT /patients/{id}/insurance` | RF-01, RF-03 |
| 3 | Especialidad y sede | `GET /locations`, `GET /specialties` | RF-04 |
| 4 | Fecha y hora | `GET /availability` | RF-05 |
| 5 | Confirmación y Mis citas | `POST /appointments`, `GET /patients/{id}/appointments`, `PATCH /appointments/{id}/reschedule`, `PATCH /appointments/{id}/cancellation` | RF-06 a RF-11 |

---

## 1. Patients

### `GET /patients?documentType=DNI&documentNumber=45871236`

`200 OK`

```json
{
  "id": 1,
  "documentType": "DNI",
  "documentNumber": "45871236",
  "firstNames": "Lucía",
  "lastNames": "Paredes Quispe",
  "birthDate": "1991-04-18",
  "phone": "987654321",
  "email": "lucia.paredes@correo.pe",
  "insurance": {
    "patientInsuranceId": 3,
    "insurer": "Rímac Seguros",
    "plan": "Red Médica",
    "policyNumber": "RM-00458712",
    "coverageStatus": "ACTIVE"
  }
}
```

`insurance` es `null` cuando el paciente se atiende de forma particular. Devuelve `404 PATIENT_NOT_FOUND` si no existe.

### `POST /patients`

```json
{
  "documentType": "DNI",
  "documentNumber": "72015893",
  "firstNames": "Diego",
  "lastNames": "Salazar Rojas",
  "birthDate": "1988-11-02",
  "sex": "M",
  "phone": "956112233",
  "email": "diego.salazar@correo.pe",
  "acceptsNotifications": true
}
```

`201 Created` con el mismo cuerpo que `GET /patients`. `409 DUPLICATE_DOCUMENT` si el documento ya existe.

Validaciones: `DNI` exactamente 8 dígitos · `CE` 9 a 12 caracteres · `PAS` 6 a 12 · `phone` 9 dígitos empezando en 9 · `email` válido · `birthDate` en el pasado.

### `PUT /patients/{id}/insurance`

```json
{ "planId": 2, "policyNumber": "RM-00458712" }
```

`200 OK` con el paciente actualizado. Enviar `{ "planId": null }` registra atención particular.

---

## 2. Catálogos

### `GET /insurers`

```json
[
  {
    "id": 1,
    "name": "Rímac Seguros",
    "plans": [
      { "id": 1, "name": "Red Preferente", "consultationCopay": 45.00 },
      { "id": 2, "name": "Red Médica", "consultationCopay": 60.00 }
    ]
  }
]
```

### `GET /locations`

```json
[
  { "id": 1, "code": "SI", "name": "Sede San Isidro", "address": "Av. Alfredo Salazar 350", "district": "San Isidro" },
  { "id": 2, "code": "LM", "name": "Sede La Molina", "address": "Av. La Fontana 362", "district": "La Molina" }
]
```

### `GET /specialties?locationId=1`

```json
[
  { "id": 4, "name": "Cardiología", "description": "Evaluación y control del corazón y la presión arterial.", "appointmentMinutes": 20 }
]
```

---

## 3. Availability

### `GET /availability?specialtyId=4&locationId=1&from=2026-09-16&to=2026-09-29`

El rango máximo es de 14 días. Devuelve solo cupos libres a partir de la hora actual.

```json
{
  "days": [
    {
      "date": "2026-09-16",
      "freeSlots": 9,
      "slots": [
        {
          "slotId": 118,
          "startTime": "07:15",
          "endTime": "07:35",
          "room": "Consultorio 304",
          "doctor": { "id": 7, "fullName": "Carla Benavides Ortiz", "license": "045712" }
        }
      ]
    }
  ]
}
```

Los días sin cupos se incluyen con `freeSlots: 0` para pintar la franja de fechas completa. `license` es el número de colegiatura (CMP), que el kiosko muestra con esa etiqueta.

---

## 4. Appointments

### `POST /appointments`

Cabecera obligatoria `Idempotency-Key: <UUID>`: el kiosko genera una por intento de reserva y la reutiliza si reintenta tras un corte de red (RNF-07).

```json
{
  "patientId": 1,
  "slotId": 118,
  "patientInsuranceId": 3,
  "visitReason": "Control de presión arterial"
}
```

`201 Created` (o `200 OK` si la clave ya fue usada, con la misma cita):

```json
{
  "id": 52,
  "code": "MCA-7K2Q9",
  "status": "BOOKED",
  "date": "2026-09-16",
  "startTime": "07:15",
  "endTime": "07:35",
  "specialty": "Cardiología",
  "location": "Sede San Isidro",
  "room": "Consultorio 304",
  "doctor": "Carla Benavides Ortiz",
  "coverage": "Rímac Seguros · Red Médica",
  "visitReason": "Control de presión arterial",
  "rescheduleCount": 0,
  "bookedAt": "2026-09-15T21:42:10-05:00"
}
```

Errores: `409 SLOT_NOT_AVAILABLE` (RF-07) · `409 DUPLICATE_APPOINTMENT` (RF-08) · `422 COVERAGE_NOT_ACTIVE`.

### `GET /patients/{id}/appointments?upcoming=true`

Lista de citas en estado `BOOKED` con fecha futura, ordenada por fecha y hora.

### `PATCH /appointments/{id}/reschedule`

```json
{ "newSlotId": 131 }
```

`200 OK` con la cita actualizada. Errores: `409 SLOT_NOT_AVAILABLE`, `409 DUPLICATE_APPOINTMENT`, `422 APPOINTMENT_NOT_MODIFIABLE`.

### `PATCH /appointments/{id}/cancellation`

```json
{ "reason": "Viaje fuera de Lima" }
```

`200 OK` con `status: "CANCELLED"`. Error: `422 APPOINTMENT_NOT_MODIFIABLE`.

### `GET /appointments/{id}/notifications`

```json
[
  { "type": "CONFIRMATION", "channel": "EMAIL", "status": "SENT", "scheduledFor": "2026-09-15T21:42:10-05:00", "sentAt": "2026-09-15T21:42:12-05:00", "attempts": 1 },
  { "type": "REMINDER", "channel": "SMS", "status": "PENDING", "scheduledFor": "2026-09-15T07:15:00-05:00", "sentAt": null, "attempts": 0 }
]
```

---

## 5. Formato de error

Todas las respuestas de error usan la misma forma. `message` está pensado para mostrarse tal cual al paciente (RNF-06): en español, sin detalles técnicos.

```json
{
  "code": "SLOT_NOT_AVAILABLE",
  "message": "Ese horario acaba de ser reservado por otra persona. Elige otro horario disponible.",
  "fields": [],
  "timestamp": "2026-09-15T21:42:10-05:00"
}
```

| HTTP | `code` | Cuándo |
|---|---|---|
| 400 | `INVALID_DATA` | Falla Bean Validation; `fields` lista `{ "field", "message" }` |
| 401 | `UNAUTHORIZED` | Ruta del personal sin token, o con token vencido o manipulado |
| 401 | `INVALID_CREDENTIALS` | Usuario o contraseña incorrectos en el login (no se indica cuál) |
| 403 | `FORBIDDEN` | Hay sesión, pero el rol no puede usar esa ruta |
| 403 | `USER_INACTIVE` | El usuario fue desactivado por el administrador |
| 404 | `PATIENT_NOT_FOUND`, `APPOINTMENT_NOT_FOUND`, `RESOURCE_NOT_FOUND` | Id o documento inexistente |
| 409 | `DUPLICATE_DOCUMENT` | RF-01 |
| 409 | `SLOT_NOT_AVAILABLE` | RF-07 |
| 409 | `DUPLICATE_APPOINTMENT` | RF-08 |
| 422 | `COVERAGE_NOT_ACTIVE`, `APPOINTMENT_NOT_MODIFIABLE` | Regla de negocio incumplida |
| 500 | `INTERNAL_ERROR` | Mensaje genérico; el detalle solo va al log |

<!-- Issue #45 -->
Los módulos del personal (secciones 7 y 8) agregan `403 DOCTOR_NOT_LINKED` (usuario `DOCTOR` sin médico vinculado) y reutilizan `404 APPOINTMENT_NOT_FOUND` y `422 APPOINTMENT_NOT_MODIFIABLE` para la agenda del médico.

---

## 6. Autenticación y roles

Los endpoints de las secciones 1 a 4 son **públicos**: el kiosko no envía token y no cambia. El personal de la clínica se autentica con usuario y contraseña y recibe un **JWT** (HS256, vigente 480 minutos por defecto) que envía en cada petición protegida:

```http
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
```

Claims del token: `sub` (usuario), `role` (`ADMIN`, `FRONT_DESK` o `DOCTOR`), `uid` (id en `app_user`), `iss` = `medicitas-api`, `iat` y `exp`. La API verifica en cada petición la firma, la vigencia y el emisor del token, y además que el usuario de `uid` exista y siga activo: un usuario desactivado recibe `403 USER_INACTIVE` de inmediato en cualquier ruta, aunque su token no haya vencido. El rol que autoriza cada petición es el guardado en `app_user` en ese momento, no el del claim `role` (que solo informa al cliente): un cambio de rol aplica en la siguiente petición sin volver a iniciar sesión.

Las rutas públicas del kiosko (`/patients/**`, `/appointments/**`, `/availability/**`, `/specialties/**`, `/locations/**`, `/insurers/**`; las notificaciones van en `/appointments/{id}/notifications`) se abren completas; los endpoints del personal se publican siempre bajo `/doctor/**`, `/front-desk/**` o `/admin/**`.

| Ruta | Rol requerido |
|---|---|
| `POST /auth/login` | Ninguno |
| `GET /auth/me` | Cualquier usuario autenticado |
| `/doctor/**` | `DOCTOR` |
| `/front-desk/**` | `FRONT_DESK` o `ADMIN` |
| `/admin/**` | `ADMIN` |

### `POST /auth/login`

```json
{ "username": "admin", "password": "admin123" }
```

`200 OK`:

```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "expiresAt": "2026-10-20T16:00:00-05:00",
  "user": { "id": 1, "username": "admin", "role": "ADMIN", "fullName": "admin", "doctorId": null }
}
```

`fullName` es el nombre completo del médico vinculado (perfil `DOCTOR`) o el nombre de usuario en los demás perfiles; `doctorId` solo tiene valor para los médicos.

Errores: `400 INVALID_DATA` (campos vacíos) · `401 INVALID_CREDENTIALS` · `403 USER_INACTIVE`.

### `GET /auth/me`

Requiere token. `200 OK` con el mismo objeto `user` del login:

```json
{ "id": 3, "username": "dr.benavides", "role": "DOCTOR", "fullName": "Carla Benavides Ortiz", "doctorId": 1 }
```

Errores: `401 UNAUTHORIZED` · `403 USER_INACTIVE`.

### `GET /admin/users`

Requiere rol `ADMIN`. `200 OK`:

```json
[
  { "id": 1, "username": "admin", "role": "ADMIN", "fullName": "admin", "doctorId": null, "active": true, "lastAccess": "2026-10-20T08:00:00-05:00" },
  { "id": 2, "username": "admision", "role": "FRONT_DESK", "fullName": "admision", "doctorId": null, "active": true, "lastAccess": null },
  { "id": 3, "username": "dr.benavides", "role": "DOCTOR", "fullName": "Carla Benavides Ortiz", "doctorId": 1, "active": true, "lastAccess": null }
]
```

Errores: `401 UNAUTHORIZED` · `403 FORBIDDEN`.

### Ejemplos de error

Sin token en una ruta protegida:

```json
{ "code": "UNAUTHORIZED", "message": "Debes iniciar sesión para continuar.", "fields": [], "timestamp": "2026-10-20T08:00:00-05:00" }
```

Con token de `DOCTOR` en `/admin/**`:

```json
{ "code": "FORBIDDEN", "message": "No tienes permiso para realizar esta acción.", "fields": [], "timestamp": "2026-10-20T08:00:00-05:00" }
```

Con el token de un usuario que fue desactivado (en cualquier ruta):

```json
{ "code": "USER_INACTIVE", "message": "Tu usuario está desactivado. Comunícate con el administrador del sistema.", "fields": [], "timestamp": "2026-10-20T08:00:00-05:00" }
```

### Auditoría

Toda reserva, reprogramación y anulación de cita (desde el kiosko o con token) escribe una fila en `audit_log` con la acción (`APPOINTMENT_BOOKED`, `APPOINTMENT_RESCHEDULED`, `APPOINTMENT_CANCELLED`), la entidad `APPOINTMENT`, el id de la cita, el usuario responsable (`null` desde el kiosko), la IP de origen (la de la conexión; la de `X-Forwarded-For` solo si la API se configura detrás de un proxy de confianza) y un detalle legible en español, por ejemplo «Cita MCA-7K2Q9 reservada en el cupo 12 (2026-10-21 09:00, Carla Benavides Ortiz) para el paciente 5 desde el kiosko» o «... por admisión» cuando la reserva la hace el personal con token. El inicio de sesión se audita como `USER_LOGGED_IN`. Las citas reservadas con token se registran con `bookingChannel = FRONT_DESK`.

---

<!-- Issue #45: módulos de médico y admisión -->

## 7. Módulo de Médico

Rutas bajo `/doctor/**`, solo para el rol `DOCTOR` (token Bearer). El médico se resuelve desde el usuario autenticado (`app_user.doctor_id`): nunca se envía el id del médico en la petición. Un usuario `DOCTOR` sin médico vinculado recibe `403 DOCTOR_NOT_LINKED`. Casos de uso CU-011 y CU-012; requisitos RF-20 y RF-21.

### `GET /doctor/agenda?date=2026-10-20`

`date` es opcional (formato `YYYY-MM-DD`); por defecto es **hoy** en la zona horaria de la clínica (`America/Lima`). Devuelve todas las citas del médico en esa fecha, en cualquier estado, ordenadas por hora de inicio, más un resumen por estado.

`200 OK`:

```json
{
  "date": "2026-10-20",
  "doctor": { "id": 1, "fullName": "Carla Benavides Ortiz", "specialty": "Cardiología" },
  "summary": { "total": 3, "booked": 1, "attended": 1, "noShow": 0, "cancelled": 1 },
  "items": [
    {
      "appointmentId": 52,
      "code": "MCA-7K2Q9",
      "startTime": "09:00",
      "endTime": "09:20",
      "room": "Consultorio 201",
      "location": "Sede San Isidro",
      "patient": { "id": 1, "fullName": "Lucía Paredes Quispe", "documentType": "DNI", "documentNumber": "45871236", "phone": "987654321" },
      "visitReason": "Control de presión arterial",
      "insurance": "Rímac Seguros · Red Médica",
      "status": "ATTENDED",
      "bookingChannel": "KIOSK",
      "attendanceRegisteredAt": "2026-10-20T09:25:10-05:00"
    }
  ]
}
```

`insurance` es la cobertura usada al reservar o `"Particular"`. `attendanceRegisteredAt` solo tiene valor cuando `status` es `ATTENDED` o `NO_SHOW` (es la última actualización de la cita, que en ese estado corresponde al registro de asistencia); en los demás estados es `null`.

Errores: `400 INVALID_DATA` (fecha con formato inválido, `fields[0].field = "date"`) · `401 UNAUTHORIZED` · `403 FORBIDDEN` (otro rol) · `403 DOCTOR_NOT_LINKED`.

### `PATCH /doctor/appointments/{id}/attendance`

Registra el resultado de la atención de una cita **del médico autenticado**. Solo admite citas en estado `BOOKED` cuya fecha de cupo sea hoy o anterior.

```json
{ "status": "ATTENDED", "note": "Paciente llegó 10 minutos tarde" }
```

`status` acepta únicamente `ATTENDED` o `NO_SHOW` (cualquier otro valor es `400 INVALID_DATA`); `note` es opcional, de hasta 250 caracteres, y se guarda como detalle del evento en el historial.

Efectos: la cita pasa al estado indicado y actualiza `updatedAt`; se agrega a `appointment_history` un evento `ATTENDANCE` (`BOOKED → ATTENDED|NO_SHOW`, `user_id` = médico, `detail` = nota); se escribe en `audit_log` la acción `ATTENDANCE_REGISTERED` (entidad `APPOINTMENT`, usuario = médico); se cancelan todas las notificaciones `PENDING` de la cita (pasan a `CANCELLED`). El cupo permanece `BOOKED`.

`200 OK` con el mismo item de agenda actualizado:

```json
{
  "appointmentId": 52,
  "code": "MCA-7K2Q9",
  "startTime": "09:00",
  "endTime": "09:20",
  "room": "Consultorio 201",
  "location": "Sede San Isidro",
  "patient": { "id": 1, "fullName": "Lucía Paredes Quispe", "documentType": "DNI", "documentNumber": "45871236", "phone": "987654321" },
  "visitReason": "Control de presión arterial",
  "insurance": "Rímac Seguros · Red Médica",
  "status": "ATTENDED",
  "bookingChannel": "KIOSK",
  "attendanceRegisteredAt": "2026-10-20T09:25:10-05:00"
}
```

Errores:

| HTTP | `code` | Cuándo |
|---|---|---|
| 400 | `INVALID_DATA` | `status` ausente o distinto de `ATTENDED`/`NO_SHOW`; `note` de más de 250 caracteres |
| 403 | `DOCTOR_NOT_LINKED` | El usuario `DOCTOR` no tiene médico vinculado |
| 404 | `APPOINTMENT_NOT_FOUND` | La cita no existe **o pertenece a otro médico** (no se revela que exista) |
| 422 | `APPOINTMENT_NOT_MODIFIABLE` | La cita no está `BOOKED` (anulada o ya marcada), o su fecha es futura: «Esta cita aún no ha ocurrido. Registra la asistencia el día de la atención.» |

## 8. Módulo de Admisión

Rutas bajo `/front-desk/**`, para los roles `FRONT_DESK` y `ADMIN` (token Bearer). Reutilizan las mismas reglas del kiosko (`PatientService`, `AppointmentService`), de modo que toda operación queda atribuida al usuario autenticado en `appointment_history.user_id` y en `audit_log`, y las reservas se registran con `bookingChannel = FRONT_DESK`. Casos de uso CU-013 a CU-017; requisitos RF-22 a RF-26.

La **búsqueda de disponibilidad** no se duplica: la ventanilla usa el endpoint público `GET /availability?specialtyId&locationId&from&to` de la sección 3 (puede enviar el token, pero no es necesario).

### `GET /front-desk/patients?documentType=DNI&documentNumber=45871236`

Misma respuesta que `GET /patients` (sección 1): el paciente con su cobertura vigente en `insurance` (o `null` si es particular). Errores: `404 PATIENT_NOT_FOUND`.

### `POST /front-desk/patients`

Mismo cuerpo y validaciones que `POST /patients` (RF-01). `201 Created` con `Location: /api/v1/front-desk/patients/{id}`. Errores: `400 INVALID_DATA` · `409 DUPLICATE_DOCUMENT`.

### `GET /front-desk/patients/{id}/insurance`

Validación de aseguradora (CU-014): lista **todas** las coberturas registradas del paciente, vigentes primero. `valid` es `true` solo si la cobertura está `ACTIVE` y `validUntil` es nulo o no anterior a hoy.

`200 OK`:

```json
{
  "patientId": 1,
  "valid": true,
  "message": "El paciente tiene una cobertura vigente. Puedes reservar con ese seguro.",
  "coverages": [
    { "patientInsuranceId": 3, "insurer": "Rímac Seguros", "plan": "Red Médica", "policyNumber": "RM-00458712", "coverageStatus": "ACTIVE", "validUntil": "2027-10-20", "valid": true },
    { "patientInsuranceId": 2, "insurer": "Pacífico Seguros", "plan": "Plan Esencial", "policyNumber": null, "coverageStatus": "SUSPENDED", "validUntil": null, "valid": false }
  ]
}
```

Si ninguna cobertura es válida, `valid` es `false` y `message` indica que el paciente puede atenderse como particular («El paciente no tiene una cobertura vigente. Puede atenderse como particular o actualizar su seguro.», o «El paciente no tiene ningún seguro registrado. Puede atenderse como particular o asociar un seguro.» cuando `coverages` está vacío). `patientInsuranceId` es el valor que se envía como `patientInsuranceId` al reservar. Errores: `404 PATIENT_NOT_FOUND`.

### `PUT /front-desk/patients/{id}/insurance`

Mismo cuerpo y respuesta que `PUT /patients/{id}/insurance` (RF-03): `{ "planId": 3, "policyNumber": "RM-00458712" }` asocia o reactiva ese plan y suspende los demás; sin `planId` el paciente queda como particular.

### `GET /front-desk/patients/{id}/appointments?upcoming=true`

Misma respuesta que `GET /patients/{id}/appointments`: con `upcoming=true` (por defecto) solo las citas `BOOKED` futuras; con `upcoming=false` todas, de la más reciente a la más antigua. Errores: `404 PATIENT_NOT_FOUND`.

### `POST /front-desk/appointments`

Cabecera obligatoria `Idempotency-Key: <UUID>` y el mismo cuerpo que `POST /appointments` (`patientId`, `slotId`, `patientInsuranceId` opcional, `visitReason`). `201 Created` (o `200 OK` si la clave ya fue usada) con la misma respuesta de cita de la sección 4. La cita se guarda con `bookingChannel = FRONT_DESK`, el historial y la auditoría (`APPOINTMENT_BOOKED`, detalle «... por admisión») llevan el usuario de ventanilla, y se programan la confirmación y el recordatorio (RF-11, RF-12).

Errores: `400 INVALID_DATA` (sin `Idempotency-Key` o cuerpo inválido) · `404 PATIENT_NOT_FOUND` / `RESOURCE_NOT_FOUND` · `409 SLOT_NOT_AVAILABLE` (cupo ya tomado, RF-07) · `409 DUPLICATE_APPOINTMENT` (el paciente ya tiene una cita `BOOKED` que se cruza con ese horario, RF-08) · `422 COVERAGE_NOT_ACTIVE`.

### `PATCH /front-desk/appointments/{id}/reschedule`

Mismo cuerpo, respuesta y errores que `PATCH /appointments/{id}/reschedule`: `{ "newSlotId": 131 }`. Audita `APPOINTMENT_RESCHEDULED` con el usuario de ventanilla.

### `PATCH /front-desk/appointments/{id}/cancellation`

A diferencia del kiosko, el **motivo es obligatorio**:

```json
{ "reason": "El paciente viajó" }
```

`200 OK` con `status: "CANCELLED"`; el motivo queda en `appointment_history.detail` y en el detalle de la auditoría (`APPOINTMENT_CANCELLED`, «... Motivo: El paciente viajó»).

Errores: `400 INVALID_DATA` sin cuerpo o con `reason` vacío (`fields[0] = { "field": "reason", "message": "Indica el motivo de la anulación." }`; máximo 200 caracteres) · `404 APPOINTMENT_NOT_FOUND` · `422 APPOINTMENT_NOT_MODIFIABLE`.

### Acceso y auditoría de ambos módulos

| Ruta | `DOCTOR` | `FRONT_DESK` | `ADMIN` | Sin token |
|---|---|---|---|---|
| `/doctor/**` | 200 | 403 `FORBIDDEN` | 403 `FORBIDDEN` | 401 `UNAUTHORIZED` |
| `/front-desk/**` | 403 `FORBIDDEN` | 200 | 200 | 401 `UNAUTHORIZED` |

Acciones nuevas en `audit_log.action`: `ATTENDANCE_REGISTERED` (entidad `APPOINTMENT`, usuario = médico, detalle «Cita MCA-7K2Q9: paciente atendido (cupo 12 del 2026-10-20 09:00, Carla Benavides Ortiz). Nota: ...» o «... paciente no se presentó ...»). Las operaciones de ventanilla usan las acciones ya existentes `APPOINTMENT_BOOKED`, `APPOINTMENT_RESCHEDULED` y `APPOINTMENT_CANCELLED` con el usuario de admisión.

---

<!-- Issue #46: administración y reportes -->

## 9. Administración

Todas las rutas de esta sección cuelgan de `/admin` y exigen rol `ADMIN` (`401 UNAUTHORIZED` sin token, `403 FORBIDDEN` con otro rol). Cada alta, edición, generación de agenda y bloqueo escribe una fila en `audit_log` atribuida al administrador (ver 7.5). Los cambios del catálogo se reflejan de inmediato en el kiosko: `GET /specialties` solo lista especialidades activas en sedes que las atienden, y `GET /availability` solo muestra cupos `FREE` de médicos activos, de especialidades activas que la sede sigue atendiendo.

### 9.1 Usuarios (CU-020, RF-15)

Respuesta de todos los endpoints: el mismo objeto de `GET /admin/users` (sección 6). La contraseña nunca se devuelve.

| Método y ruta | Cuerpo | Respuesta |
|---|---|---|
| `GET /admin/users` | - | `200` lista ordenada por usuario |
| `GET /admin/users/{id}` | - | `200` · `404 RESOURCE_NOT_FOUND` |
| `POST /admin/users` | `{ "username", "password", "role", "doctorId"? }` | `201` + `Location` |
| `PUT /admin/users/{id}` | `{ "role", "doctorId"?, "active" }` | `200` |
| `PUT /admin/users/{id}/password` | `{ "password" }` | `204` |

```json
{ "username": "dr.quiroga", "password": "clave.segura.123", "role": "DOCTOR", "doctorId": 21 }
```

Reglas: `username` único sin distinguir mayúsculas (3 a 50 caracteres, letras, números, `.`, `-` y `_`) → `409 USERNAME_TAKEN`; `password` de 8 a 72 caracteres → `400 INVALID_DATA`; el perfil `DOCTOR` exige `doctorId` de un médico existente (`404 RESOURCE_NOT_FOUND`) sin otro usuario (`409 DOCTOR_ALREADY_LINKED`); los demás perfiles no admiten `doctorId` (`400 INVALID_DATA`). El estado se edita con `active` en el `PUT` (no hay endpoints `activate`/`deactivate`); un administrador no puede desactivarse ni quitarse el perfil `ADMIN` → `422 SELF_DEACTIVATION`. Como la autorización lee el rol y el estado de la base en cada petición, un cambio aplica de inmediato al usuario afectado sin esperar a que venza su token. Auditoría: `USER_CREATED`, `USER_UPDATED`, `USER_DEACTIVATED` (cuando `active` pasa a `false`), `USER_PASSWORD_CHANGED`; los detalles nunca incluyen la contraseña.

### 9.2 Sedes y especialidades (CU-021, RF-21)

`GET /admin/locations` lista todas las sedes, incluidas las inactivas: `{ "id", "code", "name", "address", "district", "phone", "active" }`.

`GET /admin/specialties` lista todas las especialidades, activas e inactivas, con las sedes que las atienden:

```json
[
  { "id": 1, "name": "Cardiología", "description": "Evaluación y control del corazón...", "appointmentMinutes": 20, "active": true,
    "locations": [ { "id": 1, "code": "SI", "name": "Sede San Isidro" }, { "id": 2, "code": "LM", "name": "Sede La Molina" } ] }
]
```

`POST /admin/specialties` (`201`) y `PUT /admin/specialties/{id}` (`200`) reciben:

```json
{ "name": "Reumatología", "description": "Enfermedades de las articulaciones.", "appointmentMinutes": 30, "locationIds": [1], "active": true }
```

`active` se ignora en el alta (siempre activa) y, si se omite al editar, se mantiene. Al editar, las sedes que ya no figuran en `locationIds` quedan con la relación inactiva (no se borra, para conservar el historial) y dejan de ofrecer la especialidad en el kiosko. Errores: `409 SPECIALTY_NAME_TAKEN` (nombre repetido sin distinguir mayúsculas) · `404 RESOURCE_NOT_FOUND` (especialidad o sede inexistente) · `400 INVALID_DATA` (`appointmentMinutes` fuera de 5 a 240). Auditoría: `SPECIALTY_CREATED`, `SPECIALTY_UPDATED`.

### 9.3 Médicos (CU-022, RF-22)

`GET /admin/doctors` lista todos los médicos, activos e inactivos, ordenados por apellidos:

```json
[
  { "id": 1, "license": "021400", "specialistRegistry": "10200", "firstNames": "Carla", "lastNames": "Benavides Ortiz",
    "fullName": "Carla Benavides Ortiz", "specialty": { "id": 1, "name": "Cardiología" }, "active": true }
]
```

`POST /admin/doctors` (`201`) y `PUT /admin/doctors/{id}` (`200`) reciben `{ "license", "specialistRegistry"?, "firstNames", "lastNames", "specialtyId", "active"? }` (`active` con las mismas reglas que en especialidades). Errores: `409 DOCTOR_LICENSE_TAKEN` · `404 RESOURCE_NOT_FOUND`. Un médico inactivo conserva sus cupos y citas, pero sus cupos no se muestran en el kiosko ni se le puede generar agenda. Auditoría: `DOCTOR_CREATED`, `DOCTOR_UPDATED`.

### 9.4 Agendas (CU-023, RF-23)

Objeto cupo:

```json
{ "id": 1203, "date": "2026-11-10", "startTime": "08:00", "endTime": "08:20", "room": "Consultorio 201", "status": "FREE",
  "doctor": { "id": 1, "fullName": "Carla Benavides Ortiz" }, "location": { "id": 1, "name": "Sede San Isidro" } }
```

| Método y ruta | Uso |
|---|---|
| `GET /admin/slots?doctorId=1&from=2026-11-10&to=2026-11-14` | Cupos del médico en el rango (máximo 92 días), en cualquier estado, ordenados por fecha y hora |
| `POST /admin/slots/generate` | Genera cupos `FREE` → `201 { "created", "skipped", "slots": [...] }` |
| `PATCH /admin/slots/{id}/block` | `FREE` → `BLOCKED`; `409 SLOT_NOT_FREE` si está reservado o ya bloqueado |
| `PATCH /admin/slots/{id}/unblock` | `BLOCKED` → `FREE`; `409 SLOT_NOT_BLOCKED` si no está bloqueado |

```json
{ "doctorId": 1, "locationId": 1, "room": "Consultorio 201", "from": "2026-11-10", "to": "2026-11-14",
  "startTime": "08:00", "endTime": "13:00", "weekdays": ["MONDAY", "WEDNESDAY", "FRIDAY"], "durationMinutes": 20 }
```

`weekdays` es opcional (por defecto de lunes a sábado) y `durationMinutes` también (por defecto los minutos por cita de la especialidad del médico). Cada día se divide en bloques consecutivos desde `startTime`; el último termina como máximo en `endTime`. Los horarios que coinciden o se cruzan con cupos que el médico ya tiene se omiten y se cuentan en `skipped`, así que repetir la generación nunca duplica cupos (`uk_slot_doctor_schedule`). Validaciones: médico existente y activo (`404`, `422 DOCTOR_INACTIVE`), sede existente que atienda la especialidad del médico (`404`, `422 SPECIALTY_NOT_OFFERED`), `from` no anterior a hoy, `from ≤ to`, rango de hasta 92 días y `startTime < endTime` con espacio para al menos un cupo (`400 INVALID_DATA` con el campo). Un cupo `BLOCKED` no aparece en `GET /availability`. Auditoría: `SLOTS_GENERATED` (entidad `SLOT`, sin `entityId`), `SLOT_BLOCKED`, `SLOT_UNBLOCKED`.

### 9.5 Auditoría (CU-024, RF-20)

`GET /admin/audit?entity=APPOINTMENT&action=APPOINTMENT_BOOKED&userId=2&from=2026-10-01&to=2026-10-31&page=0&size=20`

Todos los filtros son opcionales. `from` y `to` son fechas de la clínica, ambas inclusive (desde las 00:00 de `from` hasta el final del día `to`). `page` empieza en 0 y `size` va de 1 a 100 (por defecto 20); fuera de rango → `400 INVALID_DATA`. El orden es fijo: del registro más reciente al más antiguo.

```json
{
  "content": [
    { "id": 418, "registeredAt": "2026-10-20T09:15:02-05:00",
      "user": { "id": 2, "username": "admision", "fullName": "admision" },
      "action": "APPOINTMENT_BOOKED", "entity": "APPOINTMENT", "entityId": 907,
      "detail": "Cita MCA-7K2Q9 reservada en el cupo 12 (2026-10-21 09:00, Carla Benavides Ortiz) para el paciente 5 por admisión",
      "sourceIp": "127.0.0.1" },
    { "id": 417, "registeredAt": "2026-10-20T09:10:40-05:00", "user": null, "action": "APPOINTMENT_BOOKED", "entity": "APPOINTMENT",
      "entityId": 906, "detail": "... desde el kiosko", "sourceIp": "10.0.0.15" }
  ],
  "page": 0, "size": 20, "totalElements": 2, "totalPages": 1
}
```

`user` es `null` cuando actuó el paciente desde el kiosko. Acciones registradas y su entidad: `APPOINTMENT_BOOKED`, `APPOINTMENT_RESCHEDULED`, `APPOINTMENT_CANCELLED` (`APPOINTMENT`); `USER_LOGGED_IN`, `USER_CREATED`, `USER_UPDATED`, `USER_DEACTIVATED`, `USER_PASSWORD_CHANGED` (`APP_USER`); `SPECIALTY_CREATED`, `SPECIALTY_UPDATED` (`SPECIALTY`); `DOCTOR_CREATED`, `DOCTOR_UPDATED` (`DOCTOR`); `SLOTS_GENERATED`, `SLOT_BLOCKED`, `SLOT_UNBLOCKED` (`SLOT`).

---

## 10. Reportes

Rutas bajo `/admin/reports`, rol `ADMIN`. Todas reciben `from` y `to` (fechas ISO, ambas inclusive): `from ≤ to` y como máximo 366 días, si no `400 INVALID_DATA` en el campo `to`. Las citas se asignan al rango por la **fecha de su cupo**. Las tasas van entre 0 y 1 con cuatro decimales. Las cifras se calculan en memoria sobre las citas y cupos del rango (dos consultas), de modo que los tres reportes y el CSV salen de los mismos datos.

### `GET /admin/reports/appointments?from=2026-10-01&to=2026-10-31&specialtyId?&locationId?` (CU-025, RF-24)

```json
{
  "from": "2026-10-01", "to": "2026-10-31",
  "totals": { "booked": 12, "attended": 180, "cancelled": 21, "noShow": 37, "total": 250 },
  "bySpecialty": [
    { "specialtyId": 1, "specialtyName": "Cardiología", "totals": { "booked": 4, "attended": 60, "cancelled": 7, "noShow": 9, "total": 80 } }
  ],
  "byLocation": [
    { "locationId": 2, "locationName": "Sede La Molina", "totals": { "booked": 5, "attended": 70, "cancelled": 8, "noShow": 17, "total": 100 } },
    { "locationId": 1, "locationName": "Sede San Isidro", "totals": { "booked": 7, "attended": 110, "cancelled": 13, "noShow": 20, "total": 150 } }
  ]
}
```

`specialtyId` y `locationId` son opcionales y limitan el reporte (`404 RESOURCE_NOT_FOUND` si no existen). Los desgloses van ordenados por nombre.

### `GET /admin/reports/no-show-rate?from&to` (CU-026, RF-25)

`rate = noShow / (attended + noShow)`, 0 si no hubo atenciones; las citas reservadas o anuladas no cuentan y una especialidad sin atenciones ni inasistencias no aparece.

```json
{
  "from": "2026-10-01", "to": "2026-10-31",
  "bySpecialty": [ { "specialtyId": 1, "specialtyName": "Cardiología", "attended": 60, "noShow": 9, "rate": 0.1304 } ],
  "total": { "attended": 180, "noShow": 37, "rate": 0.1705 }
}
```

### `GET /admin/reports/occupancy?from&to` (CU-027, RF-26)

Una fila por especialidad y sede: `slotsTotal` son los cupos generados en el rango sin contar los bloqueados (que van en `slotsBlocked`), `slotsBooked` las citas en estado `BOOKED`, `ATTENDED` o `NO_SHOW`, y `rate = slotsBooked / slotsTotal` (0 si no hay cupos).

```json
{
  "from": "2026-10-01", "to": "2026-10-31",
  "rows": [
    { "specialtyId": 1, "specialtyName": "Cardiología", "locationId": 2, "locationName": "Sede La Molina", "slotsTotal": 120, "slotsBlocked": 6, "slotsBooked": 84, "rate": 0.7 }
  ],
  "total": { "slotsTotal": 1800, "slotsBlocked": 40, "slotsBooked": 1250, "rate": 0.6944 }
}
```

### `GET /admin/reports/export?report=appointments|no-show-rate|occupancy&from&to[&format=csv|xlsx][&specialtyId][&locationId]`

Descarga el reporte como CSV (`Content-Type: text/csv;charset=UTF-8`, `Content-Disposition: attachment; filename="reporte-citas_2026-10-01_2026-10-31.csv"`; prefijos `reporte-citas`, `reporte-inasistencia`, `reporte-ocupacion`). El archivo empieza con el BOM UTF-8 (para que Excel reconozca las tildes), separa con coma, termina cada línea con CRLF y entrecomilla solo los valores con comas o comillas. Un `report` desconocido → `400 INVALID_DATA`.

```csv
grupo,nombre,reservadas,atendidas,anuladas,inasistencias,total
Especialidad,Cardiología,4,60,7,9,80
Sede,Sede San Isidro,7,110,13,20,150
Total,Todas,12,180,21,37,250
```

Cabeceras de los otros dos: `especialidad,atendidas,inasistencias,tasa_inasistencia` (última fila `Total`) y `especialidad,sede,cupos,cupos_bloqueados,cupos_ocupados,tasa_ocupacion` (última fila `Total,Todas`).

**Formato.** `format` es opcional: `csv` (por defecto, igual que antes) o `xlsx` (sin distinguir mayúsculas). Con `xlsx` la respuesta es un libro de Excel generado con Apache POI (`Content-Type: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`, mismo nombre de archivo con extensión `.xlsx`): una hoja con las mismas filas y cifras que el CSV, cabecera en negrita y fija, enteros y tasas como números (las tasas con 4 decimales). Todo texto va como celda de texto, nunca como fórmula. Cualquier otro valor de `format` responde `400 INVALID_DATA` en el campo `format`.

`specialtyId` y `locationId` son opcionales, se aplican solo al reporte `appointments` (en ambos formatos) y responden `404 RESOURCE_NOT_FOUND` si la especialidad o la sede no existen.

### Códigos de error de las secciones 9 y 10

| HTTP | `code` | Cuándo |
|---|---|---|
| 409 | `USERNAME_TAKEN` | Nombre de usuario ya registrado |
| 409 | `DOCTOR_ALREADY_LINKED` | El médico ya tiene un usuario |
| 422 | `SELF_DEACTIVATION` | El administrador intenta desactivarse o quitarse el perfil `ADMIN` |
| 409 | `SPECIALTY_NAME_TAKEN` | Nombre de especialidad repetido |
| 409 | `DOCTOR_LICENSE_TAKEN` | Colegiatura repetida |
| 422 | `DOCTOR_INACTIVE` | Generar agenda a un médico inactivo |
| 422 | `SPECIALTY_NOT_OFFERED` | La sede no atiende la especialidad del médico |
| 409 | `SLOT_NOT_FREE` | Bloquear un cupo reservado o ya bloqueado |
| 409 | `SLOT_NOT_BLOCKED` | Desbloquear un cupo que no está bloqueado |
