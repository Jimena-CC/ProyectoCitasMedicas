# Documentación del Proyecto - Citas Médicas

Este directorio contiene la documentación técnica y de diseño de la solución.

Los nombres de archivos, carpetas e identificadores del código están en inglés. El contenido explicativo y los textos que ve el paciente se mantienen en español.

## Contenido

| Archivo | Contenido |
| :--- | :--- |
| `requirements-specification.md` | Requerimientos funcionales (RF) y no funcionales (RNF). |
| `actors.md` | Actores del sistema por fase. |
| `technologies.md` | Stack tecnológico con la justificación de cada elección. |
| `technical-solution-proposals.md` | Propuestas de solución evaluadas y la elegida. |
| `clinic-profile.md` | Perfil de la Clínica Anglo Americana y sus especialidades. |
| `api-contract.md` | Contrato REST entre el Kiosko (JavaFX) y la API. |
| `screens.md` | Capturas de las pantallas ya construidas en la aplicación de escritorio. |
| `/database` | `initial_schema.sql`: script DDL de PostgreSQL alineado al diagrama ER. |
| `/prototype` | Prototipo visual navegable del flujo de reserva, con capturas de sus 8 pantallas. |
| `/screens` | `img/`: capturas PNG de la aplicación de escritorio, generadas desde la propia app. |

## Diagramas (`/diagrams`)

Los `.puml` generan sus PNG y SVG en `diagrams/img/` automáticamente al abrir un PR hacia `develop`.

| Archivo | Contenido |
| :--- | :--- |
| `01-process-current.puml` | Proceso actual (AS-IS) de reserva en ventanilla o central, con los problemas identificados. |
| `01-process-proposed.puml` | Proceso propuesto (TO-BE) de reserva en el Kiosko, con reintentos ante cupo tomado o cita duplicada. |
| `02-use-case-diagram.puml` | Casos de uso por fase. |
| `03-entity-relationship-diagram.puml` | Modelo de datos en PostgreSQL: catálogo, pacientes y seguros, agenda y citas, notificaciones, seguridad. |
| `04-class-diagram.puml` | Diagrama de clases del dominio. |
| `05-architecture-diagram.puml` | Arquitectura de componentes. |
| `06-process-reschedule.puml` | Proceso TO-BE para reprogramar una cita, con reintento ante cupo tomado. |
| `06-process-cancellation.puml` | Proceso TO-BE para anular una cita y liberar su cupo. |
| `07-process-notifications.puml` | Proceso TO-BE de envío de confirmaciones y recordatorios con reintentos. |
