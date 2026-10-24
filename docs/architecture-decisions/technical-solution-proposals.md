# Propuestas de Solución Técnica

Para el sistema de gestión de citas médicas para la **Clínica Anglo Americana**, la opción ideal integra:

- El alcance de las dos sedes: **San Isidro** y **La Molina**.
- Un volumen de más de **250 mil pacientes anuales**.
- El stack técnico seleccionado:
  - **Java 21**
  - **Spring Boot**
  - **PostgreSQL**
  - **Docker**
  - **JavaFX**
  - **AtlantaFX**

## Propuestas de Solución

### Propuesta A — Cliente Desktop + API REST Modular

> **Elegida por el equipo**

#### Descripción

Solución híbrida **monousuario/multi-estación** con interfaz rica en **JavaFX** (AtlantaFX, ControlsFX) que consume un backend centralizado con **Spring Boot** y **PostgreSQL**.

Permite despliegues locales en puntos físicos de la clínica mediante dos modos:

- **Kiosko Autoservicio:** orientado a pacientes.
- **Panel Administrativo:** orientado a recepcionistas.

#### Puntos Positivos

- Cumple estrictamente con las directrices académicas de construir una **aplicación de escritorio**.
- Desacopla la lógica de negocio mediante una **API REST en Spring Boot**.
- Elimina riesgos de seguridad al no exponer la base de datos **PostgreSQL** directamente a los ejecutables.
- Sienta las bases para conectar **interfaces web** en fases futuras.
- Permite mantener una arquitectura profesional, modular y extensible.

---

### Propuesta B — Arquitectura Monolítica de Escritorio Clásica

**JavaFX + JDBC Directo**

#### Descripción

Aplicación nativa **JavaFX** que interactúa directamente con la base de datos **PostgreSQL** mediante un driver **JDBC**, centralizando la lógica dentro del mismo cliente de escritorio.

#### Puntos Negativos

- Alto riesgo de seguridad al distribuir credenciales de base de datos compiladas en el ejecutable cliente.
- Presenta rigidez para expandir el sistema a otros canales, como el **formulario web de pacientes**.
- Puede duplicar la validación de las reglas de negocio en caso de querer escalar el sistema.

---

### Propuesta C — Aplicación Monolítica Web Tradicional

**Spring Boot + Thymeleaf / HTML**

#### Descripción

Servidor web renderizado desde el servidor (**Server-Side Rendering**) y accesible completamente mediante un navegador web.

#### Puntos Negativos / Descarte


No cumple con el requerimiento del curso universitario de entregar una **aplicación de escritorio cliente**.

Además, priva al área de admisión de una interfaz pesada (**Rich Client**) optimizada para:

- Periféricos locales.
- Impresoras térmicas.
- Atención presencial.
- Flujos de trabajo rápidos en los puntos físicos de la clínica.

---

## ¿Por qué optamos por la Propuesta A?

La **Propuesta A** resuelve la exigencia académica del desarrollo de escritorio sin generar deuda técnica.

Desconecta la **interfaz gráfica (JavaFX)** de la **base de datos** mediante contratos **REST (JSON)**.

Esto permite implementar los requerimientos del módulo de atención manteniendo una arquitectura:

- **Profesional**
- **Extensible**
- **Modular**
- **Segura**

Además, se encuentra alineada con la filosofía **"Paciente Ante Todo" (PAT)** de la Clínica Anglo Americana.

### Arquitectura propuesta

```text
┌─────────────────────────────────────────────┐
│           CLIENTE DE ESCRITORIO             │
│                  JavaFX                     │
│              + AtlantaFX                    │
├──────────────────┬──────────────────────────┤
│                  │                          │
│ Kiosko            │ Panel Administrativo    │
│ Autoservicio      │ Recepción               │
│                  │                          │
└──────────────────┴────────────┬─────────────┘
                               │
                         API REST / JSON
                               │
                               ▼
┌─────────────────────────────────────────────┐
│              BACKEND CENTRAL                │
│              Spring Boot                   │
│                                             │
│          Lógica de negocio                  │
│          Validaciones                       │
│          Servicios REST                     │
└──────────────────────┬──────────────────────┘
                       │
                       │ JDBC / JPA
                       ▼
┌─────────────────────────────────────────────┐
│                 PostgreSQL                  │
│                                             │
│             Base de datos central           │
└─────────────────────────────────────────────┘
