# Implementation Plan

## Overview

Ampliar el modulo social a cinco redes (WhatsApp, Facebook, Instagram, Messenger, TikTok), construir
la pantalla de Conexiones (reutiliza endpoints ya existentes), reorganizar el menu y extender los
filtros por canal en Publicaciones/Campanas/Analitica. Sin tablas nuevas; una migracion no
destructiva relaja los CHECK de `canal`.

## Tasks

- [ ] 1. Backend: ampliar canales a cinco
- [ ] 1.1 Agregar FACEBOOK y TIKTOK al enum `CanalSocial`
  - Nuevos valores `FACEBOOK("facebook")`, `TIKTOK("tiktok")`; actualizar Javadoc (TikTok no es Meta). `valorBd`/`desdeValorBd` sin cambios de logica.
  - _Requirements: 1.1, 1.2_
- [ ] 1.2 Migracion V63: relajar los CHECK de `canal` (no destructiva)
  - DROP/ADD del CHECK en cuenta_canal_social, conversacion, plantilla_mensaje, consentimiento_canal (V41) y publicacion_social, campana_publicitaria (V43) para incluir 'facebook' y 'tiktok' (respetar `canal IS NULL OR ...` en campana). Leer V41/V43 para los nombres EXACTOS de constraint. Encabezado comentado.
  - _Requirements: 1.1, 1.5_
- [ ] 1.3 Pruebas backend de canales
  - `CanalSocialTest`: round-trip de los cinco; crear cuenta con 'facebook'/'tiktok' via servicio/controlador; V63 aplica y permite insertar 'tiktok'. No regresion de las pruebas sociales existentes.
  - _Requirements: 1.1, 1.2, 1.5, 6.2_

- [ ] 2. Frontend: modelos, etiquetas y servicio de cuentas
- [ ] 2.1 Ampliar `CanalSocial` (type) y `ETIQUETA_CANAL`/`ICONO_CANAL` a cinco
  - `'facebook' | 'tiktok'` en el type; etiquetas "Facebook"/"TikTok"; iconos de Material Symbols; el color no es el unico portador (WCAG AA).
  - _Requirements: 1.3, 1.4_
- [ ] 2.2 `CuentasCanalService.crear(request)` (POST /social/cuentas-canal)
  - Interface `CrearCuentaCanalSocialRequest` { canal, identificadorExterno, nombre, credencialesRef }; metodo crear que hace POST y devuelve la cuenta creada.
  - _Requirements: 2.2, 2.3_
- [ ] 2.3 Prueba del servicio
  - `crear` hace POST con el cuerpo correcto; el listado ya cubierto se mantiene.
  - _Requirements: 2.2_

- [ ] 3. Frontend: vista Conexiones
- [ ] 3.1 Componente `conexiones` (lista + alta)
  - Lista de cuentas (Canal icono+etiqueta, Nombre, Identificador, Estado) con state-container; boton "Conectar cuenta" con formulario (canal de los 5, nombre, identificador, referencia de credencial con ayuda "no es la credencial"); POST -> 201 recarga; 409 muestra conflicto. Sin UUIDs ni credenciales. Responsivo, WCAG AA, espanol.
  - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.5_
- [ ] 3.2 Ruta y menu
  - `social.routes.ts`: agregar 'conexiones' (guarda `cuenta_canal_social:listar`) y cambiar redirect '' -> 'conexiones'. Agregar "Conexiones" como primera entrada del grupo social en el menu, respetando gating por modulo/permiso.
  - _Requirements: 2.6, 3.1, 3.3_
- [ ] 3.3 Prueba de la vista Conexiones
  - Lista (carga/vacio/error), alta exitosa recarga, 409 conflicto, no muestra credenciales/UUIDs, axe WCAG.
  - _Requirements: 2.1, 2.4, 2.5_

- [ ] 4. Frontend: cinco canales en Publicaciones, Campanas, Analitica y estados vacios
- [ ] 4.1 Selectores/filtros a cinco canales (origen unico)
  - Derivar las opciones de canal de un unico origen (ETIQUETA_CANAL) en publicaciones, campanas y analitica; incluir facebook y tiktok. El alta de Publicacion sigue exigiendo cuenta conectada.
  - _Requirements: 4.1, 4.2, 4.3, 4.4_
- [ ] 4.2 Estados vacios que guian a Conexiones
  - Cuando no hay cuentas/datos, el mensaje sugiere ir a "Conexiones" (CTA).
  - _Requirements: 3.2_
- [ ] 4.3 Pruebas de UI ampliada
  - Los cinco canales aparecen en los selectores/filtros; specs sociales existentes verdes.
  - _Requirements: 4.1, 4.2, 4.3, 6.2_

- [ ] 5. Verificacion integral y puesta en vivo
- [ ] 5.1 Build + pruebas backend
  - `mvn -o package -DskipTests` (0 errores) y `mvn -o test` (verde). Relanzar con V63 y confirmar arranque + Flyway aplica V63.
  - _Requirements: 6.1, 6.2_
- [ ] 5.2 Build + pruebas frontend
  - `ng build` (0 errores) y `ng test` (verde; reintentar axe flaky en aislamiento).
  - _Requirements: 6.1, 6.2, 6.3_
- [ ] 5.3 Verificacion viva
  - Social -> Conexiones: conectar una cuenta (p. ej. Facebook) y verla listada; luego confirmar los cinco canales en Publicaciones/Campanas/Analitica.
  - _Requirements: 1.1, 2.1, 4.1_

## Task Dependency Graph

```
1.1 -> 1.2 -> 1.3
2.1 -> 2.2 -> 2.3
2.1, 2.2 -> 3.1 -> 3.2 -> 3.3
2.1 -> 4.1 -> 4.2 -> 4.3
(1.x backend y 2-4 frontend en paralelo)
todo -> 5.1, 5.2 -> 5.3
```

```json
{
  "waves": [
    { "wave": 1, "tasks": ["1.1", "2.1"] },
    { "wave": 2, "tasks": ["1.2", "2.2", "4.1"] },
    { "wave": 3, "tasks": ["1.3", "2.3", "3.1", "4.2"] },
    { "wave": 4, "tasks": ["3.2", "4.3"] },
    { "wave": 5, "tasks": ["3.3"] },
    { "wave": 6, "tasks": ["5.1", "5.2"] },
    { "wave": 7, "tasks": ["5.3"] }
  ]
}
```

## Notes

- Reutiliza endpoints existentes: POST/GET /social/cuentas-canal (no crear controladores nuevos).
- V63 es NO destructiva (solo relaja CHECK). AVOID `clean` en el build. Backend mvn -o package/test.
- Frontend: ng build/test; reintentar en aislamiento las specs axe con flakiness conocida.
- Sin exponer credenciales ni UUIDs; espanol; tokens; WCAG AA; responsivo.
- Integracion real (Meta Graph / TikTok) queda PREPARADA tras puerto/adaptador; se documentan las
  credenciales necesarias por plataforma. No se cablea proveedor real en este alcance.
- Archivos UTF-8 sin BOM. Relanzar backend (con V63) y dev server al final.