# Design Document

_(Documento de diseno - Redes sociales: conexiones y canales ampliados)_

## Overview

Dos frentes: (A) BACKEND — ampliar el dominio de `CanalSocial` de 3 a 5 canales (agregar FACEBOOK
y TIKTOK) y relajar los CHECK de las migraciones que acotan `canal`, mediante una migracion NO
destructiva (V63); (B) FRONTEND — construir la vista "Conexiones" (que usa el endpoint YA existente
`POST /social/cuentas-canal`), agregar los dos canales nuevos a etiquetas/iconos y a los filtros de
Publicaciones/Campanas/Analitica, y reorganizar el menu del modulo social.

Hallazgo clave: el backend ya expone crear/consultar/listar cuentas de canal; la publicacion/envio
reales ya estan tras puertos/adaptadores (hoy con implementaciones de registro en log), por lo que
la integracion real queda PREPARADA sin rehacer las vistas (Req 5).

## Architecture

```
BACKEND
  CanalSocial (enum)  ->  + FACEBOOK("facebook"), + TIKTOK("tiktok")
  V63 (migracion)     ->  ALTER de los CHECK canal IN (...) en:
                          cuenta_canal_social, conversacion, plantilla_mensaje,
                          consentimiento_canal (V41), publicacion_social, campana_publicitaria (V43)
                          para incluir 'facebook' y 'tiktok' (no destructivo).
  (opcional) notificacion.canal (V42) NO se toca en este alcance salvo que se requiera.
  Endpoints EXISTENTES reutilizados:
    POST /social/cuentas-canal   (crear conexion)  [perm cuenta_canal_social:crear]
    GET  /social/cuentas-canal   (listar)          [perm cuenta_canal_social:listar]
    GET  /social/cuentas-canal/{id} (consultar)    [perm cuenta_canal_social:leer]

FRONTEND (features/social)
  models/social.models.ts  -> CanalSocial += 'facebook' | 'tiktok'
  social-etiquetas.ts       -> ETIQUETA_CANAL + ICONO_CANAL para los 5
  services/cuentas-canal.service.ts -> + crear(request) (POST)
  conexiones/ (NUEVA vista)  -> lista + alta de Cuenta_Canal_Social
  social.routes.ts           -> + ruta 'conexiones' (primera); redirect '' -> 'conexiones'
  publicaciones/, campanas/, analitica/  -> selectores/filtros con los 5 canales
  shell/menu del modulo social -> orden: Conexiones, Publicaciones, Bandeja, Campanas, Analitica
```

## Components and Interfaces

### Backend

#### `CanalSocial` (enum) — ampliacion
- Agregar `FACEBOOK("facebook")` y `TIKTOK("tiktok")`. `desdeValorBd`/`valorBd` siguen igual
  (iteran `values()`), por lo que soportan los nuevos sin cambios de logica. Actualizar el Javadoc
  (ya no son "solo los tres canales de Meta"; TikTok no es de Meta).

#### V63 migracion — relajar CHECK de canal (no destructiva)
- Para cada tabla con `CHECK (canal IN ('whatsapp','messenger','instagram'))` de V41/V43:
  `ALTER TABLE <t> DROP CONSTRAINT <ck_...>;` seguido de
  `ALTER TABLE <t> ADD CONSTRAINT <ck_...> CHECK (canal IN ('whatsapp','facebook','instagram','messenger','tiktok'));`
  respetando el `canal IS NULL OR ...` donde aplica (campana_publicitaria).
- Tablas: cuenta_canal_social, conversacion, plantilla_mensaje, consentimiento_canal (V41),
  publicacion_social, campana_publicitaria (V43). Idempotencia razonable (los nombres de
  constraint se conservan). Encabezado comentado como en V5/V47/V62. Verificar los nombres EXACTOS
  de cada constraint leyendo V41/V43.
- `CrearCuentaCanalSocialRequest.canal` es `@Size(max=12)`: 'facebook'(8)/'tiktok'(6) caben; SIN
  cambios de DTO.

#### Integracion real (Req 5) — preparada
- La publicacion/envio ya pasan por puertos/adaptadores (implementaciones de registro en log por
  ahora). Se documenta el punto de cableado y las credenciales por plataforma (Meta Graph API para
  WhatsApp/Facebook/Instagram/Messenger; TikTok Content Posting API). No se implementa el proveedor
  real en este alcance.

### Frontend

#### Modelos y etiquetas
- `CanalSocial` (type) += `'facebook' | 'tiktok'`.
- `ETIQUETA_CANAL`: agregar `facebook: 'Facebook'`, `tiktok: 'TikTok'`.
- `ICONO_CANAL`: iconos de Material Symbols aproximados (p. ej. facebook -> 'thumb_up' o 'public';
  tiktok -> 'music_note'; el icono se acompana SIEMPRE de la etiqueta textual, el color no es el
  unico portador, WCAG AA).

#### `CuentasCanalService` — metodo crear
- `crear(request: CrearCuentaCanalSocialRequest): Observable<CuentaCanalSocial>` -> `POST
  /social/cuentas-canal`. Interface `CrearCuentaCanalSocialRequest` = { canal, identificadorExterno,
  nombre, credencialesRef }.

#### Vista `Conexiones` (nueva) — features/social/conexiones/
- Lista las cuentas (GET listar) con columnas: Canal (icono + etiqueta), Nombre, Identificador,
  Estado (activa/inactiva) usando data-table/estado compartidos.
- Boton "Conectar cuenta" abre formulario (inline o dialog) con: Canal (select de los 5), Nombre,
  Identificador externo, Referencia de credencial (texto; ayuda: "Nombre del secreto/credencial,
  no la credencial en si"). Envia POST; 201 recarga la lista; 409 muestra mensaje de conflicto.
- Estados carga/vacio/error (state-container), responsivo, WCAG AA, espanol, sin UUIDs.
- Guardas: modulo `redes-sociales` + permiso `cuenta_canal_social:listar` (ver) / `crear` (alta).

#### Rutas y menu
- `social.routes.ts`: agregar `{ path: 'conexiones', canActivate:[guardaPorPermiso('cuenta_canal_social','listar')], loadComponent: ... }`;
  cambiar el redirect por defecto de 'bandeja' a 'conexiones'.
- El menu lateral del ambito empresa (donde se listan las entradas del modulo social) debe incluir
  "Conexiones" como primera entrada del grupo social. Localizar la definicion del menu y agregarla
  respetando el gating por permiso/modulo.

#### Publicaciones / Campanas / Analitica
- Sustituir las listas de canales embebidas (hoy 3) por los 5, idealmente derivandolas de un unico
  origen (ETIQUETA_CANAL) para no volver a divergir. Anadir 'facebook' y 'tiktok' a los arreglos de
  filtro/select. El alta de Publicacion sigue exigiendo cuenta conectada.
- Estados vacios: cuando no hay cuentas/ް datos, el mensaje sugiere ir a "Conexiones".

## Data Models

Sin tablas nuevas. Se relajan CHECK de `canal` para admitir 'facebook' y 'tiktok'. `CuentaCanalSocial`
y demas entidades ya existen (V41/V43). El DTO de salida no cambia (sigue sin exponer credenciales).

## Error Handling

- Alta duplicada (mismo canal+identificador): backend responde 409; la vista muestra un mensaje
  claro y no duplica.
- Datos invalidos (422): se muestran los errores de validacion por campo.
- Sin proveedor real cableado: publicar/enviar degrada de forma controlada (log) sin romper el
  flujo (comportamiento existente).

## Testing Strategy

### Backend
- `CanalSocialTest`: `desdeValorBd`/`valorBd` resuelven 'facebook' y 'tiktok'; los 5 round-trip.
- Prueba de que crear cuenta con canal 'facebook'/'tiktok' funciona (servicio/controlador slice).
- La migracion V63 aplica limpiamente (los tests de contexto/Flyway con Testcontainers levantan el
  esquema V63); insertar una cuenta 'tiktok' ya no viola el CHECK.
- No regresion: pruebas sociales existentes (publicaciones, campanas, analitica, bandeja) verdes.

### Frontend
- `cuentas-canal.service.spec.ts`: `crear` hace POST a /social/cuentas-canal con el cuerpo correcto.
- `conexiones.spec.ts` (nuevo): lista (estados carga/vacio/error), alta exitosa recarga, 409 muestra
  conflicto, no muestra credenciales/UUIDs, axe WCAG.
- Etiquetas: los 5 canales aparecen en los selectores/filtros de publicaciones/campanas/analitica.
- Specs existentes del modulo social permanecen verdes.

## Verification

- Backend: `mvn -o package -DskipTests` (0 errores) + `mvn -o test` (verde); relanzar con V63 y
  confirmar arranque + Flyway aplica V63.
- Frontend: `ng build` (0 errores) + `ng test` (verde; reintentar axe flaky en aislamiento).
- Vivo: entrar a Social -> Conexiones, conectar una cuenta (p. ej. Facebook) y verla en la lista;
  luego en Publicaciones/Campanas/Analitica ver los 5 canales.

## Correctness Properties

### Property 1: Canales ampliados sin regresion - `CanalSocial.desdeValorBd`/`valorBd` resuelven los
  cinco canales (whatsapp, facebook, instagram, messenger, tiktok) en ambos sentidos, y los tres
  previos siguen resolviendo igual.

**Validates: Requirements 1.1, 1.2, 1.5**

### Property 2: CHECK admite los cinco - tras V63, insertar una fila con canal 'facebook' o 'tiktok'
  en cuenta_canal_social/publicacion_social/campana_publicitaria NO viola ningun CHECK; un canal
  desconocido si lo viola.

**Validates: Requirements 1.1, 1.5**

### Property 3: Alta de conexion segura - el alta envia canal/nombre/identificador/referencia de
  credencial a POST /social/cuentas-canal y NUNCA transmite ni muestra la credencial en claro; un
  duplicado (canal+identificador) produce 409 y no crea una segunda cuenta.

**Validates: Requirements 2.2, 2.3, 2.4**

### Property 4: Cinco canales en toda la UI social - los selectores/filtros de Conexiones,
  Publicaciones, Campanas y Analitica ofrecen exactamente los cinco canales con etiqueta legible
  (el color no es el unico portador de significado).

**Validates: Requirements 1.3, 1.4, 4.1, 4.2, 4.3**