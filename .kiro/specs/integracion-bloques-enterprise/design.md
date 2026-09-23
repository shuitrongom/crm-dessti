# Design Document

_(Documento de diseno - Integracion enterprise de los bloques Comercial, Redes Sociales y Estrategia)_

## Overview

Hallazgo clave tras auditar el backend: la mayoria de los enlaces YA existen a nivel de datos y de
API, por lo que esta integracion es PRINCIPALMENTE de frontend (exponer y conectar), con endpoints
de apoyo minimos y SIN migraciones nuevas:

- Oportunidad y Cotizacion ya se listan filtrando por `clienteId` (repos con `buscarConFiltros`),
  y hay agregados por cliente (`contarPorEtapa`, `contarPorEstado`, `sumarValorPipelineAbierto`).
- Oportunidad/Cotizacion ya referencian `canalVentaId` y exponen endpoints para asignarlo.
- La Cotizacion ya sugiere precio desde la Lista_Precios cuando la partida trae producto y precio
  vacio.
- La Conversacion social YA tiene `cliente_id`/`contacto_id` y un metodo de dominio `vincular(...)`
  => el "lead social" NO requiere migracion; solo falta exponer el endpoint/He UI de vinculacion si
  no existe.

Por tanto el diseno: (A) construye la Ficha 360 del Cliente reutilizando los filtros por cliente;
(B) enriquece las vistas de Oportunidad/Cotizacion/Producto para mostrar y navegar relaciones y
asignar/filtrar canal; (C) expone la vinculacion conversacion<->cliente/oportunidad en Redes; (D)
alimenta Estrategia/Inicio con indicadores comerciales cuando el modulo este contratado. Todo con
gating por modulo/permiso, multi-tenant, sin UUIDs, WCAG AA, tokens, espanol.

## Architecture

```
Ficha 360 Cliente (nueva vista / seccion en cliente)
  GET /clientes/{id}                      (datos)         [cliente:leer]
  GET /contactos?clienteId=...            (contactos)     [contacto:listar]
  GET /oportunidades?clienteId=...        (oportunidades) [oportunidad:listar]
  GET /cotizaciones?clienteId=...         (cotizaciones)  [cotizacion:listar]
  (indicadores) reutilizan agregados por cliente ya existentes si se exponen; si no,
  se calculan en el cliente a partir de las listas paginadas (fallback).

Oportunidad conectada (vista existente, enriquecida)
  PUT /oportunidades/{id}/canal-venta     (asignar canal) [oportunidad:actualizar]
  filtro canalVentaId en el listado (ya soportado por el repo)
  enlace a Cliente (ficha 360) y a Cotizacion generada (cotizacionId)

Cotizacion conectada (detalle existente, enriquecido)
  muestra Cliente (enlace) y Oportunidad de origen (oportunidadId, enlace)
  asigna/muestra canal; sugerencia de precio desde lista (ya soportada) aclarada en UI

Producto conectado
  GET /listas-precios/... precios por producto (segun API existente) o
  seccion "precios" en el producto reutilizando el endpoint de precios

Redes -> CRM (lead social)
  POST/PUT vinculacion de Conversacion a Cliente/Contacto (metodo de dominio vincular ya existe)
  desde Bandeja/detalle de Conversacion: selector de Cliente (sin UUID) o crear lead

Estrategia / Inicio
  si modulo 'comercial' contratado: mostrar indicadores (pipeline abierto, cotizaciones periodo)
  reutilizando los agregados comerciales; degradacion si no contratado
```

## Components and Interfaces

### Backend (cambios minimos; verificar existencia antes de crear)

1. **Vinculacion de Conversacion (lead social):** confirmar si existe un endpoint que invoque
   `Conversacion.vincular(clienteId, contactoId)`. Si NO existe, agregar en el controlador de
   bandeja/conversacion un `PUT /social/bandeja/{id}/vinculacion` (o `.../conversaciones/{id}/vinculo`)
   con permiso `conversacion:actualizar` que:
   - reciba `{ clienteId }` (y opcional `contactoId`),
   - valide que el Cliente existe en el tenant (puerto tipo `ClienteExistentePort`),
   - invoque el metodo de dominio y audite. Sin migracion (columnas ya existen).
2. **Crear lead (cliente/oportunidad) desde conversacion (opcional, fase 2):** un caso de uso que,
   dado el remitente de la conversacion, cree un Cliente minimo + Oportunidad y los vincule. Se
   evalua si el alcance lo amerita; puede quedar como "vincular a cliente existente" en fase 1.
3. **Indicadores por cliente para la Ficha 360 (opcional):** si conviene una sola llamada, exponer
   `GET /clientes/{id}/resumen-comercial` que devuelva contadores (oportunidades abiertas, valor en
   pipeline, num. cotizaciones) reutilizando los agregados ya existentes. Alternativa sin backend:
   el frontend calcula a partir de las listas. Preferir el endpoint si evita N llamadas.
4. **NO se tocan** los endpoints de PDF/correo/WhatsApp ni la sugerencia de precio (ya funcionan).

### Frontend

#### Comercial
- **Ficha 360 del Cliente:** al "ver" un cliente, ademas del formulario de solo lectura, agregar
  secciones (tarjetas/pesta#as) con: Contactos, Oportunidades, Cotizaciones (listas resumidas con
  estado/etapa/valor/folio) y una fila de indicadores (pipeline abierto, #oportunidades, ultima
  cotizacion). Cada item enlaza a su detalle. Cada seccion se oculta si falta el permiso de listar.
  Reutiliza los servicios existentes con el filtro `clienteId`.
- **Oportunidades:** agregar accion "Asignar canal" (selector de canal_venta, sin UUID), filtro por
  canal en la barra de filtros, columna/enlace al Cliente (ficha 360) y, si `cotizacionId` existe,
  enlace "Ver cotizacion". Conservar convertir/avanzar/aprobar segun permiso.
- **Cotizacion (detalle):** mostrar Cliente (enlace) y Oportunidad de origen (enlace si
  `oportunidadId`); selector/etiqueta de canal; aclarar en el alta que al elegir producto y dejar
  el precio vacio se toma el precio de la lista vigente. Conservar PDF/correo/WhatsApp/aprobacion.
- **Producto:** seccion "Precios por lista" (si `lista_precios:listar`/`producto` lo permite),
  mostrando el precio del producto en cada lista. Listas de precios ya asigna precio por producto.

#### Redes Sociales
- **Bandeja / detalle de Conversacion:** boton "Vincular a cliente" que abre un selector de Cliente
  (buscador sin UUID) e invoca el endpoint de vinculacion; mostrar el Cliente vinculado (enlace a
  ficha 360). Gating por modulo `redes-sociales` y permiso `conversacion:actualizar`.
- (Fase 2 opcional) "Crear lead": crea Cliente/Oportunidad desde la conversacion.

#### Estrategia / Inicio
- **Estrategia:** si `comercial` contratado, mostrar una tarjeta de indicadores comerciales (valor
  en pipeline, cotizaciones del periodo) como apoyo a los OKR; si no, solo captura manual.
- **Inicio:** resumen coherente de estrategia + (si aplica) mini-resumen comercial.

## Data Models

Sin tablas ni columnas nuevas. Se reutilizan: `cliente`, `contacto`, `oportunidad`
(cliente_id, canal_venta_id, cotizacion_id), `cotizacion` (cliente_id, oportunidad_id,
canal_venta_id), `partida_cotizacion` (producto_id), `precio_producto` (lista_precios_id,
producto_id), `canal_venta`, `conversacion` (cliente_id, contacto_id ya existentes).

## Error Handling

- Secciones relacionadas sin permiso: se ocultan (no error).
- Sin datos relacionados: estado vacio claro ("Este cliente aun no tiene oportunidades", etc.).
- Vincular conversacion a cliente inexistente/otro tenant: 404/validacion; la UI muestra mensaje.
- Indicadores comerciales sin modulo comercial: no se muestran (degradacion), sin romper.

## Testing Strategy

### Backend
- Si se agrega el endpoint de vinculacion: prueba de servicio (vincula, valida cliente del tenant,
  audita) y slice del controlador (permiso, 404 cliente ajeno, 200 ok).
- Si se agrega `resumen-comercial`: prueba que agrega por cliente y respeta tenant.
- No regresion de las suites comerciales/sociales existentes.

### Frontend
- Ficha 360: renderiza secciones segun permiso; enlaces navegan; estados vacio/carga/error; axe.
- Oportunidades: asignar canal (llama al endpoint), filtro por canal, enlaces a cliente/cotizacion.
- Cotizacion detalle: muestra cliente/oportunidad de origen/canal; specs existentes verdes.
- Redes: vincular conversacion a cliente (selector sin UUID); gating.
- Estrategia/Inicio: indicadores solo con modulo comercial; degradacion sin el.

## Verification

- Backend: `mvn -o package -DskipTests` + `mvn -o test` verdes; relanzar solo si hubo cambios de
  backend.
- Frontend: `ng build` (0 errores) + `ng test` (verde; axe flaky en aislamiento).
- Vivo: abrir un cliente y ver su ficha 360 con oportunidades/cotizaciones; asignar canal a una
  oportunidad; vincular una conversacion a un cliente; ver indicadores en Estrategia/Inicio.

## Correctness Properties

### Property 1: Aislamiento multi-tenant en relaciones - toda lista/relacion mostrada (oportunidades,
  cotizaciones, contactos, conversaciones vinculadas) pertenece al mismo tenant del contexto; nunca
  se muestran datos de otra Empresa.

**Validates: Requirements 1.1, 5.2, 6.4, 8.4**

### Property 2: Gating por permiso en cada seccion - una seccion relacionada solo se renderiza si el
  usuario tiene el permiso de listar el recurso correspondiente; sin permiso, no aparece (ni datos
  ni error).

**Validates: Requirements 1.4, 2.5, 7.2**

### Property 3: Sin identificadores tecnicos - ninguna vista de relacion ni selector expone UUIDs;
  las entidades se muestran y eligen por nombre/folio/titulo.

**Validates: Requirements 1.5, 2.1, 5.5, 7.1**

### Property 4: Navegacion consistente - todo enlace entre entidades relacionadas navega al detalle
  correcto de la misma Empresa y respeta el gating por modulo.

**Validates: Requirements 1.2, 2.3, 2.4, 3.1, 7.1, 7.2**

### Property 5: Degradacion sin romper - si un modulo no esta contratado o no hay datos
  relacionados, la vista degrada con estado vacio/oculto sin lanzar errores.

**Validates: Requirements 5.4, 6.2, 8.4**