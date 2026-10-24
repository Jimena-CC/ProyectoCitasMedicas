# Especificación de Requerimientos - Backoffice web (Admisión, Médico y Administrador)

## 1. Alcance y contexto

Este documento define los requerimientos funcionales y no funcionales de la **segunda fase** del sistema **MediCitas Anglo**: el **Backoffice web** de gestión interna, construido como aplicación web separada (React + Vite + Tailwind v4 con el sistema de diseño AlignUI, carpeta `web/`) que consume la misma **API Spring Boot** del Kiosco. Cubre seguridad y roles, auditoría, agenda del médico, registro de asistencia, admisión en ventanilla, administración de catálogos y agendas, y reportes.

Complementa a la [Especificación de Requerimientos de la Fase 1](requirements-specification.md) (RF-01 a RF-14 y RNF-01 a RNF-08) y continúa su numeración: los requerimientos funcionales comienzan en **RF-15** y los no funcionales en **RNF-09**. Cada RF cita el caso de uso que lo origina según el [cuadro de casos de uso](use-cases.md).

Las reglas de negocio de la Fase 1 siguen vigentes en ventanilla: un cupo no puede reservarse dos veces (RF-07), un paciente no puede tener dos citas que se crucen (RF-08) y los fallos del Servicio de Mensajería no alteran la cita (RF-14).

## 2. Actores

| Actor | Tipo | Descripción | Interacción principal | Responsabilidad |
|---|---|---|---|---|
| **Personal de Admisión / Central de Citas** | Persona (usuario interno, rol `FRONT_DESK`) | Empleado administrativo en ventanilla o central telefónica que usa el Backoffice web. | Búsqueda y registro de pacientes, validación de aseguradoras, agendamiento, reprogramación y anulación de citas a nombre del paciente. | Mantener la agilidad en la atención y dejar trazabilidad de cada cambio en la agenda. |
| **Médico / Profesional de Salud** | Persona (usuario interno, rol `DOCTOR`) | Médico especialista de consultorio externo que usa el Backoffice web. | Consulta de su agenda del día y registro de asistencia o inasistencia del paciente. | Actualizar el estado de la cita en tiempo real al atender al paciente. |
| **Administrador del Sistema** | Persona (usuario interno de TI, rol `ADMIN`) | Personal técnico responsable del sistema que usa el Backoffice web. | Gestión de usuarios, médicos, especialidades y agendas; revisión de auditoría (Ley 29733) y generación de reportes. | Velar por la seguridad, los permisos y la calidad de la información operativa. |
| **Servicio de Mensajería Externalizado** | Sistema externo | API externa de SMS/Email. | Recibe las mismas confirmaciones y recordatorios que en la Fase 1 cuando la cita se gestiona desde ventanilla. | Garantizar la entrega de las notificaciones. |

## 3. Requerimientos Funcionales (RF)

### 3.1. Seguridad y roles

| Código | Nombre | Descripción | Caso de uso | Prioridad | Criterio de Verificación |
|---|---|---|---|---|---|
| **RF-15** | Inicio de sesión | El Backoffice debe permitir iniciar sesión con usuario y contraseña. La API valida la contraseña contra su hash BCrypt en `app_user`, actualiza `last_access` y devuelve un token JWT que incluye el rol del usuario. Las cuentas inactivas no pueden iniciar sesión. | CU-011 | Alta | Iniciar sesión con credenciales correctas y verificar que se reciba un token con el rol; repetir con contraseña incorrecta o cuenta inactiva y verificar la respuesta `401` con mensaje claro. |
| **RF-16** | Cierre de sesión | El usuario debe poder cerrar sesión desde el Backoffice; la API invalida el token y el navegador elimina la sesión local. | CU-012 | Media | Cerrar sesión y verificar que el token anterior sea rechazado en la siguiente petición. |
| **RF-17** | Control de acceso por rol | Cada endpoint interno debe autorizarse por rol: `/doctor/**` solo `DOCTOR`, `/front-desk/**` solo `FRONT_DESK`, `/admin/**` solo `ADMIN`. El Backoffice muestra únicamente los módulos del rol autenticado. | CU-011 | Alta | Con un token de rol `DOCTOR` invocar un endpoint `/admin/**` y verificar la respuesta `403`; verificar que el menú del Backoffice no muestre módulos ajenos al rol. |
| **RF-18** | Bloqueo de endpoints internos | Toda petición a `/auth/me`, `/doctor/**`, `/front-desk/**` y `/admin/**` sin token, con token vencido o manipulado debe rechazarse con `401`, sin exponer información del sistema. Los endpoints públicos del Kiosco no cambian. | CU-011 | Alta | Invocar un endpoint interno sin cabecera `Authorization` y con un token alterado; ambos deben responder `401` y los endpoints del Kiosco deben seguir respondiendo sin token. |

### 3.2. Auditoría

| Código | Nombre | Descripción | Caso de uso | Prioridad | Criterio de Verificación |
|---|---|---|---|---|---|
| **RF-19** | Auditoría de citas | La API debe registrar en `audit_log` (usuario, acción, entidad, id, detalle, IP y fecha) toda creación, reprogramación, anulación y registro de asistencia de citas realizada desde el Backoffice, además del evento correspondiente en `appointment_history` con el `user_id` del operador. | CU-013 | Alta | Agendar, reprogramar y anular una cita desde ventanilla y registrar una asistencia; verificar que existan cuatro filas en `audit_log` y cuatro eventos en `appointment_history` con el usuario correcto. |
| **RF-20** | Auditoría de cambios administrativos | La API debe registrar en `audit_log` la creación y modificación de usuarios, médicos, especialidades y cupos, así como los inicios y cierres de sesión (exitosos y fallidos). | CU-013 | Alta | Crear un usuario, editar un médico y generar cupos; verificar que cada acción aparezca en la consulta de auditoría con el administrador que la ejecutó. |

### 3.3. Agenda del médico

| Código | Nombre | Descripción | Caso de uso | Prioridad | Criterio de Verificación |
|---|---|---|---|---|---|
| **RF-21** | Consulta de agenda del día | El médico autenticado debe ver en el Backoffice las citas de su agenda para una fecha (por defecto hoy), ordenadas por hora, con nombre del paciente, documento, motivo de consulta, sede, consultorio, seguro y estado (`GET /doctor/agenda?date=`). El médico se obtiene de `app_user.doctor_id`. | CU-014 | Alta | Iniciar sesión como un médico con citas registradas para hoy y verificar que la agenda muestre exactamente esas citas y ninguna de otro médico. |
| **RF-22** | Registro de asistencia e inasistencia | El médico debe poder marcar una cita BOOKED de su agenda como `ATTENDED` o `NO_SHOW` (`PATCH /doctor/appointments/{id}/attendance`). La API registra el evento `ATTENDANCE` en `appointment_history`, cancela recordatorios pendientes y rechaza cambios sobre citas canceladas, ya marcadas o de otro médico. | CU-015 | Alta | Marcar una cita como atendida y otra como inasistencia; verificar el nuevo estado, el evento `ATTENDANCE` y que un segundo intento sobre la misma cita responda `422 APPOINTMENT_NOT_MODIFIABLE`. |

### 3.4. Admisión

| Código | Nombre | Descripción | Caso de uso | Prioridad | Criterio de Verificación |
|---|---|---|---|---|---|
| **RF-23** | Búsqueda y registro de paciente en ventanilla | El Personal de Admisión debe poder buscar a un paciente por tipo y número de documento y, si no existe, registrarlo con las mismas validaciones de RF-01 (`GET /front-desk/patients`, `POST /front-desk/patients`). | CU-016 | Alta | Buscar un documento existente y verificar que se muestre el perfil; buscar uno inexistente, registrarlo y verificar que un segundo registro con el mismo documento sea rechazado. |
| **RF-24** | Validación de aseguradora | Antes de agendar con seguro, el Backoffice debe consultar a la API si la cobertura del paciente está `ACTIVE` y vigente a la fecha; si no lo está, debe indicarlo y permitir continuar como atención particular (`GET /front-desk/patients/{id}/insurance`). | CU-017 | Media | Validar a un paciente con cobertura `EXPIRED` y verificar el mensaje de cobertura no vigente y la opción de atención particular; repetir con cobertura `ACTIVE` y verificar que se permita agendar con seguro. |
| **RF-25** | Agendamiento en ventanilla | El Personal de Admisión debe poder reservar una cita a nombre del paciente eligiendo especialidad, sede, fecha y horario disponible. La API aplica RF-07 y RF-08, registra `booking_channel = FRONT_DESK`, usa clave de idempotencia y programa las notificaciones de RF-11 y RF-12 (`POST /front-desk/appointments`). | CU-018 | Alta | Agendar una cita desde ventanilla y verificar que quede BOOKED con canal `FRONT_DESK`, que el cupo pase a `BOOKED` y que se generen la confirmación y el recordatorio; intentar tomar el mismo cupo dos veces y verificar que solo una reserva se acepte. |
| **RF-26** | Reprogramación en ventanilla | El Personal de Admisión debe poder trasladar una cita BOOKED del paciente a otro cupo disponible de la misma especialidad, liberando el original, con las mismas reglas de RF-09 (`PATCH /front-desk/appointments/{id}/reschedule`). | CU-019 | Media | Reprogramar una cita desde ventanilla y verificar que el cupo original quede `FREE`, el nuevo `BOOKED` y que el evento `RESCHEDULE` tenga el `user_id` del operador. |
| **RF-27** | Anulación en ventanilla | El Personal de Admisión debe poder anular una cita BOOKED del paciente indicando el motivo, con las mismas reglas de RF-10 (`PATCH /front-desk/appointments/{id}/cancellation`). | CU-020 | Media | Anular una cita desde ventanilla y verificar el estado `CANCELLED`, el cupo liberado, el motivo en `appointment_history` y el aviso de anulación generado. |

### 3.5. Administración

| Código | Nombre | Descripción | Caso de uso | Prioridad | Criterio de Verificación |
|---|---|---|---|---|---|
| **RF-28** | Gestión de usuarios | El Administrador debe poder listar, crear, editar y desactivar usuarios del Backoffice, asignando rol `ADMIN`, `FRONT_DESK` o `DOCTOR` y, para los médicos, el `doctor_id` asociado. Las contraseñas se almacenan con hash BCrypt y nunca se devuelven (`/admin/users`). | CU-021 | Alta | Crear un usuario `DOCTOR` vinculado a un médico, iniciar sesión con él y verificar que vea su agenda; desactivarlo y verificar que ya no pueda iniciar sesión. |
| **RF-29** | Gestión de médicos | El Administrador debe poder registrar y actualizar médicos (licencia única, registro de especialista, nombres, especialidad y estado activo) (`/admin/doctors`). | CU-022 | Alta | Registrar un médico con una licencia ya existente y verificar el rechazo; registrar uno válido y verificar que aparezca al generar cupos. |
| **RF-30** | Gestión de especialidades | El Administrador debe poder registrar y actualizar especialidades (nombre único, descripción, minutos por cita y estado activo) y las sedes donde se ofrecen (`/admin/specialties`). | CU-023 | Media | Crear una especialidad activa en una sede y verificar que el Kiosco la liste para esa sede; desactivarla y verificar que deje de listarse. |
| **RF-31** | Gestión de agendas y horarios | El Administrador debe poder generar cupos (`slot`) para un médico en una sede y consultorio, indicando rango de fechas, hora de inicio y fin y la duración de la especialidad, y bloquear cupos puntuales (`BLOCKED`). No se pueden generar cupos duplicados ni bloquear cupos ya reservados (`/admin/slots`). | CU-024 | Alta | Generar la agenda de una semana para un médico y verificar que los cupos aparezcan en la disponibilidad del Kiosco; intentar generar el mismo rango de nuevo y verificar que no se dupliquen; bloquear un cupo `FREE` y verificar que deje de ofrecerse. |
| **RF-32** | Consulta de auditoría | El Administrador debe poder consultar el registro de auditoría filtrando por usuario, acción, entidad y rango de fechas, con paginación (`GET /admin/audit`). | CU-025 | Media | Filtrar la auditoría por un usuario y un rango de fechas y verificar que los resultados coincidan con las acciones realizadas en ese periodo. |

### 3.6. Reportes

| Código | Nombre | Descripción | Caso de uso | Prioridad | Criterio de Verificación |
|---|---|---|---|---|---|
| **RF-33** | Reporte de citas por estado | El Administrador debe poder obtener, para un rango de fechas y opcionalmente una sede o especialidad, el total de citas atendidas, canceladas y no asistidas (`GET /admin/reports/appointments`). | CU-026 | Alta | Registrar un conjunto conocido de citas con distintos estados y verificar que el reporte devuelva exactamente esos totales. |
| **RF-34** | Tasa de inasistencia | El reporte debe calcular la tasa de inasistencia como `NO_SHOW / (ATTENDED + NO_SHOW)` para el rango consultado, desglosada por especialidad (`GET /admin/reports/no-show-rate`). | CU-026 | Media | Con 8 citas atendidas y 2 no asistidas en una especialidad, verificar que la tasa reportada sea 20 %. |
| **RF-35** | Ocupación por especialidad | El reporte debe mostrar, por especialidad y sede, el porcentaje de cupos ocupados (`BOOKED`, `ATTENDED` o `NO_SHOW`) sobre los cupos generados en el rango (`GET /admin/reports/occupancy`). | CU-026 | Media | Generar 10 cupos para una especialidad, reservar 4 y verificar que la ocupación reportada sea 40 %. |
| **RF-36** | Exportación de reportes | Cualquier reporte debe poder descargarse en formato CSV con codificación UTF-8 y los mismos filtros aplicados en pantalla (`GET /admin/reports/export`). | CU-027 | Baja | Exportar el reporte de citas por estado y verificar que el archivo CSV contenga las mismas filas y totales que la pantalla. |

## 4. Requerimientos No Funcionales (RNF)

Clasificados según ISO/IEC 25010.

| Código | Característica | Nombre | Descripción | Prioridad | Criterio de Verificación |
|---|---|---|---|---|---|
| **RNF-09** | Seguridad | Almacenamiento seguro de contraseñas | Las contraseñas de `app_user` deben almacenarse únicamente como hash BCrypt (factor de costo mínimo 10); nunca en texto plano, en logs ni en respuestas de la API. | Alta | Revisar la tabla `app_user` y los logs tras crear un usuario y verificar que solo exista el hash; verificar que `/admin/users` no devuelva el campo de contraseña. |
| **RNF-10** | Seguridad | Sesión con expiración | El token JWT debe expirar como máximo a las 8 horas de emitido y el Backoffice debe cerrar la sesión automáticamente tras 30 minutos de inactividad, pidiendo volver a iniciar sesión. | Alta | Usar un token vencido y verificar la respuesta `401`; dejar el Backoffice inactivo 30 minutos y verificar que redirija al inicio de sesión. |
| **RNF-11** | Seguridad | Comunicación cifrada | Toda comunicación entre el Backoffice web y la API debe realizarse sobre HTTPS; el token se envía solo en la cabecera `Authorization` y no en la URL. | Alta | Verificar que las peticiones del Backoffice usen HTTPS y que ninguna URL ni log contenga el token. |
| **RNF-12** | Seguridad | Protección de datos personales (Ley 29733) | Se aplica el principio de mínimo privilegio: cada rol accede solo a los datos que necesita (el médico solo a su agenda, Admisión solo a los datos de contacto y seguro necesarios para agendar) y los datos personales no se exponen en reportes exportados más allá de lo indispensable. | Alta | Revisar los endpoints por rol y verificar que un médico no pueda consultar citas de otro médico ni la lista de pacientes; revisar el CSV exportado y verificar que no incluya datos personales innecesarios. |
| **RNF-13** | Seguridad | Auditoría de accesos | Todo inicio de sesión (exitoso o fallido), cierre de sesión y consulta de auditoría debe quedar registrado con usuario, IP de origen y fecha, y conservarse al menos 2 años. | Media | Realizar un inicio de sesión fallido y uno exitoso y verificar ambas filas en `audit_log` con la IP de origen. |
| **RNF-14** | Rendimiento | Latencia de la agenda del día | La consulta de la agenda del día de un médico debe responder en menos de 2 segundos con hasta 60 citas en el día. | Alta | Medir el tiempo de respuesta de `GET /doctor/agenda` con una agenda de 60 citas mediante Postman o JMeter. |
| **RNF-15** | Rendimiento | Latencia de reportes | Los reportes deben responder en menos de 5 segundos para un rango de hasta 12 meses con el volumen anual de la clínica (alrededor de 250 mil citas). | Media | Cargar un conjunto de datos de 250 mil citas y medir el tiempo de respuesta de cada reporte para un rango de 12 meses. |
| **RNF-16** | Usabilidad | Claridad de mensajes al usuario interno | Los errores de validación y de negocio del Backoffice deben mostrarse en español, sin tecnicismos ni detalles técnicos, con la misma política de mensajes que RNF-06. | Alta | Provocar un error de cupo tomado, de credenciales inválidas y de cobertura no vigente y verificar que los mensajes sean comprensibles y en español. |
| **RNF-17** | Usabilidad | Navegación por rol | Tras iniciar sesión, el Backoffice debe llevar al usuario directamente al módulo de su rol (agenda del día, ventanilla o administración) y ocultar los módulos que no le corresponden. | Media | Iniciar sesión con un usuario de cada rol y verificar la pantalla inicial y el menú visible. |
| **RNF-18** | Compatibilidad | Acceso desde navegador moderno | El Backoffice debe funcionar en las dos últimas versiones de Chrome, Edge y Firefox en resolución mínima de 1280 × 720 sin instalar software adicional. | Media | Ejecutar el flujo de agenda del día y de agendamiento en los tres navegadores y verificar que no haya errores ni elementos fuera de pantalla. |
| **RNF-19** | Mantenibilidad | Documentación OpenAPI de los nuevos endpoints | Los endpoints `/auth/**`, `/doctor/**`, `/front-desk/**` y `/admin/**` deben estar documentados en OpenAPI/Swagger, incluyendo el esquema de seguridad JWT, parámetros, respuestas y códigos de error. | Baja | Abrir `/swagger-ui.html`, verificar que los nuevos endpoints aparezcan con el candado de seguridad y probar al menos tres de ellos desde la propia interfaz. |

## 5. Trazabilidad RF - CU

| Caso de uso | Requerimientos funcionales |
|---|---|
| CU-011 Iniciar sesión | RF-15, RF-17, RF-18 |
| CU-012 Cerrar sesión | RF-16 |
| CU-013 Registrar auditoría | RF-19, RF-20 |
| CU-014 Consultar agenda del día | RF-21 |
| CU-015 Registrar asistencia o inasistencia | RF-22 |
| CU-016 Buscar paciente | RF-23 |
| CU-017 Validar aseguradora | RF-24 |
| CU-018 Agendar cita en ventanilla | RF-25 (+ RF-07, RF-08, RF-11, RF-12) |
| CU-019 Reprogramar cita en ventanilla | RF-26 (+ RF-09) |
| CU-020 Anular cita en ventanilla | RF-27 (+ RF-10) |
| CU-021 Gestionar usuarios | RF-28 |
| CU-022 Gestionar médicos | RF-29 |
| CU-023 Gestionar especialidades | RF-30 |
| CU-024 Gestionar agendas y horarios | RF-31 |
| CU-025 Revisar auditoría | RF-32 |
| CU-026 Generar reportes | RF-33, RF-34, RF-35 |
| CU-027 Exportar reportes | RF-36 |
