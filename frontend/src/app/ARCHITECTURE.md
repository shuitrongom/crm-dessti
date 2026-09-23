# Estructura del frontend

Frontend Angular (zoneless, standalone, signals-first) con Angular Material y el
Sistema de Diseño propio. La aplicacion se organiza por ambitos de acceso
(plataforma / empresa / portal), autenticacion basada en JWT y autorizacion
deny-by-default (Req 1, 3, 68).

```
src/
├── app/
│   ├── core/                     # Servicios singleton, auth, guardas, navegacion, layout
│   │   ├── auth/
│   │   │   ├── auth.models.ts       # TokenResponse, ClaimsToken, roles, Ambito
│   │   │   ├── jwt.util.ts          # Decodificacion de claims (sin verificar firma)
│   │   │   ├── auth.service.ts      # Sesion como signals + login/refresh/logout
│   │   │   └── auth.guard.ts        # Guardas deny-by-default (rol/permiso/ambito)
│   │   ├── navigation/navigation.ts # Menu dinamico segun permisos/roles
│   │   ├── layout/shell-layout/     # Shell: barra + drawer responsive + menu usuario
│   │   ├── interceptors/            # apiInterceptor: Bearer + refresh-on-401
│   │   ├── services/                # ApiConfigService, ThemeService, error-mensajes
│   │   └── models/                  # PaginaResponse (contrato de paginacion)
│   ├── shared/                   # Componentes/servicios reutilizables del Sistema de Diseño
│   │   ├── components/              # PageHeader, StateContainer, DataTable, ConfirmDialog,
│   │   │                            #   ProgressBadge, IndicatorCard
│   │   ├── services/                # NotificacionesService (toast)
│   │   └── models/                  # EstadoSolicitud (cargando/ok/vacio/error)
│   ├── features/
│   │   ├── auth/login/              # Login (formulario reactivo accesible)
│   │   ├── acceso-denegado/         # Vista 403 (deny-by-default)
│   │   ├── empresa/                 # Ambito empresa (50.2 + 50.4)
│   │   │   ├── home/                   # Pagina principal (branding, esencia, objetivos, tablero)
│   │   │   ├── services/               # branding / estrategia / tablero
│   │   │   ├── administracion/         # Usuarios, roles, sesiones, branding, config, estrategia, presupuestos
│   │   │   └── empresa.routes.ts       # ShellLayout + hijos (punto de extension bloque 51)
│   │   ├── plataforma/              # Ambito super_admin (50.3): empresas, planes/suscripciones, offboarding
│   │   └── portal/                  # Ambito cliente_portal (landing; bloque 51.3 anade vistas)
│   ├── app.config.ts             # Providers raiz (router + input binding, HttpClient + interceptor, animaciones)
│   ├── app.routes.ts             # Enrutamiento por ambito con guardas
│   └── app.*                     # Contenedor raiz (router-outlet de nivel superior)
├── environments/                 # apiBaseUrl -> /api/v1
└── styles/                       # Sistema de Diseño: _tokens.scss, _breakpoints.scss, _material-theme.scss
```

## Autenticacion y sesion (Req 1, 68)

- `AuthService` mantiene la sesion como **signals**: `accessToken`, `refreshToken`,
  `claims` (derivados), `identificador`, `tenantId`, `roles`, `permisos`,
  `isAuthenticated` y `ambito`.
- Los claims del Token_Acceso (JWT) se **decodifican en cliente sin verificar la
  firma** (la verificacion la hace el servidor) solo para componer navegacion y
  guardas. El acceso efectivo lo reimpone el backend en cada peticion.
- Los tokens se persisten en **sessionStorage** (no localStorage) por seguridad:
  la sesion no sobrevive al cierre del navegador.
- `apiInterceptor` adjunta `Authorization: Bearer` a las peticiones autenticadas
  (todas salvo `/auth/*`) y ante un 401 intenta **una** renovacion
  (refresh-and-retry); si falla, limpia la sesion y navega a `/login`.

## Autorizacion deny-by-default (Req 3)

- Helpers: `tienePermiso(recurso, operacion)`, `tieneRol`, `tieneAlgunRol`,
  `tieneAlgunPermiso`.
- Guardas funcionales: `authGuard`, `guardaPorRoles`, `guardaPorPermiso`,
  `guardaPlataforma`, `guardaEmpresa`, `guardaAdminEmpresa`, `guardaPortal`,
  `guardaRaiz`.
- El menu de navegacion (`NavigationService`) se compone **dinamicamente**: solo
  se muestran los enlaces para los que el Usuario esta autorizado.
- No autenticado -> `/login`; autenticado sin permiso -> `/acceso-denegado`.

## Enrutamiento por ambito

```
/login                      publico
/plataforma/**              super_admin  -> empresas, planes-suscripciones, offboarding
/empresa/inicio             empresa      -> pagina principal (50.2)
/empresa/administracion/**  admin_empresa-> usuarios, roles, sesiones, branding, configuracion, estrategia, presupuestos (50.4)
/portal/**                  cliente_portal-> landing (bloque 51.3)
/acceso-denegado            vista 403
```

Cada ambito se carga de forma **diferida (lazy)** para mantener reducido el bundle
inicial. Las vistas de negocio del bloque 51 se anaden como rutas **hijas** del
ambito correspondiente (ver el punto de extension documentado en
`empresa.routes.ts` y `portal.routes.ts`) sin reestructurar el shell.

## Contrato de backend integrado (path -> interfaz TS)

| Endpoint backend | Interfaz/servicio frontend |
| --- | --- |
| `POST /auth/login`, `/auth/refresh`, `/auth/logout` | `AuthService` (TokenResponse) |
| `GET/PUT /empresa/branding` | `BrandingService` (Branding) |
| `GET/PUT /estrategia/esencia`, `GET/POST /estrategia/objetivos` | `EstrategiaService` |
| `GET /reportes-bi/tablero` | `TableroService` (Tablero) |
| `GET /empresas`, `POST /empresas`, `/empresas/{id}/activar|suspender` | `EmpresasService` |
| `POST /empresas/{id}/offboarding/{exportar|cancelar|eliminar}` | `EmpresasService` |
| `GET/POST /planes`, `PUT /planes/{id}` | `PlanesService` |
| `GET/POST /suscripciones`, `/suscripciones/{id}/{activar|suspender|cancelar}` | `PlanesService` |
| `POST /usuarios`, `/usuarios/{id}/desactivar`, `PUT /usuarios/{id}/roles` | `UsuariosService` |
| `GET /usuarios/{id}/sesiones`, `POST .../sesiones/revocar` | `SesionesService` |
| `GET /presupuestos` | `PresupuestosService` |

### Puntos de integracion pendientes (marcados y sin endpoints inventados)

- **Listado de Usuarios**: el backend aun no expone `GET /usuarios`. La vista de
  Usuarios opera por identificador (alta, desactivacion, reasignacion de roles) y
  conectara el listado cuando exista (`UsuariosService`).
- **Roles personalizados (REST)**: el backend gestiona roles por `ServicioRoles`
  pero no publica un controlador REST de roles. La vista de Roles muestra los
  roles/permisos efectivos del Usuario y habilitara la edicion cuando exista.

## Pruebas (Vitest, zoneless TestBed)

Especificaciones unitarias deterministas del nucleo de acceso (builder
`@angular/build:unit-test`):

- `core/auth/jwt.util.spec.ts` — decodificacion de claims y expiracion.
- `core/auth/auth.service.spec.ts` — derivacion de signals (identificador,
  tenant, roles, permisos, ambito, isAuthenticated), helpers deny-by-default,
  manejo de tokens en login/refresh/logout, persistencia y restauracion.
- `core/auth/auth.guard.spec.ts` — guardas deny-by-default: redireccion a
  `/login` sin sesion y a `/acceso-denegado` sin rol/permiso; `guardaRaiz` por
  ambito.
- `core/interceptors/api.interceptor.spec.ts` — Bearer en peticiones
  autenticadas, exclusion de `/auth/*`, refresh-and-retry unico ante 401 y
  limpieza + redireccion cuando el refresco falla.

Las pruebas de sesion (`auth.service.spec.ts`) usan JWT sin firmar generados por
`core/auth/jwt.test-util.ts` y `provideHttpClientTesting`; las del interceptor
inyectan un `AuthService`/`Router` simulados sobre el pipeline real de HttpClient.

## Sistema de Diseño y accesibilidad

- Tokens CSS (`--ds-*`), tema claro/oscuro via `data-theme` (`ThemeService`),
  breakpoints mobile-first (`bp.from('md')`), tema de Angular Material coherente.
- Accesibilidad (Req 57): idioma `es`, foco visible, skip-link, landmarks
  (header/nav/main), reduccion de movimiento, color nunca como unico portador de
  significado (insignias y tendencias acompanadas de texto).
```
