# Cuadro de Casos de Uso - MediCitas Anglo

Cuadro organizativo de los casos de uso del sistema. Cada caso de uso tiene un identificador `CU-xxx` que aparece también en el [diagrama de casos de uso](diagrams/02-use-case-diagram.puml) y en las especificaciones de requerimientos ([Fase 1](requirements-specification.md) y [Backoffice](backoffice-requirements-specification.md)).

Canales por actor:

- **Paciente:** Kiosco de Autoatención (JavaFX).
- **Personal de Admisión, Médico y Administrador:** Backoffice web (React), que consume la misma API Spring Boot.
- **Servicio de Mensajería:** sistema externo que recibe las solicitudes de envío de la API.

## 1. Fase 1 - Autoatención (Kiosco)

| Código | Nombre | Actor(es) | Descripción breve | Requerimiento(s) relacionado(s) | Fase |
|---|---|---|---|---|---|
| **CU-001** | Registrar paciente | Paciente | Crea el perfil de un paciente nuevo validando que el documento no exista (`POST /patients`). | RF-01 | 1 |
| **CU-002** | Identificar paciente | Paciente | Recupera el perfil de un paciente registrado a partir de su tipo y número de documento (`GET /patients`). | RF-02 | 1 |
| **CU-003** | Asociar aseguradora | Paciente | Registra la aseguradora y el plan del paciente, o la atención particular (`PUT /patients/{id}/insurance`). | RF-03 | 1 |
| **CU-004** | Consultar especialidades | Paciente | Lista las sedes y las especialidades activas por sede (`GET /locations`, `GET /specialties`). | RF-04 | 1 |
| **CU-005** | Consultar disponibilidad | Paciente | Muestra los cupos libres de una especialidad y sede en una franja de hasta 14 días (`GET /availability`). | RF-05, RNF-04 | 1 |
| **CU-006** | Reservar cita | Paciente | Toma un cupo libre y crea la cita BOOKED con código único y clave de idempotencia (`POST /appointments`). Incluye CU-005, CU-009 y CU-010. | RF-06, RF-07, RF-08, RNF-07 | 1 |
| **CU-007** | Reprogramar cita | Paciente | Traslada una cita BOOKED a otro cupo de la misma especialidad y libera el original (`PATCH /appointments/{id}/reschedule`). Incluye CU-005, CU-009 y CU-010. | RF-07, RF-08, RF-09 | 1 |
| **CU-008** | Anular cita | Paciente | Cancela una cita BOOKED futura y libera su cupo (`PATCH /appointments/{id}/cancellation`). Incluye CU-009. | RF-10 | 1 |

## 2. Notificaciones

| Código | Nombre | Actor(es) | Descripción breve | Requerimiento(s) relacionado(s) | Fase |
|---|---|---|---|---|---|
| **CU-009** | Enviar confirmación | Servicio de Mensajería (sistema) | Envía por correo la confirmación de la reserva, de la reprogramación o de la anulación inmediatamente después de confirmar la transacción, registrando el estado del envío y reintentando ante fallos. | RF-11, RF-13, RF-14 | 1 |
| **CU-010** | Enviar recordatorio | Servicio de Mensajería (sistema) | Programa y envía por SMS un recordatorio 24 horas antes de la cita; se cancela si la cita deja de estar BOOKED. | RF-12, RF-13, RF-14 | 1 |

## 3. Fase 2 - Backoffice web: seguridad y auditoría

| Código | Nombre | Actor(es) | Descripción breve | Requerimiento(s) relacionado(s) | Fase |
|---|---|---|---|---|---|
| **CU-011** | Iniciar sesión | Personal de Admisión, Médico, Administrador | Autentica al usuario con usuario y contraseña (hash BCrypt) y entrega un token JWT con su rol (`POST /auth/login`). Todo caso de uso del Backoffice lo incluye. | RF-15, RF-17, RF-18, RNF-09, RNF-10, RNF-13 | 2 |
| **CU-012** | Cerrar sesión | Personal de Admisión, Médico, Administrador | Invalida el token vigente y registra la salida (`POST /auth/logout`). | RF-16, RNF-13 | 2 |
| **CU-013** | Registrar auditoría | Sistema (incluido por los casos de uso internos) | Escribe en `audit_log` quién hizo qué, sobre qué entidad y cuándo, para citas, asistencia y cambios administrativos. | RF-19, RF-20, RNF-12, RNF-13 | 2 |

## 4. Fase 2 - Backoffice web: Médico

| Código | Nombre | Actor(es) | Descripción breve | Requerimiento(s) relacionado(s) | Fase |
|---|---|---|---|---|---|
| **CU-014** | Consultar agenda del día | Médico | Muestra las citas del día del médico autenticado, ordenadas por hora, con paciente, sede, consultorio y estado (`GET /doctor/agenda`). | RF-21, RNF-14 | 2 |
| **CU-015** | Registrar asistencia o inasistencia | Médico | Marca una cita BOOKED del día como ATTENDED o NO_SHOW, dejando el evento ATTENDANCE en `appointment_history` y la auditoría (`PATCH /doctor/appointments/{id}/attendance`). Incluye CU-014 y CU-013. | RF-22 | 2 |

## 5. Fase 2 - Backoffice web: Admisión

| Código | Nombre | Actor(es) | Descripción breve | Requerimiento(s) relacionado(s) | Fase |
|---|---|---|---|---|---|
| **CU-016** | Buscar paciente | Personal de Admisión | Busca al paciente por documento y, si no existe, permite registrarlo con las mismas validaciones de CU-001 (`GET /front-desk/patients`, `POST /front-desk/patients`). | RF-23 | 2 |
| **CU-017** | Validar aseguradora | Personal de Admisión | Verifica que la cobertura del paciente esté ACTIVE y vigente antes de agendar; extiende a CU-018 cuando el paciente usa seguro (`GET /front-desk/patients/{id}/insurance`). | RF-24 | 2 |
| **CU-018** | Agendar cita en ventanilla | Personal de Admisión | Reserva una cita a nombre del paciente con canal FRONT_DESK y las mismas reglas de cupo y duplicidad del Kiosco (`POST /front-desk/appointments`). Incluye CU-016, CU-013, CU-009 y CU-010. | RF-25, RF-07, RF-08 | 2 |
| **CU-019** | Reprogramar cita en ventanilla | Personal de Admisión | Traslada una cita BOOKED a otro cupo a pedido del paciente (`PATCH /front-desk/appointments/{id}/reschedule`). Incluye CU-016 y CU-013. | RF-26, RF-09 | 2 |
| **CU-020** | Anular cita en ventanilla | Personal de Admisión | Cancela una cita BOOKED a pedido del paciente registrando el motivo (`PATCH /front-desk/appointments/{id}/cancellation`). Incluye CU-016 y CU-013. | RF-27, RF-10 | 2 |

## 6. Fase 2 - Backoffice web: Administración

| Código | Nombre | Actor(es) | Descripción breve | Requerimiento(s) relacionado(s) | Fase |
|---|---|---|---|---|---|
| **CU-021** | Gestionar usuarios | Administrador | Crea, edita y desactiva usuarios del Backoffice asignándoles rol ADMIN, FRONT_DESK o DOCTOR (`/admin/users`). Incluye CU-013. | RF-28, RNF-12 | 2 |
| **CU-022** | Gestionar médicos | Administrador | Registra y actualiza médicos, su especialidad, licencia y estado activo (`/admin/doctors`). Incluye CU-013. | RF-29 | 2 |
| **CU-023** | Gestionar especialidades | Administrador | Registra y actualiza especialidades, su duración de cita y las sedes donde se ofrecen (`/admin/specialties`). Incluye CU-013. | RF-30 | 2 |
| **CU-024** | Gestionar agendas y horarios | Administrador | Genera y bloquea cupos (`slot`) por médico, especialidad, consultorio y sede en un rango de fechas (`/admin/slots`). Incluye CU-013. | RF-31 | 2 |
| **CU-025** | Revisar auditoría | Administrador | Consulta y filtra el registro de auditoría por usuario, acción, entidad y fecha (`GET /admin/audit`). | RF-32, RNF-13 | 2 |
| **CU-026** | Generar reportes | Administrador | Obtiene citas atendidas, canceladas y no asistidas, tasa de inasistencia y ocupación por especialidad en un rango de fechas (`/admin/reports/...`). | RF-33, RF-34, RF-35, RNF-15 | 2 |
| **CU-027** | Exportar reportes | Administrador | Descarga cualquier reporte en formato CSV; extiende a CU-026 (`GET /admin/reports/export`). | RF-36 | 2 |

## 7. Trazabilidad resumida

| Actor | Casos de uso |
|---|---|
| Paciente | CU-001 a CU-008 |
| Servicio de Mensajería | CU-009, CU-010 |
| Personal de Admisión | CU-011, CU-012, CU-016 a CU-020 |
| Médico | CU-011, CU-012, CU-014, CU-015 |
| Administrador | CU-011, CU-012, CU-021 a CU-027 |
| Sistema (incluido) | CU-013 |
