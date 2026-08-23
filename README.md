# Reglas Generales del Repositorio - ProyectoCitasMedicas

Bienvenido al repositorio oficial del **ProyectoCitasMedicas**. Este documento establece las normas de desarrollo, la estructura del proyecto, el flujo de trabajo con Git/GitHub, las reglas de Integración Continua / Despliegue Continuo (CI/CD), y los estándares de calidad que todos los integrantes del equipo deben cumplir durante las **18 semanas** de desarrollo.

---

## 1. Información General del Proyecto

- **Nombre del Repositorio:** `ProyectoCitasMedicas`
- **Curso / Contexto:** Proyecto Integrador de Desarrollo de Software (18 Semanas)
- **Objetivo Central:** Al finalizar el curso, el estudiante construye soluciones informáticas a problemas específicos utilizando las tecnologías aprendidas hasta el momento, las cuales definirán el alcance de la solución elaborada.
- **Stack Tecnológico:**
  - **Lenguaje Principal:** Java
  - **Backend (API):** Java Spring Boot
  - **Cliente Desktop (App):** Java (JavaFX / Swing)
  - **Base de Datos:** PostgreSQL
  - **Control de Versiones:** Git & GitHub

---

## 2. Equipo de Desarrollo

| Nombre Completo    | Código de Estudiante | Usuario de GitHub | Rol / Responsabilidad |
| :----------------- | :------------------- | :---------------- | :-------------------- |
| _(Agregar Nombre)_ | _(Agregar Código)_   | `@eslimdaga`      | Desarrollador         |
| Melissa            | `U22302923`          | `@Melissa-stars`  | Desarrollador         |
| Jimena             | `U22237919`          | `@Jimena-CC`      | Desarrollador         |
| Stefano            | `U23261601`          | `@svamnpent`      | Desarrollador         |
| Mateo              | `U23203172`          | `@elPiveDc`       | Desarrollador         |

---

## 3. Arquitectura General del Sistema

El sistema sigue una arquitectura de n-capas distribuida:

```
+------------------------+             +------------------------+             +------------------------+
|  Aplicativo Escritorio | HTTP / REST |      API Backend       |    JDBC     | Base de Datos PostgreSQL|
|     (JavaFX / Swing)   | ----------> |  (Spring Boot / Java)  | ----------> |       (PostgreSQL)     |
+------------------------+             +------------------------+             +------------------------+
```

1. **Desktop Client (`/desktop`):** Consume la API RESTful mediante peticiones HTTP (JSON). No tiene acceso directo a la base de datos.
2. **API Backend (`/api`):** Expone endpoints REST, procesa la lógica de negocio, valida datos y gestiona la seguridad.
3. **Base de Datos:** Instancia de PostgreSQL gestionada por la API mediante JPA/Hibernate o JDBC.

---

## 4. Estructura de Directorios del Repositorio

El directorio raíz `ProyectoCitasMedicas` estará estructurado de la siguiente manera:

```text
ProyectoCitasMedicas/
├── .github/
│   ├── ISSUE_TEMPLATE/
│   │   ├── bug_report.md
│   │   └── feature_request.md
│   ├── PULL_REQUEST_TEMPLATE.md
│   └── workflows/
│       ├── api-ci.yml
│       └── desktop-ci.yml
├── docs/
│   ├── README.md
│   ├── arquitectura.md
│   ├── base-de-datos/
│   │   ├── script_inicial.sql
│   │   └── modelo_er.png
│   └── manuales/
├── api/
│   ├── README.md
│   ├── pom.xml
│   └── src/
│       ├── main/java/com/citasmedicas/api/
│       └── test/java/com/citasmedicas/api/
├── desktop/
│   ├── README.md
│   ├── pom.xml
│   └── src/
│       ├── main/java/com/citasmedicas/desktop/
│       └── test/java/com/citasmedicas/desktop/
├── .gitignore
└── README.md
```

### Descripción de Componentes y READMEs Internos

#### A. Directorio Raíz (`/`)

- **`README.md` (Raíz):** Visión general del repositorio, estado del proyecto, instrucciones de clonado e instalación rápida, tabla de integrantes y enlaces a subdirectorios.

#### B. Directorio de Documentación (`/docs`)

- **Propósito:** Almacenar diagramas de arquitectura, casos de uso, especificaciones de la API, modelo entidad-relación de PostgreSQL y actas de reuniones.
- **`docs/README.md`:** Explica el índice de documentos disponibles, normas para documentar en Markdown y mantenimiento del modelo de datos.

#### C. Directorio API Backend (`/api`)

- **Propósito:** Proyecto Java autónomo que contiene el servidor backend, la configuración de conexión a PostgreSQL y los controladores REST.
- **`api/README.md`:** Explica las dependencias, configuración del archivo `application.properties` / `environment variables`, endpoints expuestos, comandos para compilar (`mvn clean install` o `gradle build`) y ejecución local.

#### D. Directorio Aplicación de Escritorio (`/desktop`)

- **Propósito:** Proyecto Java autónomo con la interfaz gráfica de usuario (GUI) para la gestión de citas médicas.
- **`desktop/README.md`:** Explica los requisitos de ejecución (JRE/JDK), configuración para conectar la app a la API (`api.url`), compilación del ejecutable `.jar` y guías de la interfaz.

---

## 5. Convenciones de Commits (Conventional Commits)

Todos los mensajes de commit deben seguir la sintaxis estandarizada:

```text
<tipo>(<alcance>): <descripción corta en imperativo>
```

### Tipos Permitidos:

- `feat`: Nueva funcionalidad (ej. `feat(api): agregar endpoint de autenticación JWT`).
- `fix`: Corrección de errores/bugs (ej. `fix(desktop): corregir validación de fecha en formulario`).
- `docs`: Cambios solo en la documentación (ej. `docs(readme): actualizar tabla de integrantes`).
- `style`: Cambios de formato (espacios, formato de código, comas) sin modificar lógica.
- `refactor`: Refactorización de código sin cambiar funcionalidad externa.
- `test`: Adición o modificación de pruebas unitarias/integración.
- `chore`: Tareas secundarias o herramientas de construcción (ej. actualización de `pom.xml`).

---

## 6. Estrategia de Ramificación (GitFlow Simplificado)

Queda **estrictamente prohibido** hacer `commit` o `push` directo en la rama `main` o `develop`.

- `main`: Rama de producción/entrega final. Solo código estable y probado.
- `develop`: Rama principal de integración.
- **Ramas Temáticas (Feature/Fix):**
  - `feature/<modulo>-<descripcion>` (ej. `feature/api-login`, `feature/desktop-citas`)
  - `fix/<modulo>-<descripcion>` (ej. `fix/api-postgres-connection`)

---

## 7. Reglas de Pull Requests (PR) y Issues

1. **Creación de PR:**
   - Las ramas deben solicitar fusionarse hacia `develop`.
   - El título del PR debe seguir el formato de commit: `feat(api): implementar registro de médicos`.
   - Se debe completar el **Pull Request Template** obligatorio.

2. **Revisión y Aprobación:**
   - Todo PR requiere mínimo **1 revisión y aprobación (Code Review)** de otro integrante del equipo.
   - Ningún desarrollador puede aprobar su propio PR.
   - La suite de pruebas de CI debe pasar con éxito antes del merge.

3. **Estrategia de Merge:**
   - Se prefiere **Squash and Merge** para mantener un historial limpio en `develop`.

---

## 8. Integración Continua (CI) y Despliegue Continuo (CD)

### Integración Continua (CI)

A través de **GitHub Actions**, se ejecutarán los siguientes flujos de trabajo ante cada `push` o `Pull Request` hacia `develop` o `main`:

- **Compilación:** Verificación de que los módulos Java (`api` y `desktop`) compilen sin errores.
- **Pruebas Automatizadas:** Ejecución de pruebas unitarias (JUnit / Mockito).
- **Análisis Estático de Código:** Verificación de formato y buenas prácticas (Checkstyle / SpotBugs).
- **Verificación de BD:** Validación de scripts SQL de PostgreSQL.

### Despliegue Continuo (CD) / Empaquetado

- **API:** Generación automatizada del artefacto `api-service.jar` o despliegue en entorno de pruebas/servidor cloud al realizar merge a `main`.
- **Desktop:** Generación automatizada del ejecutable distribuible de la aplicación de escritorio (`desktop-app.jar`) publicado en los **GitHub Releases** del repositorio.

---

## 9. Reglas de Calidad de Código en Java

1. **Nombrado:**
   - Clases e Interfaces: `PascalCase` (ej. `CitaMedicaController`).
   - Métodos y Variables: `camelCase` (ej. `obtenerCitasPorPaciente`).
   - Constantes: `UPPER_SNAKE_CASE` (ej. `MAX_INTENTOS_LOGIN`).
2. **Tratamiento de Excepciones:** No dejar bloques `catch` vacíos. Usar excepciones personalizadas y logging.
3. **Buenas Prácticas:** Inyección de dependencias, bajo acoplamiento y alta cohesión. Uso de variables de entorno para credenciales de PostgreSQL.
