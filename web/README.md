# MediCitas Anglo · Backoffice Web

Aplicación web del personal de la clínica (Admisión, Médico y Administración). Es una SPA en **React 19 + Vite + TypeScript + Tailwind CSS v4 + AlignUI** que consume la API REST de `/api` con un token JWT. La decisión de tecnología y la arquitectura están en [`docs/web-frontend-decision.md`](../docs/web-frontend-decision.md).

## Requisitos

- **Node 22 o superior** y npm 10+ (`node --version`).
- La API corriendo en `http://localhost:8080/api/v1` (`./scripts/run-api.sh` desde la raíz del repositorio).

## Comandos

```bash
npm install          # instala dependencias (usa .npmrc con legacy-peer-deps)
npm run dev          # servidor de desarrollo en http://localhost:5173
npm run build        # verificación de tipos + compilación a dist/
npm run preview      # sirve dist/ para probar la compilación
npm run lint         # oxlint
npm run typecheck    # tsc --noEmit -p tsconfig.app.json
```

Desde la raíz del repositorio: `./scripts/run-web.sh` (o `scripts\run-web.bat`) instala las dependencias si faltan y arranca el servidor de desarrollo.

## Variables de entorno

Copia `.env.example` a `.env` y ajusta lo necesario (Vite solo expone variables con prefijo `VITE_`):

| Variable | Por defecto | Uso |
| :--- | :--- | :--- |
| `VITE_API_URL` | `http://localhost:8080/api/v1` | URL base de la API (sin barra final). |
| `VITE_IDLE_MINUTES` | `30` | Minutos de inactividad antes de cerrar la sesión (RNF-10). |

## Credenciales de demostración

Con la API en perfil `h2` (el de `run-api.sh`) existen estos usuarios:

| Usuario | Contraseña | Rol | Pantalla inicial |
| :--- | :--- | :--- | :--- |
| `admin` | `admin123` | Administrador | `/administracion/usuarios` (también ve Ventanilla) |
| `admision` | `admision123` | Admisión | `/ventanilla` |
| `dr.benavides` | `doctor123` | Médico (Carla Benavides, Cardiología) | `/agenda` |

Hay un usuario por cada médico demo: `dr.<primer apellido sin tildes>` (`dr.villanueva`, `dr.huaman`, `dr.galvez`...). Pacientes de prueba: DNI **45871236** (Lucía Paredes, con seguro) y **70123456** (Diego Salazar, particular).

## Estructura

```text
web/
├── index.html
├── components.json            # registro @alignui para `npx shadcn add`
├── src/
│   ├── main.tsx               # QueryClientProvider + AuthProvider + Tooltip.Provider + Router + Toaster
│   ├── styles/globals.css     # Tailwind v4 + tokens AlignUI + fuente Bricolage Grotesque
│   ├── app/                   # router, AppShell (sidebar + contenido), navegación por rol, páginas genéricas
│   │   ├── router.tsx         # createBrowserRouter; importa las rutas de cada feature
│   │   ├── app-shell.tsx      # layout autenticado (sidebar fija, contenido con scroll, autologout)
│   │   ├── sidebar.tsx        # logo, grupos de menú según rol, tarjeta de usuario con "Cerrar sesión"
│   │   ├── navigation.ts      # NAV_GROUPS: ítems de menú por rol (RNF-17)
│   │   └── pages/             # forbidden (403), not-found (404), under-construction, route-error
│   ├── components/
│   │   ├── ui/                # componentes oficiales AlignUI (instalados con shadcn, no editar a mano)
│   │   ├── page-header.tsx    # PageHeader: título, descripción, acciones; fija el título de la pestaña
│   │   ├── card.tsx           # Card y CardHeader (tarjeta blanca estándar)
│   │   ├── data-table.tsx     # DataTable sobre @tanstack/react-table + Table AlignUI (carga, vacío, error, paginación)
│   │   ├── form-field.tsx     # FormField, TextField, SelectField, TextareaField (Label + control + Hint)
│   │   ├── confirm-modal.tsx  # ConfirmModal (modal-demo) con estados success/error/warning/information
│   │   ├── empty-state.tsx    # EmptyState con icono
│   │   ├── loading-state.tsx  # Skeleton, LoadingState, LoadingPage
│   │   ├── appointment-status-badge.tsx  # AppointmentStatusBadge y SlotStatusBadge
│   │   └── notify.tsx         # notify.success/error/warning/info/fromError (toast AlignUI)
│   ├── features/
│   │   ├── auth/              # AuthProvider, useAuth/useCurrentUser, LoginPage, RequireAuth, RequireRole, IdleLogout, roles
│   │   ├── admission/routes.tsx   # rutas de Ventanilla  (/ventanilla/**)      FRONT_DESK y ADMIN
│   │   ├── doctor/routes.tsx      # rutas del Médico     (/agenda)             DOCTOR
│   │   └── admin/routes.tsx       # rutas de Administración (/administracion/**) ADMIN
│   └── lib/
│       ├── api/               # cliente tipado: client.ts (fetch + ApiError), types.ts, auth, catalog, doctor, front-desk, admin, reports
│       ├── session.ts         # sessionStore: token + usuario en memoria y sessionStorage
│       ├── query.ts           # QueryClient de TanStack Query
│       ├── formatters.ts      # fechas/horas en español, documento, moneda, tasas, roles, etiquetas de estado
│       └── cn.ts, tv.ts, ...  # utilidades de AlignUI
└── dist/                      # salida de `npm run build` (no se versiona)
```

### Cada feature vive en su carpeta

Las vistas de cada rol se desarrollan dentro de `src/features/<rol>/` (`admission`, `doctor`, `admin`). Cada carpeta exporta desde `routes.tsx` un arreglo de `RouteObject` que `src/app/router.tsx` incorpora al árbol de rutas. Hoy cada ruta planificada muestra una página "En construcción" (`UnderConstructionPage`); **el siguiente paso reemplaza esos placeholders por las vistas reales**, sin tocar el router ni el cliente de la API.

Convenciones:

- Identificadores y nombres de archivo en inglés; **todo texto visible en español, claro y sin tecnicismos** (RNF-16). Los mensajes de error de la API ya vienen en español: se muestran tal cual (`ApiError.message`, `notify.fromError(error)`). Los fallos de red usan "No pudimos conectar con el servidor…".
- Datos de servidor con TanStack Query (`useQuery` / `useMutation`) y las funciones de `@/lib/api`.
- Solo tokens de diseño de AlignUI (`bg-bg-white-0`, `text-text-sub-600`, `ring-stroke-soft-200`, `text-label-sm`, `rounded-20`…); nunca colores crudos de Tailwind.
- Rutas en español y kebab-case (`/ventanilla/pacientes`, `/administracion/horarios`).

## Sesión y seguridad

- El login (`POST /auth/login`) devuelve un JWT que se guarda en memoria y en `sessionStorage` (sobrevive a recargas, se pierde al cerrar la pestaña).
- Cada petición lleva `Authorization: Bearer <token>`. Un `401` cierra la sesión y redirige a `/login` con el aviso "Tu sesión expiró. Vuelve a iniciar sesión."; un `403 USER_INACTIVE` hace lo mismo con su propio aviso.
- Cierre automático por inactividad a los 30 minutos con aviso "¿Sigues ahí?" un minuto antes (RNF-10), y al vencer el token.
- `RequireAuth` protege todo el área autenticada; `RequireRole` muestra un 403 amigable cuando el rol no corresponde (RNF-17). Tras iniciar sesión cada rol va a su módulo: Admisión → `/ventanilla`, Médico → `/agenda`, Administrador → `/administracion/usuarios`.

## Cómo agregar componentes AlignUI

Los componentes se instalan desde el registro oficial con shadcn y quedan en `src/components/ui/`:

```bash
npx -y shadcn@latest add @alignui/<componente> --yes      # p. ej. @alignui/command-menu
npx -y shadcn@latest add @alignui/<ejemplo> --yes         # instala un ejemplo con sus componentes
```

Notas:

- `.npmrc` tiene `legacy-peer-deps=true` (react-day-picker 8 declara React 18 como peer); no lo quites.
- Los componentes se usan como namespaces: `import * as Button from '@/components/ui/button'` y luego `<Button.Root variant='primary' size='small'><Button.Icon as={RiAddLine} />Nueva</Button.Root>`. Iconos de `@remixicon/react` vía la prop `as`.
- El backoffice es solo en modo claro: `src/lib/alignui.css` no incluye el bloque `@media (prefers-color-scheme: dark)`. Si vuelves a instalar `@alignui/base` con `--overwrite`, elimínalo de nuevo.
- Catálogo de ejemplos y tokens: consulta el MCP de AlignUI (`alignui_get_example`, `alignui_get_component`, `alignui_get_design_tokens`).

## Despliegue

`npm run build` genera un sitio estático en `dist/` que puede servirse con Nginx (o cualquier servidor de archivos) o copiarse como recursos estáticos de la API. Al ser una SPA, el servidor debe devolver `index.html` para cualquier ruta desconocida (`try_files $uri /index.html;` en Nginx). La URL de la API se fija en tiempo de compilación con `VITE_API_URL`, y el origen del sitio debe estar permitido en `SECURITY_CORS_ALLOWED_ORIGINS` de la API.
