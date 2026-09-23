# Implementation Plan

## Overview

Construir la UI enterprise completa y conectada del Inventario Avanzado, reutilizando los 13
endpoints existentes del backend (sin reescribirlo) y ELIMINANDO los UUIDs visibles (resolucion de
nombres en el cliente via mapas id->nombre + selectores). Trabajo principalmente frontend. Prioridad:
servicio ampliado + Existencias legibles + Movimientos (lo que mas valor y conexion aporta), luego
Kardex, Configuracion, Lotes, Alertas/Resumen.

## Tasks

- [ ] 1. Frontend: ampliar servicio y modelos (base para todo)
- [ ] 1.1 Modelos TS de request/response de los endpoints faltantes
  - En operacion.models.ts: RegistrarEntradaRequest, RegistrarSalidaRequest, TransferirRequest, ConfigurarInventarioMaterialRequest/Dto, CrearLoteRequest/Lote, y tipos de MetodoCosteo. Reflejar los request records del backend.
  - _Requirements: 3.1, 3.2, 3.3, 5.1, 6.1_
- [ ] 1.2 Ampliar InventarioAvanzadoService
  - Agregar registrarEntrada, registrarSalida, transferir, configurarInventarioMaterial, crearLote, listarLotes hacia los endpoints existentes. Verificar si hay GET de config; si NO, documentar que la config se edita con valores por defecto/lo devuelto por el PUT (no inventar endpoint).
  - _Requirements: 3.1, 3.2, 3.3, 5.1, 6.1, 6.2_
- [ ] 1.3 Prueba del servicio
  - Cada metodo nuevo hace el POST/PUT correcto con el cuerpo esperado.
  - _Requirements: 3.1, 3.2, 3.3, 5.1, 6.1_

- [ ] 2. Frontend: Existencias legibles y conectadas (sin UUIDs)
- [ ] 2.1 Resolucion de nombres id->nombre
  - Cargar Almacenes (listarAlmacenes) y Materiales (MaterialesService.listar) una vez; construir mapas id->nombre; helper que devuelve nombre o marcador neutro (nunca UUID).
  - _Requirements: 1.2, 2.1_
- [ ] 2.2 Tabla de Existencias con nombres, filtros y alertas
  - Mostrar nombre de almacen y material, cantidad y costo promedio; filtros por Almacen y Material (entity-select, sin UUID); resaltar stock bajo (existencia < stock_minimo del material) y sobre-maximo si hay config; accion "Ver Kardex" por fila.
  - _Requirements: 2.1, 2.2, 2.3, 2.4, 1.5_
- [ ] 2.3 Pruebas Existencias
  - Muestra nombres (no UUID); filtros invocan el servicio; resalte de stock bajo; "Ver Kardex" navega con seleccion; axe.
  - _Requirements: 2.1, 2.2, 2.3, 2.4_

- [ ] 3. Frontend: Movimientos (entrada, salida, transferencia)
- [ ] 3.1 Formularios de Entrada / Salida / Transferencia
  - Selectores por nombre (almacen/material/lote); Entrada (cantidad, costo unitario, lote opcional); Salida (cantidad, lote opcional); Transferencia (origen, destino, material, cantidad). Invocar el servicio; tras exito toast + recarga de existencias/kardex. Gated por movimiento_inventario:crear.
  - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 3.6_
- [ ] 3.2 Manejo de salida insuficiente y validaciones
  - Salida que dejaria existencias negativas -> mostrar el mensaje 422 sin romper la vista; validar campos en cliente antes de enviar.
  - _Requirements: 3.2, 9.4_
- [ ] 3.3 Pruebas Movimientos
  - Entrada/salida/transferencia llaman al endpoint correcto; 422 de salida insuficiente se muestra; gating; sin UUID en el DOM.
  - _Requirements: 3.1, 3.2, 3.3, 3.5, 3.6_

- [ ] 4. Frontend: Kardex navegable
- [ ] 4.1 Kardex por seleccion y desde Existencias
  - Selectores Almacen + Material por nombre; tabla cronologica (fecha, tipo etiqueta es-MX, cantidad, costo unitario/total, saldo cantidad y costo); abrir con seleccion previa desde Existencias (Req 2.4).
  - _Requirements: 4.1, 4.2, 4.3_
- [ ] 4.2 Pruebas Kardex
  - Consulta por seleccion (sin UUID escrito); apertura desde existencia con seleccion; columnas correctas; axe.
  - _Requirements: 4.1, 4.2, 4.3_

- [ ] 5. Frontend: Configuracion por Material y Lotes
- [ ] 5.1 Configuracion de inventario por Material
  - Elegir Material (selector); editar metodo de costeo (Promedio ponderado/PEPS), stock maximo, control de lote, consumo promedio, tiempo de entrega; PUT; validaciones no-negativas; etiquetas es-MX. Gated por material:actualizar.
  - _Requirements: 5.1, 5.2, 5.3_
- [ ] 5.2 Lotes y caducidad
  - Elegir Material (selector); listar sus lotes + alta (codigo, caducidad con datepicker); resaltar proximos a caducar/caducados; ofrecer lote en entradas/salidas cuando el material controla lotes. Gated por lote:listar/crear.
  - _Requirements: 6.1, 6.2, 6.3_
- [ ] 5.3 Pruebas Configuracion/Lotes
  - Config hace el PUT correcto; lotes crear/listar; caducidad resaltada; gating; sin UUID.
  - _Requirements: 5.1, 6.1, 6.3_

- [ ] 6. Frontend: Resumen/Indicadores y Alertas + conexion
- [ ] 6.1 Resumen con indicadores y alertas consolidadas
  - Stat-cards (# almacenes, materiales con stock bajo, por reabastecer, valor total = suma cantidad*costoPromedio) calculados en cliente; seccion de alertas (bajo minimo/sobre maximo/por reabastecer) con enlace a Kardex/registrar entrada. Degradar si faltan datos/permiso.
  - _Requirements: 1.3, 7.1, 7.2, 7.3, 8.3_
- [ ] 6.2 Reorganizar la vista en secciones enterprise
  - Sustituir las 3 pestanas actuales por Resumen, Existencias, Movimientos, Kardex, Lotes, Configuracion; cada una gated por permiso; navegacion fluida (un clic) entre ellas.
  - _Requirements: 1.1, 1.4, 8.4_
- [ ] 6.3 Pruebas Resumen/Alertas
  - Indicadores calculados; alertas correctas; secciones gated; degradacion; axe.
  - _Requirements: 1.3, 1.4, 7.1, 7.2_

- [ ] 7. Verificacion integral y puesta en vivo
- [ ] 7.1 Build + pruebas frontend
  - `ng build` (0 errores) y specs tocadas en aislamiento (one-shot, sin watch). Backend solo si se toca.
  - _Requirements: 9.1, 9.2, 9.3_
- [ ] 7.2 Verificacion viva (tras deploy)
  - Crear almacenes; registrar entrada (ver costeo), salida (PEPS/promedio), transferencia; existencias con nombres + alertas; Kardex; configurar material; lotes. Confirmar CERO UUIDs visibles.
  - _Requirements: 1.2, 2.1, 3.1, 4.1, 5.1, 6.1_

## Task Dependency Graph

```
1.1 -> 1.2 -> 1.3
1.2, 2.1 -> 2.2 -> 2.3
2.1 -> 4.1 (kardex reusa mapas/selectores)
1.2 -> 3.1 -> 3.2 -> 3.3
2.2 -> 4.1 -> 4.2   (ver kardex desde existencias)
1.2 -> 5.1 ; 1.2 -> 5.2 ; (5.1,5.2) -> 5.3
2.2 -> 6.1 -> 6.3 ; (2,3,4,5) -> 6.2
todo -> 7.1 -> 7.2
```

```json
{
  "waves": [
    { "wave": 1, "tasks": ["1.1"] },
    { "wave": 2, "tasks": ["1.2", "2.1"] },
    { "wave": 3, "tasks": ["1.3", "2.2", "3.1", "5.1", "5.2"] },
    { "wave": 4, "tasks": ["2.3", "3.2", "4.1", "5.3", "6.1"] },
    { "wave": 5, "tasks": ["3.3", "4.2", "6.2"] },
    { "wave": 6, "tasks": ["6.3"] },
    { "wave": 7, "tasks": ["7.1"] },
    { "wave": 8, "tasks": ["7.2"] }
  ]
}
```

## Notes

- SIN backend nuevo (salvo que falte un GET de config y se decida agregarlo con prueba). Reutilizar
  los 13 endpoints existentes.
- SIN UUIDs visibles: resolver nombres por mapa; selectores entity-select; id no resuelto -> marcador
  neutro, jamas el UUID.
- El costeo lo calcula el backend (promedio/PEPS). La UI solo envia el movimiento y muestra el costo.
- Selector de Material = catalogo del Nucleo (MaterialesService), no paralelo.
- Fechas con datepicker es-MX ya estandarizado. Etiquetas de tipo/metodo en espanol centralizadas.
- Frontend: ng build + `ng test --watch=false --include=<spec>` one-shot; NO watch, NO full suite,
  NO ng serve; max dos reintentos por comando (evitar timeouts).
- Preservar contratos verificados del backend. Archivos UTF-8 sin BOM. Redeploy al servidor tras
  verificar en local.