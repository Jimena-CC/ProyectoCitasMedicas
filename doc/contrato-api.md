# Contrato de la API - Kiosko de Autoatención

Contrato REST entre el **Kiosko (JavaFX)** y la **API MediCitas (Spring Boot)** para las 5 pantallas funcionales de la Fase 1. El modelo de datos que lo respalda es el [Diagrama Entidad-Relación](diagramas/03-Diagrama-Entidad-Relacion.puml).

- **Base URL:** `http://localhost:8080/api/v1`
- **Formato:** JSON en UTF-8. Fechas `yyyy-MM-dd`, horas `HH:mm`, instantes ISO-8601 con zona `-05:00`.
- **Documentación viva:** `/swagger-ui.html` (springdoc-openapi).

## Pantallas y endpoints

| # | Pantalla del kiosko | Endpoints | Requerimientos |
|---|---|---|---|
| 1 | Identificación | `GET /pacientes` | RF-02 |
| 2 | Datos y seguro | `POST /pacientes`, `GET /aseguradoras`, `PUT /pacientes/{id}/seguro` | RF-01, RF-03 |
| 3 | Especialidad y sede | `GET /sedes`, `GET /especialidades` | RF-04 |
| 4 | Fecha y hora | `GET /disponibilidad` | RF-05 |
| 5 | Confirmación y Mis citas | `POST /citas`, `GET /pacientes/{id}/citas`, `PATCH /citas/{id}/reprogramacion`, `PATCH /citas/{id}/anulacion` | RF-06 a RF-11 |

---

## 1. Pacientes

### `GET /pacientes?tipoDocumento=DNI&numeroDocumento=45871236`

`200 OK`

```json
{
  "id": 1,
  "tipoDocumento": "DNI",
  "numeroDocumento": "45871236",
  "nombres": "Lucía",
  "apellidos": "Paredes Quispe",
  "fechaNacimiento": "1991-04-18",
  "telefono": "987654321",
  "correo": "lucia.paredes@correo.pe",
  "seguro": {
    "idPacienteSeguro": 3,
    "aseguradora": "Rímac Seguros",
    "plan": "Red Médica",
    "numeroPoliza": "RM-00458712",
    "estadoCobertura": "VIGENTE"
  }
}
```

`seguro` es `null` para atención particular. `404 PACIENTE_NO_ENCONTRADO` si no existe.

### `POST /pacientes`

```json
{
  "tipoDocumento": "DNI",
  "numeroDocumento": "72015893",
  "nombres": "Diego",
  "apellidos": "Salazar Rojas",
  "fechaNacimiento": "1988-11-02",
  "sexo": "M",
  "telefono": "956112233",
  "correo": "diego.salazar@correo.pe",
  "aceptaNotificaciones": true
}
```

`201 Created` con el mismo cuerpo que `GET /pacientes`. `409 DOCUMENTO_DUPLICADO` si el documento ya existe.

Validaciones: `DNI` exactamente 8 dígitos · `CE` 9 a 12 caracteres · `PAS` 6 a 12 · `telefono` 9 dígitos empezando en 9 · `correo` válido · `fechaNacimiento` en el pasado.

### `PUT /pacientes/{id}/seguro`

```json
{ "idPlan": 2, "numeroPoliza": "RM-00458712" }
```

`200 OK` con el paciente actualizado. Enviar `{ "idPlan": null }` registra atención particular.

---

## 2. Catálogos

### `GET /aseguradoras`

```json
[
  {
    "id": 1,
    "nombre": "Rímac Seguros",
    "planes": [
      { "id": 1, "nombre": "Red Preferente", "copagoConsulta": 45.00 },
      { "id": 2, "nombre": "Red Médica", "copagoConsulta": 60.00 }
    ]
  }
]
```

### `GET /sedes`

```json
[
  { "id": 1, "codigo": "SI", "nombre": "Sede San Isidro", "direccion": "Av. Alfredo Salazar 350", "distrito": "San Isidro" },
  { "id": 2, "codigo": "LM", "nombre": "Sede La Molina", "direccion": "Av. La Fontana 362", "distrito": "La Molina" }
]
```

### `GET /especialidades?idSede=1`

```json
[
  { "id": 4, "nombre": "Cardiología", "descripcion": "Evaluación y control del corazón y la presión arterial.", "duracionCitaMin": 20 }
]
```

---

## 3. Disponibilidad

### `GET /disponibilidad?idEspecialidad=4&idSede=1&desde=2026-09-14&hasta=2026-09-27`

El rango máximo es de 14 días. Devuelve solo cupos `LIBRE` a partir de la hora actual.

```json
{
  "dias": [
    {
      "fecha": "2026-09-14",
      "cuposLibres": 9,
      "cupos": [
        {
          "idCupo": 118,
          "horaInicio": "07:15",
          "horaFin": "07:35",
          "consultorio": "Consultorio 304",
          "medico": { "id": 7, "nombreCompleto": "Dra. Carla Benavides Ortiz", "cmp": "045712" }
        }
      ]
    }
  ]
}
```

Los días sin cupos se incluyen con `cuposLibres: 0` para pintar la franja de fechas completa.

---

## 4. Citas

### `POST /citas`

Cabecera obligatoria `Idempotency-Key: <UUID>`: el kiosko genera una por intento de reserva y la reutiliza si reintenta tras un corte de red (RNF-07).

```json
{
  "idPaciente": 1,
  "idCupo": 118,
  "idPacienteSeguro": 3,
  "motivoConsulta": "Control de presión arterial"
}
```

`201 Created` (o `200 OK` si la clave ya fue usada, con la misma cita):

```json
{
  "id": 52,
  "codigo": "MCA-7K2Q9",
  "estado": "RESERVADA",
  "fecha": "2026-09-14",
  "horaInicio": "07:15",
  "horaFin": "07:35",
  "especialidad": "Cardiología",
  "sede": "Sede San Isidro",
  "consultorio": "Consultorio 304",
  "medico": "Dra. Carla Benavides Ortiz",
  "cobertura": "Rímac Seguros · Red Médica",
  "motivoConsulta": "Control de presión arterial",
  "vecesReprogramada": 0,
  "fechaReserva": "2026-09-13T21:42:10-05:00"
}
```

Errores: `409 CUPO_NO_DISPONIBLE` (RF-07) · `409 CITA_DUPLICADA` (RF-08) · `422 COBERTURA_NO_VIGENTE`.

### `GET /pacientes/{id}/citas?vigentes=true`

Lista de `CitaResponse` en estado `RESERVADA` con fecha futura, ordenada por fecha y hora.

### `PATCH /citas/{id}/reprogramacion`

```json
{ "idCupoNuevo": 131 }
```

`200 OK` con la cita actualizada. Errores: `409 CUPO_NO_DISPONIBLE`, `409 CITA_DUPLICADA`, `422 CITA_NO_MODIFICABLE`.

### `PATCH /citas/{id}/anulacion`

```json
{ "motivo": "Viaje fuera de Lima" }
```

`200 OK` con `estado: "ANULADA"`. Error: `422 CITA_NO_MODIFICABLE`.

### `GET /citas/{id}/notificaciones`

```json
[
  { "tipo": "CONFIRMACION", "canal": "EMAIL", "estado": "ENVIADA", "programadaPara": "2026-09-13T21:42:10-05:00", "enviadaEn": "2026-09-13T21:42:12-05:00", "intentos": 1 },
  { "tipo": "RECORDATORIO", "canal": "SMS", "estado": "PENDIENTE", "programadaPara": "2026-09-13T07:15:00-05:00", "enviadaEn": null, "intentos": 0 }
]
```

---

## 5. Formato de error

Todas las respuestas de error usan la misma forma. `mensaje` está pensado para mostrarse tal cual al paciente (RNF-06): en español, sin detalles técnicos.

```json
{
  "codigo": "CUPO_NO_DISPONIBLE",
  "mensaje": "Ese horario acaba de ser reservado por otra persona. Elige otro horario disponible.",
  "campos": [],
  "timestamp": "2026-09-13T21:42:10-05:00"
}
```

| HTTP | `codigo` | Cuándo |
|---|---|---|
| 400 | `DATOS_INVALIDOS` | Falla Bean Validation; `campos` lista `{ "campo", "mensaje" }` |
| 404 | `PACIENTE_NO_ENCONTRADO`, `CITA_NO_ENCONTRADA`, `RECURSO_NO_ENCONTRADO` | Id o documento inexistente |
| 409 | `DOCUMENTO_DUPLICADO` | RF-01 |
| 409 | `CUPO_NO_DISPONIBLE` | RF-07 |
| 409 | `CITA_DUPLICADA` | RF-08 |
| 422 | `COBERTURA_NO_VIGENTE`, `CITA_NO_MODIFICABLE` | Regla de negocio incumplida |
| 500 | `ERROR_INTERNO` | Mensaje genérico; el detalle solo va al log |
