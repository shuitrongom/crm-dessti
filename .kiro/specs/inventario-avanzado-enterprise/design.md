# Design Document

_(Documento de diseno - Inventario Avanzado enterprise, completo y conectado)_

## Overview

El backend del Inventario Avanzado ya expone 13 endpoints (almacenes CRUD, config por material,
kardex, existencias, entradas, salidas, transferencias, lotes crear/listar) con motor de costeo
promedio/PEPS. El trabajo es PRINCIPALMENTE de FRONTEND: construir la UI enterprise completa y
CONECTADA, eliminando los UUIDs visibles.

Hallazgo clave de contrato: los DTOs de existencias/movimientos/lotes traen SOLO `almacenId`/
`materialId`/`loteId` (UUID), NO los nombres. Para cumplir "sin UUIDs" sin tocar el backend probado,
el frontend RESUELVE nombres en el cliente: carga una vez Almacenes y Materiales, arma mapas
`id -> nombre` y muestra los nombres (mismo patron usado con exito en Oportunidades para el canal de
venta). Esto evita cambios de backend y mantiene los contratos verificados.

El servicio frontend `InventarioAvanzadoService` hoy solo cubre almacenes/existencias/kardex; se
AMPLIA con: registrarEntrada, registrarSalida, transferir, configurarInventarioMaterial, crearLote,
listarLotes (endpoints ya existentes en el backend).

## Architecture

```
Vista Inventario Avanzado (features/operacion/inventario-avanzado) — reorganizada por secciones:
  1. Resumen/Indicadores  (almacenes, stock bajo, por reabastecer, valor total — calculados)
  2. Existencias          GET /inventario-avanzado/existencias?almacenId&materialId
  3. Movimientos          POST .../almacenes/{almacenId}/entradas
                          POST .../almacenes/{almacenId}/salidas
                          POST /inventario-avanzado/transferencias
  4. Kardex               GET  .../almacenes/{almacenId}/materiales/{materialId}/kardex
  5. Lotes                POST/GET .../materiales/{materialId}/lotes
  6. Configuracion        PUT  .../materiales/{materialId}/config-inventario

Resolucion de nombres (sin UUIDs):
  - Almacenes: InventarioAvanzadoService.listarAlmacenes(null, true, 0, ~200) -> Map<id,nombre>
  - Materiales: MaterialesService.listar(null, false, 0, ~200) -> Map<id,nombre>
  - Selectores por nombre con entity-select (busqueda), no campos de UUID.
```

## Components and Interfaces

### Frontend — Servicio (ampliacion, sin backend nuevo)
Agregar a `InventarioAvanzadoService` (operacion/services/inventario.service.ts):
- `registrarEntrada(almacenId, { materialId, cantidad, costoUnitario, loteId? })` -> POST
  `/inventario-avanzado/almacenes/{almacenId}/entradas`.
- `registrarSalida(almacenId, { materialId, cantidad, loteId? })` -> POST `.../salidas`.
- `transferir({ almacenOrigenId, almacenDestinoId, materialId, cantidad })` -> POST
  `/inventario-avanzado/transferencias`.
- `configurarInventarioMaterial(materialId, { metodoCosteo, stockMaximo?, controlLote,
  consumoPromedio, tiempoEntregaDias })` -> PUT `.../materiales/{materialId}/config-inventario`.
- `crearLote(materialId, { codigo, fechaCaducidad? })` -> POST `.../materiales/{materialId}/lotes`.
- `listarLotes(materialId, page, size)` -> GET `.../materiales/{materialId}/lotes`.
- `consultarConfigInventario(materialId)` si existe GET (verificar; el controlador expone PUT; para
  leer la config, si no hay GET dedicado, la UI carga la config al abrir el material via el DTO que
  regrese el PUT o via un GET si existe. Verificar el controlador; NO inventar endpoint: si no hay
  GET, la UI edita en base a valores por defecto/lo devuelto por el PUT).
- Los request/response TS se agregan en `operacion.models.ts` (RegistrarEntradaRequest, etc.),
  reflejando los request records del backend.

### Frontend — Vista reorganizada `OperacionInventarioAvanzado`
Reemplaza las 3 pestanas actuales por secciones enterprise (mat-tabs), todas gated por permiso:
- **Resumen** (si hay permiso de leer existencias/almacenes): stat-cards con # almacenes, #
  materiales con stock bajo (existencia < stock_minimo del material), # por reabastecer (segun
  config si disponible) y valor total del inventario (suma cantidad*costoPromedio). Calculado en el
  cliente a partir de existencias + materiales; si el volumen es grande se acota a lo paginado y se
  documenta.
- **Existencias**: tabla que muestra NOMBRE de almacen y de material (via mapas id->nombre),
  cantidad y costo promedio; filtros por Almacen y Material (entity-select, sin UUID); resalte de
  stock bajo (comparando con stock_minimo del material) y de sobre-maximo (si config disponible);
  cada fila con accion "Ver Kardex" que abre la pestana Kardex ya filtrada.
- **Movimientos**: tres acciones/formularios (Entrada, Salida, Transferencia) con selectores por
  nombre (almacen/material/lote); tras exito, toast + recarga de existencias/kardex. Gated por
  `movimiento_inventario:crear`. Salida con existencias insuficientes -> mensaje claro del 422.
- **Kardex**: selectores Almacen + Material (por nombre); tabla cronologica con fecha, tipo
  (etiqueta es-MX), cantidad, costo unitario/total, saldo (cantidad y costo). Se puede abrir desde
  Existencias con seleccion previa.
- **Lotes**: elegir Material (selector) -> listar sus lotes + alta de lote (codigo, caducidad);
  resaltar proximos a caducar/caducados. Gated por `lote:listar`/`lote:crear`.
- **Configuracion**: elegir Material (selector) -> editar metodo de costeo (Promedio ponderado /
  PEPS), stock maximo, control de lote y parametros de reorden; PUT; validaciones no-negativas.
  Gated por `material:actualizar`.
- **Alertas**: seccion consolidada (o dentro de Resumen) con materiales bajo minimo / sobre maximo /
  por reabastecer, calculada de existencias + config, con enlace a Kardex / registrar entrada.

Etiquetas de tipo de movimiento y metodo de costeo en espanol via mapas centralizados. Fechas con
el datepicker/formatos es-MX ya estandarizados. Todos los selectores usan entity-select (sin UUID).

### Conexion (Req 8)
- El selector de Material usa `MaterialesService` del Nucleo (mismo catalogo, no paralelo).
- Si el `MovimientoAlmacenDto` trae origen (orden_fabricacion_id/recepcion) se muestra la etiqueta
  de origen en el Kardex; si no lo trae, se muestra solo el tipo (no se inventa vinculo). Verificar
  el DTO; el diseno no exige cambios de backend.
- Indicadores de inventario ya tienen adaptador backend (IndicadorInventarioAvanzadoAdapter) que
  alimenta el Tablero/Reportes; la UI de inventario NO lo duplica, solo muestra su propio resumen.

## Data Models

Sin tablas nuevas. Reutiliza almacen, existencia_almacen, movimiento_almacen, capa_costo, lote,
config_inventario_material (V26). Los modelos TS nuevos son request/response de los endpoints ya
existentes.

## Error Handling

- Salida con existencias insuficientes: el backend responde 422; la UI muestra el mensaje.
- Selecciones incompletas en formularios: validacion en cliente antes de enviar; backend autoridad.
- Sin permiso para una seccion: se oculta (no error).
- Resolucion de nombres: si un id no esta en el mapa (paginacion), se muestra un marcador neutro
  ("(sin nombre)") en vez del UUID — NUNCA el UUID.

## Testing Strategy

### Frontend
- Servicio: cada metodo nuevo (entrada/salida/transferencia/config/lote) hace el POST/PUT correcto
  con el cuerpo esperado.
- Vista: existencias muestran NOMBRES (no UUID) resolviendo por mapa; filtros por almacen/material;
  registrar entrada/salida/transferencia llama al servicio; kardex por seleccion y desde existencia;
  config y lotes gated por permiso; alertas calculadas; axe WCAG; ningun UUID en el DOM.
- Specs existentes del modulo operacion permanecen verdes.

### Backend
- Sin cambios previstos. Si se detecta la necesidad de un GET de configuracion por material (para
  precargar el formulario) y NO existe, se evaluara agregarlo con su prueba; de lo contrario NO se
  toca el backend.

## Verification

- Frontend: `ng build` (0 errores) + specs tocadas en aislamiento (one-shot, sin watch).
- Backend: solo si se toca; `mvn -o test -Dtest=...` y luego build.
- Vivo (tras deploy): crear almacenes, registrar entradas (ver costeo), salidas (PEPS/promedio),
  transferencia; ver existencias con nombres y alertas; consultar Kardex; configurar un material;
  crear/listar lotes. Confirmar que NO aparece ningun UUID.

## Correctness Properties

### Property 1: Sin identificadores tecnicos - ninguna vista del inventario avanzado renderiza un
  UUID; Almacenes, Materiales y Lotes se muestran/eligen por nombre/codigo. Un id no resuelto cae a
  un marcador neutro, nunca al UUID.

**Validates: Requirements 1.2, 2.1, 3.6, 4.1**

### Property 2: Gating por permiso - cada seccion/accion (existencias, movimientos, kardex, lotes,
  configuracion) se renderiza solo con su permiso atomico; sin el, no aparece (ni datos ni error).

**Validates: Requirements 1.4, 3.5, 5.1, 6.1**

### Property 3: Integridad de movimientos - registrar entrada/salida/transferencia invoca el
  endpoint correcto; una salida que dejaria existencias negativas se rechaza (422) y la UI muestra
  el mensaje sin corromper la vista; tras un movimiento exitoso las existencias/kardex reflejan el
  cambio.

**Validates: Requirements 3.1, 3.2, 3.3, 3.4**

### Property 4: Costeo delegado al backend - la UI nunca calcula el costo de valuacion; envia los
  datos del movimiento y el backend aplica el metodo configurado (promedio/PEPS). La UI solo muestra
  el costo devuelto.

**Validates: Requirements 3.1, 3.2, 5.1**

### Property 5: Conexion con el Nucleo - el selector de Material usa el catalogo de Materiales del
  Nucleo (no un catalogo paralelo); las existencias/kardex refieren esos mismos materiales.

**Validates: Requirements 8.1, 8.4**