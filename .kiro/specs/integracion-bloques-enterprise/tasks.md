# Implementation Plan

## Overview

Conectar los tres bloques (Comercial, Redes Sociales, Estrategia) de forma enterprise, exponiendo en
la UI las relaciones que el backend ya soporta. Sin migraciones nuevas. Prioridad: Comercial (Ficha
360) primero, luego Redes (lead social), luego Estrategia. Cambios de backend minimos y solo si un
endpoint no existe ya.

## Tasks

- [ ] 1. Comercial: Ficha 360 del Cliente
- [ ] 1.1 (Backend, si aplica) Endpoint de resumen comercial por cliente
  - Verificar si conviene `GET /clientes/{id}/resumen-comercial` (oportunidades abiertas, valor en pipeline, num. cotizaciones) reutilizando los agregados existentes. Si el frontend puede calcularlo con pocas llamadas, OMITIR este endpoint. Con permiso `cliente:leer`; multi-tenant.
  - _Requirements: 1.3_
- [ ] 1.2 Vista Ficha 360 en "ver Cliente"
  - Al ver un cliente, agregar secciones: Contactos, Oportunidades, Cotizaciones (listas resumidas con estado/etapa/valor/folio) y fila de indicadores. Reutiliza servicios con filtro `clienteId`. Cada seccion se oculta si falta el permiso de listar. Enlaces navegables a cada detalle. Sin UUIDs. Estados carga/vacio/error; responsivo; WCAG AA.
  - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5, 7.1, 7.3_
- [ ] 1.3 Pruebas Ficha 360
  - Renderiza secciones segun permiso; enlaces navegan; estados vacio/carga/error; axe WCAG; no muestra UUIDs.
  - _Requirements: 1.1, 1.4, 1.5, 8.2_

- [ ] 2. Comercial: Oportunidad conectada
- [ ] 2.1 Asignar/mostrar Canal_Venta desde la UI
  - Accion "Asignar canal" con selector (sin UUID) que invoca el endpoint existente; mostrar el canal actual de cada oportunidad.
  - _Requirements: 2.1, 7.1_
- [ ] 2.2 Filtro por Canal_Venta y enlaces
  - Agregar filtro por canal (repo ya lo soporta); enlace al Cliente (ficha 360) y, si `cotizacionId`, enlace "Ver cotizacion". Conservar convertir/avanzar/aprobar por permiso.
  - _Requirements: 2.2, 2.3, 2.4, 2.5, 7.1, 7.2_
- [ ] 2.3 Pruebas Oportunidad
  - Asignar canal llama al endpoint; filtro por canal; enlaces a cliente/cotizacion; permisos respetados.
  - _Requirements: 2.1, 2.2, 2.3, 8.2_

- [ ] 3. Comercial: Cotizacion y Producto conectados
- [ ] 3.1 Detalle de Cotizacion enriquecido
  - Mostrar Cliente (enlace a ficha 360) y Oportunidad de origen (enlace si `oportunidadId`); selector/etiqueta de canal; aclarar en el alta la sugerencia de precio desde la lista vigente. Conservar PDF/correo/WhatsApp/aprobacion.
  - _Requirements: 3.1, 3.2, 3.3, 3.4, 7.1_
- [ ] 3.2 Producto: precios por lista
  - En la vista de Producto, seccion "Precios por lista" (si el permiso lo permite). Confirmar que Listas de precios asigna precio por producto con selector (sin UUID).
  - _Requirements: 4.1, 4.2, 4.3_
- [ ] 3.3 Pruebas Cotizacion/Producto
  - Detalle muestra cliente/oportunidad/canal; producto muestra precios por lista; specs existentes verdes.
  - _Requirements: 3.1, 4.1, 8.2_

- [ ] 4. Redes Sociales: lead social (vinculo conversacion<->cliente)
- [ ] 4.1 (Backend) Endpoint de vinculacion si no existe
  - Verificar si existe endpoint que invoque `Conversacion.vincular(clienteId, contactoId)`. Si NO, agregar `PUT /social/bandeja/{id}/vinculacion` con permiso `conversacion:actualizar`: valida cliente del tenant (puerto), invoca dominio, audita. SIN migracion (columnas ya existen).
  - _Requirements: 5.1, 5.2_
- [ ] 4.2 UI de vinculacion en Bandeja/Conversacion
  - Boton "Vincular a cliente" con selector de Cliente (sin UUID); mostrar el cliente vinculado como enlace a ficha 360. Gating por modulo `redes-sociales` y permiso. Sin credenciales/UUIDs.
  - _Requirements: 5.1, 5.2, 5.4, 5.5, 7.1_
- [ ] 4.3 Pruebas lead social
  - Backend (si aplica): vincula, valida cliente del tenant, audita, 404 cliente ajeno. Frontend: selector sin UUID, gating, muestra vinculo.
  - _Requirements: 5.1, 5.2, 8.2_

- [ ] 5. Estrategia e Inicio conectados a lo comercial
- [ ] 5.1 Indicadores comerciales en Estrategia
  - Si `comercial` contratado, tarjeta con valor en pipeline y cotizaciones del periodo (reutiliza agregados). Degradacion si no contratado. Sin datos de otras empresas ni UUIDs.
  - _Requirements: 6.1, 6.2, 6.4_
- [ ] 5.2 Inicio coherente (estrategia + resumen comercial)
  - Inicio refleja la estrategia capturada y, si aplica, un mini-resumen comercial.
  - _Requirements: 6.3_
- [ ] 5.3 Pruebas Estrategia/Inicio
  - Indicadores solo con modulo comercial; degradacion sin el; multi-tenant; axe.
  - _Requirements: 6.1, 6.2, 6.4, 8.2_

- [ ] 6. Verificacion integral y puesta en vivo
- [ ] 6.1 Build + pruebas backend (si hubo cambios)
  - `mvn -o package -DskipTests` (0 errores) y `mvn -o test` (verde). Relanzar backend solo si cambio.
  - _Requirements: 8.1, 8.2_
- [ ] 6.2 Build + pruebas frontend
  - `ng build` (0 errores) y `ng test` (verde; reintentar axe flaky en aislamiento).
  - _Requirements: 8.1, 8.2, 8.3_
- [ ] 6.3 Verificacion viva
  - Redeploy del frontend (y backend si cambio) al servidor; abrir ficha 360, asignar canal, vincular conversacion, ver indicadores. Confirmar navegacion y gating.
  - _Requirements: 1.1, 2.1, 5.1, 6.1, 7.1_

## Task Dependency Graph

```
1.1 -> 1.2 -> 1.3
1.2 -> 2.2 (los enlaces a ficha 360 dependen de que exista la ficha)
2.1 -> 2.2 -> 2.3
1.2 -> 3.1 ; 3.1 -> 3.3 ; 3.2 -> 3.3
4.1 -> 4.2 -> 4.3   ; 4.2 enlaza a ficha 360 (1.2)
1.x agregados -> 5.1 -> 5.2 -> 5.3
todo -> 6.1, 6.2 -> 6.3
```

```json
{
  "waves": [
    { "wave": 1, "tasks": ["1.1", "2.1", "4.1"] },
    { "wave": 2, "tasks": ["1.2"] },
    { "wave": 3, "tasks": ["1.3", "2.2", "3.1", "3.2", "4.2", "5.1"] },
    { "wave": 4, "tasks": ["2.3", "3.3", "4.3", "5.2"] },
    { "wave": 5, "tasks": ["5.3"] },
    { "wave": 6, "tasks": ["6.1", "6.2"] },
    { "wave": 7, "tasks": ["6.3"] }
  ]
}
```

## Notes

- SIN migraciones nuevas: todas las columnas/relaciones ya existen (incluido conversacion.cliente_id).
- Aprovechar los filtros por clienteId y los agregados por cliente ya presentes en los repos.
- No tocar PDF/correo/WhatsApp ni la sugerencia de precio (ya funcionan).
- Antes de crear cualquier endpoint, VERIFICAR si ya existe (vinculacion, resumen) para no duplicar.
- Frontend: ng build/test; reintentar en aislamiento las specs axe con flakiness conocida.
- Sin exponer UUIDs ni credenciales; espanol; tokens; WCAG AA; responsivo; multi-tenant.
- Preservar el comportamiento verificado en produccion. Redeploy al servidor solo tras verificar.
- Archivos UTF-8 sin BOM.