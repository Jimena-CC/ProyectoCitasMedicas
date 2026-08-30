# Identificación y Análisis de Actores del Sistema (MediCitas Anglo)

## 1. Introducción
El presente documento define los actores del sistema MediCitas Anglo...

## 2. Actores de la Fase 1 / Módulo de Autoatención (API & Paciente)
*Actores que interactúan directamente con los canales digitales de entrada y notificaciones.*

### 2.1. Paciente
* **Tipo:** Persona (Entidad externa / Usuario final)
* **Descripción:** Usuario que solicita, consulta o programa citas a través de la interfaz de autoatención / API web.
* **Interacción Principal:** Consulta de disponibilidad de especialidades, reserva de cupos y recepción de notificaciones de confirmación.
* **Responsabilidad:** Brindar información correcta de su identidad y seguro, y confirmar su asistencia a la cita.

### 2.2. Servicio de Mensajería Externalizado
* **Tipo:** Sistema Externo
* **Descripción:** API externa para el envío de SMS/Email automáticos.
* **Interacción Principal:** Envío de recordatorios programados a los pacientes.
* **Responsabilidad:** Garantizar la entrega de las notificaciones para reducir el ausentismo.

---

## 3. Actores de la Fase 2 / Módulo de Gestión Interna (Aplicación de Escritorio)
*Actores del entorno operativo interno de la clínica (Admisión y Cuerpo Médico).*

### 3.1. Personal de Admisión / Central de Citas
* **Tipo:** Persona (Usuario Interno)
* **Descripción:** Empleado administrativo en ventanilla o central telefónica.
* **Interacción Principal:** Agendamiento presencial/telefónico, validación de aseguradoras, reprogramación y anulación de citas.
* **Responsabilidad:** Mantener la agilidad en la atención y resolver cuellos de botella operativos de la agenda.

### 3.2. Médico / Profesional de Salud
* **Tipo:** Persona (Usuario Interno).
* **Descripción:** Médico especialista de consultorio externo.
* **Interacción Principal:** Consulta de agenda del día y marcado de asistencia/inasistencia efectiva del paciente.
* **Responsabilidad:** Actualizar el estado del cupo en tiempo real al atender al paciente.

### 3.3. Administrador del Sistema
* **Tipo:** Persona (Usuario Interno de TI)[cite: 1]
* **Descripción:** Personal técnico responsable del sistema.
* **Interacción Principal:** Gestión de usuarios/roles, revisión de auditoría (Ley 29733) y generación de reportes.
* **Responsabilidad:** Velar por la seguridad, los permisos y la estabilidad de la plataforma.

---

## 4. Cuadro Resumen de Actores por Componente

| Actor | Tipo | Componente del Sistema | Prioridad de Implementación |
|---|---|---|---|
| **Paciente** | Persona (Externa) | Módulo Web / Autoatención (API) | Fase 1 |
| **Servicio Mensajería** | Sistema Externo | API de Notificaciones | Fase 1 |
| **Personal Admisión** | Persona (Interna) | Cliente de Escritorio Java | Fase 2 |
| **Médico** | Persona (Interna) | Cliente de Escritorio Java | Fase 2 |
| **Administrador** | Persona (Interna) | Módulo de Seguridad / Auditoría | Fase 1 / 2 |