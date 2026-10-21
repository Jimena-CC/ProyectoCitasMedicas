# Documentación del Proyecto - Citas Médicas

Este directorio contiene la documentación técnica y de diseño de la solución.

Los nombres de archivos, carpetas e identificadores del código están en inglés. El contenido explicativo y los textos que ve el paciente se mantienen en español.

## Contenido

| Archivo | Contenido |
| :--- | :--- |
| `requirements-specification.md` | Requerimientos funcionales (RF-01 a RF-14) y no funcionales (RNF-01 a RNF-08) de la Fase 1 (API y Kiosco). |
| `backoffice-requirements-specification.md` | Requerimientos funcionales (RF-15 a RF-36) y no funcionales (RNF-09 a RNF-19) del Backoffice web (Admisión, Médico y Administrador). |
| `use-cases.md` | Cuadro organizativo de casos de uso (CU-001 a CU-027) con actores, descripción, requerimientos relacionados y fase. |
| `actors.md` | Actores del sistema por fase y canal (Kiosco para el Paciente, Backoffice web para el personal interno). |
| `presentation-errata.md` | Correcciones pendientes de aplicar en la presentación externa del equipo (numeración de figuras, títulos y diagramas desactualizados). |
| `technologies.md` | Stack tecnológico con la justificación de cada elección. |
| `technical-solution-proposals.md` | Propuestas de solución evaluadas y la elegida. |
| `clinic-profile.md` | Perfil de la Clínica Anglo Americana y sus especialidades. |
| `api-contract.md` | Contrato REST entre el Kiosko (JavaFX) y la API. |
| `screens.md` | Capturas de las pantallas ya construidas en la aplicación de escritorio. |
| `../api/src/main/resources/db/migration/V1__init_schema.sql` | Script DDL de PostgreSQL (Flyway) alineado al diagrama ER; es la fuente de verdad del modelo de datos. |
| `/prototype` | Prototipo visual navegable del flujo de reserva, con capturas de sus 8 pantallas. |
| `/screens` | `img/`: capturas PNG de la aplicación de escritorio, generadas desde la propia app. |

## Diagramas (`/diagrams`)

Los `.puml` generan sus PNG y SVG en `diagrams/img/` automáticamente al abrir un PR hacia `develop`.

| Archivo | Contenido |
| :--- | :--- |
| `01-process-current.puml` | Proceso actual (AS-IS) de reserva en ventanilla o central, con los problemas identificados. |
| `01-process-proposed.puml` | Proceso propuesto (TO-BE) de reserva en el Kiosko, con reintentos ante cupo tomado o cita duplicada. |
| `02-use-case-diagram.puml` | Casos de uso por fase con identificadores CU-001 a CU-027, incluidos los del Backoffice web (Admisión, Médico y Administrador). |
| `03-entity-relationship-diagram.puml` | Modelo de datos en PostgreSQL, columna por columna con `V1__init_schema.sql`: catálogo, pacientes y seguros, agenda y citas, notificaciones, seguridad y auditoría. |
| `04-class-diagram.puml` | Diagrama de clases: entidades, servicios y controladores reales de la API (Fase 1) y las clases nuevas del Backoffice (Fase 2). |
| `05-architecture-diagram.puml` | Arquitectura de componentes. |
| `06-process-reschedule.puml` | Proceso TO-BE (06a) para reprogramar una cita desde el Kiosco, con reintento ante cupo tomado. |
| `06-process-cancellation.puml` | Proceso TO-BE (06b) para anular una cita desde el Kiosco y liberar su cupo. |
| `07-process-notifications.puml` | Proceso TO-BE de envío de confirmaciones y recordatorios (envío inmediato tras la transacción y tarea programada) con reintentos. |
| `08-conceptual-model.puml` | Modelo conceptual de datos. |
| `09-logical-model.puml` | Modelo lógico de datos. |
| `10-physical-model.puml` | Modelo físico de datos (PostgreSQL 15). |
| `11-process-doctor-attendance.puml` | Proceso TO-BE del Médico en el Backoffice web: inicio de sesión, agenda del día, registro de asistencia o inasistencia, historial y auditoría. |
| `12-process-front-desk-booking.puml` | Proceso TO-BE de agendamiento en ventanilla por el Personal de Admisión: búsqueda de paciente, validación de aseguradora, reserva con canal FRONT_DESK y auditoría. |
| `13-kiosk-api-layer.puml` | Capa de conexión a la API del Kiosko: paquetes `app`, `screens`, `ui`, `api` y `dev`, y por qué `ApiClient` equivale a la "clase de conexión a base de datos". |
