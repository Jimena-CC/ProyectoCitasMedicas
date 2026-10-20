# Decisión técnica: frontend web del Backoffice

> Issue #47. Define cómo se construye la aplicación web del personal (Admisión, Médico y Administración) que consume la API de la Fase 2.

## 1. Opciones evaluadas

| Criterio | Thymeleaf (vistas renderizadas por Spring) | Aplicación separada (SPA) |
| :--- | :--- | :--- |
| Acoplamiento con la API | Alto: las vistas viven dentro del proyecto Spring y acceden a los servicios directamente; la API REST dejaría de ser la única puerta de entrada. | Bajo: el navegador es un cliente más de la API, igual que el kiosko JavaFX. |
| Reutilización del contrato REST | Parcial: habría que mantener controladores MVC además de los REST. | Total: usa los mismos endpoints `/api/v1` ya documentados y probados. |
| Seguridad | Sesión de servidor con cookies; obligaría a convivir dos esquemas (JWT para el kiosko, sesión para la web). | Un solo esquema: JWT Bearer, ya implementado en `#44`, con CORS configurado para `http://localhost:5173`. |
| Interactividad (tablas, modales, filtros, autologout) | Requiere JavaScript adicional sobre el HTML generado. | Nativa en React; componentes listos (AlignUI). |
| Sistema de diseño | Habría que escribir CSS propio o adaptar Bootstrap. | AlignUI sobre Tailwind v4: componentes accesibles y consistentes, instalables con shadcn. |
| Compilación y CI | Dentro del `mvn package` de la API; un cambio de vista recompila el backend. | Proyecto independiente con su propio flujo de CI (`web-ci.yml`), sin tocar la API. |
| Despliegue | Un solo artefacto. | Build estático en `dist/`, servible por Nginx o como recursos estáticos de la API. |
| Curva de aprendizaje del equipo | Java + plantillas. | TypeScript + React (conocimiento transferible fuera del proyecto). |

## 2. Decisión

**Aplicación separada (SPA) en `web/`** con:

- **React 19 + Vite + TypeScript** (plantilla `react-ts`, lint con oxlint).
- **Tailwind CSS v4 + AlignUI** vía el registro de shadcn (`npx shadcn add @alignui/<componente>`), con la fuente Inter.
- **React Router 7** (modo declarativo con `createBrowserRouter`) y **TanStack Query** para el estado de servidor.
- Consumo de la API con `fetch` y **JWT Bearer**.

Razones:

1. **Separación de responsabilidades**: la API sigue siendo la única dueña de la lógica de negocio y de la seguridad; el frontend solo presenta y valida en primera línea.
2. **El kiosko ya es un cliente de la API**: añadir un segundo cliente HTTP/REST mantiene una sola arquitectura y un solo contrato (`docs/api-contract.md`).
3. **CORS y JWT ya están configurados** en `#44` pensando en este escenario (`security.cors.allowed-origins`, cabeceras `Authorization` e `Idempotency-Key`).
4. **Sistema de diseño**: AlignUI entrega tablas, formularios, modales, badges de estado, toasts y paginación con un acabado profesional sin diseñar desde cero, igual que AtlantaFX en el kiosko.
5. **CI independiente**: `npm run lint && npm run typecheck && npm run build` corre solo cuando cambia `web/**`, sin alargar el pipeline de la API.

## 3. Arquitectura

```
+--------------------+   HTTP / REST (JSON + JWT)   +------------------------+    JDBC    +------------+
|  Navegador         | ---------------------------> |      API Backend       | ---------> | PostgreSQL |
|  web/ (SPA React)  | <--------------------------- |  (Spring Boot /api/v1) | <--------- |            |
+--------------------+                              +------------------------+            +------------+
        ^                                                        ^
        | servido como                                           | HTTP / REST (JSON)
        | estático (Nginx / dist/)                      +------------------------+
                                                       |  Kiosko (JavaFX)       |
                                                       +------------------------+
```

- El navegador descarga el sitio estático (`dist/`) y a partir de ahí solo habla con `/api/v1`.
- Personal → `POST /auth/login` → JWT → cada petición lleva `Authorization: Bearer <token>`.
- Los módulos de la API se mapean uno a uno con las carpetas de `src/features/`: `/front-desk/**` → `admission`, `/doctor/**` → `doctor`, `/admin/**` → `admin`.

## 4. Estructura de carpetas

```text
web/
├── src/
│   ├── app/            router, AppShell (sidebar + contenido), navegación por rol, páginas 403/404
│   ├── components/     PageHeader, DataTable, FormField, ConfirmModal, EmptyState, LoadingState, notify…
│   │   └── ui/         componentes oficiales AlignUI (generados por shadcn)
│   ├── features/
│   │   ├── auth/       AuthProvider, LoginPage, RequireAuth, RequireRole, IdleLogout
│   │   ├── admission/  vistas de Ventanilla (routes.tsx)
│   │   ├── doctor/     vistas del Médico (routes.tsx)
│   │   └── admin/      vistas de Administración (routes.tsx)
│   └── lib/
│       ├── api/        cliente tipado por módulo (auth, catalog, doctor, front-desk, admin, reports) + types.ts
│       ├── session.ts  almacén de la sesión (memoria + sessionStorage)
│       ├── query.ts    QueryClient
│       └── formatters.ts
├── .env.example
└── README.md
```

Detalle de cada archivo en [`web/README.md`](../web/README.md).

## 5. Ejecución

```bash
./scripts/run-api.sh     # API con datos demo en http://localhost:8080/api/v1
./scripts/run-web.sh     # instala dependencias si faltan y abre http://localhost:5173
```

Requiere Node 22+. Credenciales demo: `admin/admin123`, `admision/admision123`, `dr.benavides/doctor123`.

## 6. Variables de entorno

| Variable | Por defecto | Descripción |
| :--- | :--- | :--- |
| `VITE_API_URL` | `http://localhost:8080/api/v1` | URL base de la API. Se fija en tiempo de compilación. |
| `VITE_IDLE_MINUTES` | `30` | Minutos de inactividad antes del cierre automático de sesión. |

## 7. Seguridad

- **Token en memoria + `sessionStorage`**: sobrevive a una recarga de la pestaña pero no a cerrarla, adecuado para equipos compartidos de ventanilla. No se usa `localStorage` ni cookies.
- **Autologout** (RNF-10): a los 29 minutos sin actividad se muestra el aviso "¿Sigues ahí?" con cuenta regresiva y a los 30 se cierra la sesión; además la sesión se cierra al vencer el token (`expiresAt` del login, máximo 8 h).
- **401 de la API** → se descarta la sesión y se vuelve a `/login` con el aviso "Tu sesión expiró. Vuelve a iniciar sesión."; `403 USER_INACTIVE` hace lo mismo explicando que el usuario fue desactivado.
- **Rutas por rol** (RNF-17): `RequireAuth` protege el área interna y `RequireRole` limita cada módulo (`/ventanilla/**` a Admisión y Administrador, `/agenda` a Médico, `/administracion/**` a Administrador) mostrando un 403 amigable. El menú solo lista los módulos del rol y, tras el login, cada rol llega a su pantalla inicial.
- **Mensajes** (RNF-16): la API ya responde en español; el frontend los muestra tal cual y traduce únicamente los fallos de red.

## 8. Despliegue

`npm run build` produce un sitio estático en `web/dist/` (HTML, JS y CSS con hash). Opciones:

1. **Nginx** (o cualquier servidor de archivos) sirviendo `dist/` con `try_files $uri /index.html;` para que las rutas de la SPA funcionen al recargar; la API en otro origen con su CORS (`SECURITY_CORS_ALLOWED_ORIGINS`).
2. **Recursos estáticos de la API**: copiar `dist/` a `api/src/main/resources/static/` y servir todo desde Spring Boot (un solo origen, sin CORS). Requiere un controlador de respaldo que devuelva `index.html` para rutas no API.

En ambos casos `VITE_API_URL` debe apuntar a la URL pública de la API en el momento de compilar.
