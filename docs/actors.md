# Identificación y Análisis de Actores del Sistema (MediCitas Anglo)

## 1. Introducción
El presente documento define los actores del sistema MediCitas Anglo, el canal por el que interactúan con él y la fase en la que se implementa cada uno. El Paciente usa el **Kiosco de Autoatención** (JavaFX); el personal interno (Admisión, Médico y Administrador) usa el **Backoffice web** (React), que consume la misma API Spring Boot. Los casos de uso de cada actor están en el [cuadro de casos de uso](use-cases.md).

## 2. Actores de la Fase 1 / Módulo de Autoatención (Kiosco y API)
*Actores que interactúan directamente con los canales digitales de entrada y notificaciones.*

### 2.1. Paciente
* **Tipo:** Persona (Entidad externa / Usuario final)
* **Canal:** Kiosco de Autoatención (JavaFX) en las sedes de la clínica.
* **Descripción:** Usuario que solicita, consulta o programa citas a través del Kiosco de Autoatención, que consume la API.
* **Interacción Principal:** Consulta de disponibilidad de especialidades, reserva, reprogramación y anulación de cupos, y recepción de notificaciones de confirmación.
* **Responsabilidad:** Brindar información correcta de su identidad y seguro, y confirmar su asistencia a la cita.
* **Casos de uso:** CU-001 a CU-008.

### 2.2. Servicio de Mensajería Externalizado
* **Tipo:** Sistema Externo
* **Canal:** API externa invocada desde la API MediCitas.
* **Descripción:** API externa para el envío de SMS/Email automáticos.
* **Interacción Principal:** Envío de confirmaciones y recordatorios programados a los pacientes.
* **Responsabilidad:** Garantizar la entrega de las notificaciones para reducir el ausentismo.
* **Casos de uso:** CU-009, CU-010.

---

## 3. Actores de la Fase 2 / Módulo de Gestión Interna (Backoffice web)
*Actores del entorno operativo interno de la clínica (Admisión, Cuerpo Médico y TI). Todos inician sesión en el Backoffice web con usuario, contraseña y rol (CU-011), y cada acción queda registrada en la auditoría (CU-013).*

### 3.1. Personal de Admisión / Central de Citas
* **Tipo:** Persona (Usuario Interno, rol `FRONT_DESK`)
* **Canal:** Backoffice web (React), módulo de ventanilla.
* **Descripción:** Empleado administrativo en ventanilla o central telefónica.
* **Interacción Principal:** Búsqueda y registro de pacientes, validación de aseguradoras, agendamiento presencial/telefónico, reprogramación y anulación de citas a nombre del paciente (canal `FRONT_DESK`).
* **Responsabilidad:** Mantener la agilidad en la atención y resolver cuellos de botella operativos de la agenda, aplicando las mismas reglas de cupo y duplicidad que el Kiosco.
* **Casos de uso:** CU-011, CU-012, CU-016 a CU-020.

### 3.2. Médico / Profesional de Salud
* **Tipo:** Persona (Usuario Interno, rol `DOCTOR`)
* **Canal:** Backoffice web (React), módulo de agenda del médico.
* **Descripción:** Médico especialista de consultorio externo; su usuario está vinculado a su registro de médico (`app_user.doctor_id`).
* **Interacción Principal:** Consulta de su agenda del día y marcado de asistencia (`ATTENDED`) o inasistencia (`NO_SHOW`) efectiva del paciente.
* **Responsabilidad:** Actualizar el estado de la cita en tiempo real al atender al paciente, dejando el evento en el historial de la cita.
* **Casos de uso:** CU-011, CU-012, CU-014, CU-015.

### 3.3. Administrador del Sistema
* **Tipo:** Persona (Usuario Interno de TI, rol `ADMIN`)
* **Canal:** Backoffice web (React), módulo de administración y reportes.
* **Descripción:** Personal técnico responsable del sistema.
* **Interacción Principal:** Gestión de usuarios y roles, médicos, especialidades y agendas/horarios; revisión de auditoría (Ley 29733) y generación y exportación de reportes.
* **Responsabilidad:** Velar por la seguridad, los permisos y la estabilidad de la plataforma, y mantener actualizado el catálogo que alimenta al Kiosco.
* **Casos de uso:** CU-011, CU-012, CU-021 a CU-027.

---

## 4. Cuadro Resumen de Actores por Componente

| Actor | Tipo | Componente del Sistema | Rol en `app_user` | Prioridad de Implementación |
|---|---|---|---|---|
| **Paciente** | Persona (Externa) | Kiosco de Autoatención (JavaFX) + API | No aplica | Fase 1 |
| **Servicio Mensajería** | Sistema Externo | API de Notificaciones | No aplica | Fase 1 |
| **Personal Admisión** | Persona (Interna) | Backoffice web (React) - módulo de ventanilla | `FRONT_DESK` | Fase 2 |
| **Médico** | Persona (Interna) | Backoffice web (React) - módulo de agenda | `DOCTOR` | Fase 2 |
| **Administrador** | Persona (Interna) | Backoffice web (React) - módulo de administración, auditoría y reportes | `ADMIN` | Fase 2 |

El Kiosco queda reservado al Paciente; el personal interno no usa el Kiosco ni un cliente de escritorio: toda la gestión interna se realiza desde el Backoffice web.
