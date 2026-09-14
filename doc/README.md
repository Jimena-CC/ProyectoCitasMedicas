# Documentación del Proyecto - Citas Médicas

Este directorio contiene la documentación técnica y de diseño para la solución informática.

## Contenido

- `/base-de-datos`: Contiene scripts DDL/DML de PostgreSQL y el modelo Entidad-Relación.
- `arquitectura.md`: Explicación detallada del flujo de comunicación (Escritorio -> API REST -> PostgreSQL).
- `technologies.md`: Stack tecnológico del proyecto (desarrollo, utilidades y testeo) con la justificación de cada elección.
- `contrato-api.md`: Contrato REST entre el Kiosko (JavaFX) y la API para las 5 pantallas funcionales.
- `/manuales`: Guías de usuario e instalación del sistema.

## Diagramas (`/diagramas`)

Los `.puml` generan sus PNG y SVG en `diagramas/img/` automáticamente al abrir un PR hacia `develop`.

| Archivo | Contenido |
| :--- | :--- |
| `01-Diagrama-Procesos-Tradicional.puml` | Proceso actual (AS-IS) de reserva en ventanilla o central, con los problemas identificados. |
| `01-Diagrama-Procesos-Solucion.puml` | Proceso propuesto (TO-BE) de reserva en el Kiosko, con reintentos ante cupo tomado o cita duplicada. |
| `02-Diagrama-Casos-Uso.puml` | Casos de uso por fase. |
| `03-Diagrama-Entidad-Relacion.puml` | Modelo de datos en PostgreSQL: catálogo, pacientes y seguros, agenda y citas, notificaciones, seguridad. |
| `04-Diagrama-Clases.puml` | Diagrama de clases del dominio. |
| `05-Architecture-Diagram.puml` | Arquitectura de componentes. |
| `06-Diagrama-Procesos-Reprogramacion.puml` | Proceso TO-BE para reprogramar una cita, con reintento ante cupo tomado. |
| `06-Diagrama-Procesos-Anulacion.puml` | Proceso TO-BE para anular una cita y liberar su cupo. |
| `07-Diagrama-Procesos-Notificaciones.puml` | Proceso TO-BE de envío de confirmaciones y recordatorios con reintentos. |
