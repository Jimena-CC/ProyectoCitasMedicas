# Definición de Tecnologías

Stack tecnológico del **ProyectoCitasMedicas**. Documenta las herramientas de desarrollo, utilidades y testeo, con la justificación de cada elección.

> Issue #4. Alimenta la propuesta de soluciones del issue #5.

```
Desktop (JavaFX) --HTTP/JSON--> API REST (Spring Boot) --JPA/Hibernate--> PostgreSQL
```

Java en ambos módulos, con dos proyectos Maven independientes que no comparten dependencias.

---

## 1. Herramientas de Desarrollo

| Herramienta | Versión | Uso |
| :--- | :--- | :--- |
| **Java** | 21 (LTS) | Base de ambos módulos. Requisito mínimo de Spring Boot 4. |
| **Maven** | 3.9+ (wrapper) | Construcción. Estándar del ecosistema Spring, con más documentación que Gradle. |
| **Spring Boot** | 4.1.1 | API REST. Ya inicializado en `/api` con Web MVC, Data JPA y Validation. |
| **Hibernate** | Gestionada por el BOM | Implementación de JPA incluida en Data JPA. **No se declara aparte.** |
| **PostgreSQL** | 15 (`postgres:15-alpine`) | Base de datos. Las citas exigen integridad referencial y transacciones. |
| **Docker Compose** | Docker Desktop | Levanta BD y API con un comando. Ya configurado en `docker-compose.yml`. |
| **JavaFX** | 21.0.12 (LTS) | Interfaz de escritorio. Admite CSS y separa vista de lógica. |
| **AtlantaFX** | 2.0.1 | Temas modernos para JavaFX (Primer, Nord, Cupertino). Acabado profesional sin diseñar desde cero. |
| **Ikonli** | 12.4.0 | Iconos vectoriales (Material Design 2). No pierden calidad en el modo Kiosko. |
| **ControlsFX** | 11.2.2 | Notificaciones, tablas con filtro y validación de formularios. |
| **javafx-maven-plugin** | 0.0.8 | Ejecuta el escritorio con `mvn javafx:run`. |

### Entorno de trabajo

| Herramienta | Uso |
| :--- | :--- |
| **Visual Studio Code** | IDE del equipo. Extensiones: *Extension Pack for Java*, *Spring Boot Extension Pack* y *Docker*. |
| **PlantUML** | Diagramas como código. Los `.puml` en `doc/diagramas` generan PNG y SVG automáticamente vía GitHub Actions. Conviene previsualizarlos en el servidor web de PlantUML antes de subirlos. |
| **GitHub Actions** | Integración continua. Compila y prueba ambos módulos en cada PR hacia `develop`. |

La interfaz se construye **en código Java**, no con archivos FXML. Se evita así depender de Scene Builder, que es una aplicación externa a VS Code, y se reducen los conflictos de Git: el XML autogenerado produce merges difíciles cuando varias personas editan la misma pantalla.

---

## 2. Herramientas de Utilidades

| Herramienta | Versión | Módulo | Uso |
| :--- | :--- | :--- | :--- |
| **Lombok** | 1.18.46 | api / desktop | Genera constructores, getters y setters por anotación. Reduce el ruido en las revisiones. |
| **MapStruct** | 1.6.3 | api | Conversión entidad ↔ DTO. Evita exponer entidades de BD en los endpoints. |
| **Jackson** | 2.22.2 | desktop | Conversión JSON ↔ objetos Java. En la API ya viene con Spring Web. |
| **HttpClient (JDK)** | Nativo | desktop | Consumo de la API. Al ser parte de la biblioteca estándar no añade dependencias. |
| **SLF4J + Logback** | Gestionada por el BOM | api | Registro de eventos. Sustituye el uso de `System.out.println`. |
| **springdoc-openapi** | 3.1.0 | api | Genera la especificación OpenAPI y sirve Swagger UI. Ver sección 3. |

---

## 3. Documentación y prueba de la API (OpenAPI / Swagger)

La API se documenta con **springdoc-openapi**, que genera la especificación **OpenAPI** a partir de los controladores y expone **Swagger UI**, una interfaz web donde cualquiera puede ver los endpoints disponibles y ejecutarlos desde el navegador.

| Ruta | Contenido |
| :--- | :--- |
| `/v3/api-docs` | Especificación OpenAPI en formato JSON. |
| `/swagger-ui.html` | Interfaz web interactiva para probar los endpoints. |

Su valor en este proyecto es concreto: el módulo `/desktop` consume la API por HTTP, y Swagger UI permite **probar cada endpoint antes de escribir el código del cliente**. Quien trabaje en el escritorio no necesita leer el backend ni esperar a que esté terminado para conocer el contrato de cada servicio.

### Estado actual y versión

La dependencia **ya está declarada** en `api/pom.xml`, en la versión `2.5.0`. Se verificó que funciona sobre Spring Boot 4.1.1: levantando la aplicación en un puerto aleatorio, `/v3/api-docs` responde `200` con una especificación válida.

Aun así se recomienda subirla a **3.1.0**, que es la línea de springdoc alineada a Spring Boot 4. También se probó y funciona, con dos ventajas: genera especificación **OpenAPI 3.1** en lugar de 3.0.1, y evita quedar en una línea pensada para Spring Boot 3.

```xml
<dependency>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
    <version>3.1.0</version>
</dependency>
```

Como complemento manual se usa **Bruno** o **Postman**, útiles para guardar colecciones de peticiones reutilizables y compartirlas entre el equipo, algo que Swagger UI no cubre.

---

## 4. Herramientas de Testeo

| Herramienta | Versión | Tipo | Uso |
| :--- | :--- | :--- | :--- |
| **JUnit 5 + Mockito + AssertJ** | Gestionada por el BOM | Unitaria | Incluidas en los *starters* de test ya presentes en `api/pom.xml`. |
| **MockMvc** | Gestionada por el BOM | Integración | Prueba los controladores simulando peticiones HTTP. |
| **H2** | Gestionada por el BOM | Integración | BD en memoria para las pruebas. Arranca en segundos y no requiere Docker en CI. |
| **Testcontainers** | Gestionada por el BOM | Integración | PostgreSQL real y descartable, para lo que H2 no reproduce. |
| **TestFX** | 4.0.18 | Interfaz | Automatiza clics y navegación para probar los flujos del Kiosko. |
| **JaCoCo** | 0.8.x | Cobertura | Reporte de cobertura por módulo. |
| **Checkstyle / SpotBugs** | Plugins Maven | Análisis estático | Convenciones de nombrado y errores comunes. Exigidos en la sección de CI del README raíz. |
| **Bruno / Postman** | Última estable | Manual | Prueba de endpoints antes de conectar el escritorio. |

---

## 5. Decisiones

**JavaFX sobre Swing.** El README raíz dejaba ambas opciones abiertas. Swing es de 1998 y modernizar su apariencia exige dibujar los componentes a mano. JavaFX admite CSS y, con AtlantaFX, entrega una interfaz moderna con esfuerzo mínimo de diseño, lo cual es determinante en el Kiosko por ser la cara visible del sistema ante el paciente.

**PostgreSQL sobre MongoDB.** El dominio es relacional: pacientes, médicos, citas y horarios mantienen relaciones estrictas, y una cita duplicada u huérfana es inaceptable en un sistema hospitalario. PostgreSQL lo garantiza con transacciones ACID, llaves foráneas y restricciones únicas. Se evaluó usar ambos motores y se descarta: duplica configuración, pruebas y despliegue sin beneficio proporcional. Si más adelante se requiere un módulo de recomendaciones con estructuras de árbol, se resuelve en memoria dentro de la API.

**API REST sobre conexión JDBC directa.** Acordado por votación unánime. La conexión directa obligaría a distribuir las credenciales de la BD junto con el ejecutable y ataría la lógica de negocio a la interfaz.

**Maven sobre Gradle.** Estándar de facto en proyectos Spring Boot, con configuración declarativa más predecible para un equipo trabajando en dos módulos a la vez.

---

## 6. Gestión de versiones

Spring Boot publica un **BOM** que fija versiones compatibles entre sí. Por lo tanto:

- Las dependencias de `/api` marcadas arriba como "Gestionada por el BOM" se declaran **sin etiqueta `<version>`**. Fijarlas a mano rompe la compatibilidad garantizada.
- Las de `/desktop` (JavaFX, AtlantaFX, Ikonli, ControlsFX, Jackson) **sí requieren versión explícita**, porque ese módulo no usa el BOM. Conviene centralizarlas en el bloque `<properties>`.

---

## 7. Puntos a alinear

Detectados al contrastar este documento con el estado actual de `develop`:

1. **Versión de Java inconsistente.** `api/pom.xml` usa Java 21 y `desktop/pom.xml` declara `maven.compiler.release` en 26. Ambos módulos deben compilar con la misma versión; se propone unificar en **21 (LTS)**, que es la que exige Spring Boot 4 y la que figura en el README raíz.
2. **El módulo `/desktop` aún no declara dependencias.** No tiene JavaFX ni Swing, por lo que la decisión de la sección 5 sigue abierta y debe confirmarse antes de construir pantallas.
3. **Paquete base distinto al documentado.** El README raíz indica `com.citasmedicas.api`, pero el proyecto inicializado usa `com.apiclinica`. Conviene unificar el criterio o actualizar el README.
4. **`api/src/main/resources/application.properties` solo define el nombre de la aplicación.** Falta la configuración del *datasource* apuntando al PostgreSQL del `docker-compose`.
5. **springdoc-openapi está en la versión `2.5.0`.** Funciona sobre Spring Boot 4.1.1, pero conviene subirla a `3.1.0`, que es la línea alineada a Spring Boot 4 (ver sección 3).

---

*Versiones verificadas en Maven Central el 31 de agosto de 2026.*
