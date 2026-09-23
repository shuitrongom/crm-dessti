# Requirements Document

_(Documento de requisitos - Inventario Avanzado enterprise, completo y conectado)_

## Introduction

El bloque Inventario Avanzado (modulo `inventario-avanzado`, Req 60) tiene un BACKEND muy
completo y solido: almacenes multi-sitio, existencias por almacen, Kardex append-only por
almacen, motor de costeo (promedio movil y PEPS por capas de costo), lotes con caducidad,
configuracion de inventario por material (metodo de costeo, stock maximo, punto de reorden,
control de lote), entradas, salidas, transferencias entre almacenes y notificaciones de stock
minimo/maximo/reabastecimiento, mas un adaptador de indicadores. El controlador REST expone 13
endpoints.

Sin embargo, la INTERFAZ actual esta pobre y desconectada: una sola pantalla con 3 pestanas
(Almacenes CRUD, Existencias solo-lectura y Kardex) que ademas EXPONE UUIDs crudos (las
existencias muestran `almacenId`/`materialId` como identificadores tecnicos, y el Kardex EXIGE
teclear esos UUID a mano). No hay UI para registrar entradas, salidas ni transferencias, ni para
configurar el inventario por material, ni para gestionar lotes, ni para ver alertas o indicadores.
Es decir: gran parte del poder del backend esta desperdiciado y lo poco visible no es usable ni
profesional.

Esta especificacion construye la UI enterprise COMPLETA y CONECTADA del Inventario Avanzado,
aprovechando el backend existente (sin reescribirlo), eliminando por completo los UUIDs visibles
(todo por selectores de nombre), y conectando el inventario con el resto del sistema (Materiales
del Nucleo, y trazabilidad hacia Compras/Produccion cuando aplique). Se preservan las reglas de la
plataforma: multi-tenant, RBAC por permiso atomico y modulo contratado, espanol, responsivo,
WCAG AA, tokens del sistema de diseno, sin exponer UUIDs.

## Glossary

- **Almacen:** sucursal o bodega donde se guardan existencias.
- **Existencia por Almacen:** saldo vivo (cantidad + costo promedio) de un Material en un Almacen.
- **Kardex:** historial cronologico append-only de movimientos de un Material en un Almacen, con
  saldo tras cada movimiento (inventario perpetuo).
- **Movimiento:** entrada, salida, ajuste o transferencia que altera existencias y costo.
- **Costeo:** metodo de valuacion — promedio movil ponderado o PEPS (FIFO por capas de costo).
- **Capa de costo:** lote de costo de una entrada (cantidad restante + costo unitario) que PEPS
  consume en orden.
- **Lote:** agrupacion de un Material con codigo y caducidad opcional (trazabilidad).
- **Configuracion de inventario por Material:** metodo de costeo, stock maximo, control de lote y
  parametros de punto de reorden (consumo promedio, tiempo de entrega).
- **Punto de reorden / reabastecimiento:** nivel que dispara la alerta de recompra.
- **Material:** articulo de inventario (Nucleo, Req 18); el inventario avanzado opera SOBRE
  materiales existentes.

## Requirements

### Requirement 1: Panel de Inventario Avanzado con indicadores y sin UUIDs

**User Story:** Como responsable de almacen, quiero un panel claro con indicadores y navegacion por
nombre (nunca por identificadores tecnicos), para operar el inventario de forma profesional.

#### Acceptance Criteria

1. LA vista principal DEBE presentar el inventario avanzado organizado en secciones/pestanas
   coherentes (Existencias, Almacenes, Movimientos, Lotes, Configuracion) segun permisos.
2. EN NINGUNA pantalla se DEBEN mostrar UUIDs: Almacenes, Materiales y Lotes se muestran y eligen
   por NOMBRE/codigo mediante selectores; las existencias muestran el nombre del almacen y del
   material, no sus identificadores.
3. LA vista DEBE mostrar indicadores utiles (p. ej. numero de almacenes, materiales con stock bajo,
   materiales por reabastecer, valor total del inventario) cuando los datos esten disponibles.
4. CADA seccion DEBE respetar el permiso atomico correspondiente (deny-by-default) y el modulo
   contratado; sin permiso, la seccion se oculta.
5. LOS estados de carga, vacio y error DEBEN usar los componentes compartidos; responsivo; WCAG AA;
   en espanol.

### Requirement 2: Existencias por Almacen legibles y filtrables

**User Story:** Como responsable de almacen, quiero ver las existencias por almacen y material con
nombres y poder filtrar, para saber que tengo y donde.

#### Acceptance Criteria

1. LA vista de Existencias DEBE listar cantidad y costo promedio por Almacen y Material mostrando
   los NOMBRES (no UUIDs).
2. LA vista DEBE permitir FILTRAR por Almacen (selector) y por Material (selector), sin teclear
   identificadores.
3. LA vista DEBE resaltar visualmente los materiales por debajo del stock minimo (alerta de stock
   bajo) y, si aplica, por encima del stock maximo.
4. CADA fila DEBE permitir navegar al Kardex de ese Almacen+Material con un clic.

### Requirement 3: Movimientos de inventario (entrada, salida, transferencia)

**User Story:** Como operador de almacen, quiero registrar entradas, salidas y transferencias con
selectores de almacen/material/lote, para mover el inventario sin conocer identificadores.

#### Acceptance Criteria

1. LA UI DEBE permitir registrar una ENTRADA (almacen, material, cantidad, costo unitario y lote
   opcional) usando selectores por nombre, invocando el endpoint existente; el costeo lo calcula el
   backend.
2. LA UI DEBE permitir registrar una SALIDA (almacen, material, cantidad, lote opcional) que el
   backend valua por el metodo configurado (promedio/PEPS); una salida que dejaria existencias
   negativas DEBE rechazarse con un mensaje claro (422 del backend).
3. LA UI DEBE permitir una TRANSFERENCIA entre dos Almacenes (origen, destino, material, cantidad)
   usando selectores, invocando el endpoint existente.
4. TRAS cualquier movimiento, las existencias y el Kardex DEBEN reflejar el cambio (recarga o
   actualizacion en vista).
5. LAS acciones DEBEN gobernarse por el permiso `movimiento_inventario:crear`; sin el, se ocultan.
6. NINGUN campo DEBE pedir un UUID escrito a mano; todo por selector con busqueda por nombre.

### Requirement 4: Kardex por Almacen y Material navegable

**User Story:** Como responsable de almacen, quiero consultar el Kardex eligiendo almacen y material
por nombre (o llegando desde una existencia), para auditar el historial y los saldos.

#### Acceptance Criteria

1. LA consulta de Kardex DEBE hacerse eligiendo Almacen y Material por SELECTOR (nunca UUID escrito).
2. EL Kardex DEBE mostrar, por movimiento: fecha, tipo (entrada/salida/ajuste/transferencia),
   cantidad, costo unitario/total y saldo (cantidad y costo) tras el movimiento, en orden
   cronologico, paginado.
3. SE DEBE poder abrir el Kardex directamente desde una fila de Existencias (Req 2.4) con el
   Almacen y Material ya seleccionados.

### Requirement 5: Configuracion de inventario por Material

**User Story:** Como responsable de almacen, quiero configurar por material su metodo de costeo,
stock maximo, control de lote y parametros de reorden, para automatizar valuacion y alertas.

#### Acceptance Criteria

1. LA UI DEBE permitir configurar, por Material (elegido por selector), el metodo de costeo
   (promedio/PEPS), el stock maximo, el control de lote y los parametros de punto de reorden
   (consumo promedio, tiempo de entrega), invocando el endpoint existente.
2. LA UI DEBE mostrar la configuracion actual del Material y validar los rangos (no negativos)
   antes de enviar; el backend es la autoridad (422 si invalido).
3. EL metodo de costeo DEBE presentarse con etiquetas claras en espanol (Promedio ponderado / PEPS).

### Requirement 6: Lotes y caducidad

**User Story:** Como responsable de almacen, quiero dar de alta y consultar lotes de un material con
su caducidad, para trazabilidad y control de vencimientos.

#### Acceptance Criteria

1. LA UI DEBE permitir crear un Lote de un Material (codigo unico, caducidad opcional) y listar los
   lotes de ese Material, usando el Material por selector.
2. LOS lotes DEBEN poder elegirse (cuando el material tiene control de lote) en las entradas/salidas.
3. LA UI DEBE indicar de forma visible los lotes proximos a caducar o caducados, cuando exista la
   fecha de caducidad.

### Requirement 7: Alertas de inventario (stock minimo/maximo/reabastecimiento)

**User Story:** Como responsable de almacen, quiero ver que materiales estan bajo minimo, sobre
maximo o por reabastecer, para actuar a tiempo.

#### Acceptance Criteria

1. LA UI DEBE presentar, de forma consolidada, los materiales en condicion de alerta: stock por
   debajo del minimo, por encima del maximo y por reabastecer (segun punto de reorden).
2. LAS alertas DEBEN derivarse de los datos existentes (existencias + configuracion); si el backend
   expone endpoints/indicadores para esto, se reutilizan; si no, se calculan en el cliente a partir
   de existencias y configuracion, sin inventar endpoints.
3. LAS alertas DEBEN enlazar al material/almacen correspondiente para actuar (p. ej. abrir Kardex o
   registrar entrada).

### Requirement 8: Conexion con el resto del sistema (nada aislado)

**User Story:** Como usuario enterprise, quiero que el inventario se relacione con Materiales,
Compras y Produccion, para que el flujo sea trazable de punta a punta.

#### Acceptance Criteria

1. EL inventario avanzado DEBE operar sobre los Materiales existentes del Nucleo (selector de
   material compartido), no sobre un catalogo paralelo.
2. CUANDO exista trazabilidad hacia Compras (recepcion de mercancia) o Produccion (consumo por
   Orden_Fabricacion) en el backend, la UI DEBE reflejar el ORIGEN del movimiento en el Kardex
   (p. ej. "entrada por recepcion", "salida por orden de fabricacion") cuando el dato este
   disponible; si no hay vinculo disponible, el movimiento se muestra con su tipo sin inventar
   relaciones.
3. LOS indicadores de inventario DEBEN poder alimentar el panel de Inicio/Reportes cuando el modulo
   este contratado (coherente con la conexion enterprise de otros bloques), sin romper si no lo esta.
4. LA navegacion entre Existencias, Kardex, Lotes y Configuracion DEBE ser fluida (un clic), sin
   UUIDs.

### Requirement 9: Calidad y no regresion

#### Acceptance Criteria

1. LOS cambios DEBEN compilar (backend si se toca, y frontend) sin errores.
2. LA suite de pruebas existente DEBE permanecer en verde, agregando pruebas para las nuevas vistas
   y flujos.
3. LA UI DEBE respetar tokens, ser responsiva y cumplir WCAG AA; en espanol; SIN UUIDs visibles.
4. LOS cambios DEBEN preservar el comportamiento y los contratos ya verificados del backend.