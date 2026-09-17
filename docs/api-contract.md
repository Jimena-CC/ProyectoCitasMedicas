# Contrato de la API - Kiosko de Autoatención

Contrato REST entre el **Kiosko (JavaFX)** y la **API MediCitas (Spring Boot)** para las 5 pantallas funcionales de la Fase 1. El modelo de datos que lo respalda es el [Entity Relationship Diagram](diagrams/03-entity-relationship-diagram.puml).

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
| 404 | `PATIENT_NOT_FOUND`, `APPOINTMENT_NOT_FOUND`, `RESOURCE_NOT_FOUND` | Id o documento inexistente |
| 409 | `DUPLICATE_DOCUMENT` | RF-01 |
| 409 | `SLOT_NOT_AVAILABLE` | RF-07 |
| 409 | `DUPLICATE_APPOINTMENT` | RF-08 |
| 422 | `COVERAGE_NOT_ACTIVE`, `APPOINTMENT_NOT_MODIFIABLE` | Regla de negocio incumplida |
| 500 | `INTERNAL_ERROR` | Mensaje genérico; el detalle solo va al log |
