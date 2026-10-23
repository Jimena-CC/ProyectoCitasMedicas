# Fe de erratas de la presentación del proyecto

La presentación del equipo (documento externo, no versionado en este repositorio) contiene inconsistencias detectadas al revisar la documentación del Backoffice (issue #48). Este archivo lista las correcciones que deben aplicarse en ese documento.

**Importante:** las correcciones a los diagramas ya están aplicadas en los `.puml` de `docs/diagrams/`. El documento externo debe **regenerar todas sus figuras** desde `docs/diagrams/img/` (PNG o SVG que produce el workflow `documents-img.yml` al abrir un PR hacia `develop`) en lugar de conservar las imágenes antiguas.

## 1. Numeración de figuras

| # | Corrección |
|---|---|
| 1.1 | **Figura 15 duplicada:** existen dos figuras con el número 15. Renumerar la segunda como Figura 16 y desplazar en una unidad todas las figuras siguientes hasta la siguiente duplicidad. |
| 1.2 | **Figura 23 duplicada:** existen dos figuras con el número 23. Tras aplicar 1.1 (que ya las habrá desplazado a 24), renumerar la segunda como 25 y desplazar las posteriores. Revisar que todas las referencias en el texto ("ver Figura N") apunten al número nuevo. |
| 1.3 | Mantener un único índice de figuras generado automáticamente para evitar nuevas duplicidades. |
| 1.4 | Usar el número del archivo `.puml` como prefijo del título de cada figura de diagrama (por ejemplo "Figura N - 03 Diagrama Entidad-Relación") para facilitar la trazabilidad con el repositorio. |

## 2. Títulos de secciones

| # | Corrección |
|---|---|
| 2.1 | **Sección 9:** el título dice "proyecto control Empleado", que corresponde a otro proyecto. Debe decir **"Diagrama de clases - MediCitas Anglo"**, ya que la sección presenta el diagrama de clases del sistema de citas médicas. |
| 2.2 | Revisar que ningún otro título o pie de figura mencione "control de empleados", "Empleado" o nombres ajenos a MediCitas Anglo. |

## 3. Diagramas desactualizados respecto al repositorio

| # | Figura del documento | Corrección |
|---|---|---|
| 3.1 | Diagrama de casos de uso | Reemplazar por `02-use-case-diagram`: ahora cada caso de uso lleva identificador `CU-001` a `CU-027`, el Administrador está conectado a todos sus casos de uso (usuarios, médicos, especialidades, agendas/horarios, auditoría, reportes y exportación) y se incluyen los casos de uso nuevos del Backoffice (iniciar y cerrar sesión, agenda del día, asistencia/inasistencia, buscar paciente, validar aseguradora, agendar/reprogramar/anular en ventanilla) con sus relaciones `<<include>>` y `<<extend>>`. |
| 3.2 | Diagrama entidad-relación | Reemplazar por `03-entity-relationship-diagram`: coincide columna por columna con `V1__init_schema.sql` e incluye las restricciones UK/CK/IX y las tablas `app_user`, `audit_log` y `appointment_history`. Títulos y notas ahora en español. |
| 3.3 | Diagrama de clases | Reemplazar por `04-class-diagram`: usa los nombres reales del código (`AppointmentService.book/listByPatient/reschedule/cancel`, `PatientService.findByDocument/register/updateInsurance`, `AvailabilityService.query`, `NotificationService.scheduleForBooking/scheduleForReschedule/scheduleForCancellation/send`, `AppointmentHistory.record`, etc.) y agrega las clases del Backoffice (`AppUser`, `UserRole`, `AuditLog`, `AuthService`, `AuditService`, `DoctorAgendaService`, `FrontDeskService`, `AdminCatalogService`, `ReportService` y sus controladores). |
| 3.4 | Procesos de reprogramación y anulación | Ambos tenían el mismo número "06" en el título. Ahora son **06a - Reprogramar** y **06b - Anular**; además incorporan la validación "¿La cita está BOOKED y aún no inicia?" como decisión con sí/no y el paso de envío al proveedor. |
| 3.5 | Proceso de notificaciones (07) | Ahora muestra los dos disparadores reales (envío inmediato tras confirmar la transacción y tarea programada cada 5 minutos), la excepción de las notificaciones de anulación y los parámetros `max-attempts = 3` y `retry-minutes = 10`. |
| 3.6 | Proceso de reserva en el Kiosco (01 TO-BE) | Se completó la rama "No" de "¿Documento ya existe?", se agregó la verificación de cupo futuro y cobertura vigente, y se precisó el canal de cada notificación (confirmación por correo, recordatorio por SMS 24 h antes). |
| 3.7 | Modelos conceptual, lógico y físico | Sus títulos decían "05", "06" y "07" (duplicando a la arquitectura y a los procesos). Ahora dicen **08**, **09** y **10**, igual que sus archivos. En el lógico y el físico `patient.birth_date` y `appointment.visit_reason` pasan a obligatorios y el lógico incluye `slot.version`, como en el esquema SQL. |
| 3.8 | Procesos nuevos | Agregar dos figuras: **11 - Atención del Médico** (agenda del día → asistencia/inasistencia → historial y auditoría) y **12 - Agendamiento en ventanilla** por Admisión. |
| 3.9 | Capa de conexión del Kiosco (nueva) | Agregar la figura **13 - Capa de conexión a la API del Kiosko** al apartado de la presentación sobre la capa de conexión (issue #43): muestra que el kiosco solo habla con la API por HTTP/JSON mediante `ApiClient` y nunca accede directamente a PostgreSQL. |

## 4. Textos y tablas

| # | Corrección |
|---|---|
| 4.1 | Código **RNF-8** → **RNF-08** en la tabla de requerimientos no funcionales (ya corregido en `requirements-specification.md`). |
| 4.2 | Tabla de actores: Personal de Admisión y Médico ya no usan un "Cliente de Escritorio Java"; usan el **Backoffice web** (React), igual que el Administrador. El Kiosco queda solo para el Paciente (ya corregido en `actors.md`). |
| 4.3 | Incorporar el cuadro de casos de uso (`use-cases.md`) y la especificación de requerimientos del Backoffice (`backoffice-requirements-specification.md`, RF-15 a RF-36 y RNF-09 a RNF-19). |
| 4.4 | Donde el documento diga que el esquema SQL está en `docs/database/initial_schema.sql`, indicar la ruta real: `api/src/main/resources/db/migration/V1__init_schema.sql`. |
