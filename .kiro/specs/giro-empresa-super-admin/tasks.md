# Implementation Plan

## Overview

Exponer el cambio de giro por REST (el servicio ya existe) y mostrar/editar el giro en el super_admin.
Cross-stack acotado, sin migracion ni permiso nuevo.

## Tasks

- [ ] 1. Backend: endpoint para cambiar el Giro
- [ ] 1.1 `CambiarGiroRequest` + endpoint `PUT /empresas/{id}/giro`
  - Crear el record `CambiarGiroRequest { @NotNull UUID giroId }` en el paquete rest de empresas. Agregar en `EmpresaController` el `@PutMapping("/{id}/giro")` con `@PreAuthorize("@autorizador.tiene('empresa','cambiar_estado')")` que llama `servicioEmpresas.cambiarGiro(id, request.giroId())` y devuelve `EmpresaDto`. Mirar el patron de activar/suspender/actualizarModulos. No tocar el servicio ni la BD.
  - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.5, 2.6, 2.7_
- [ ] 1.2 Prueba de rebanada del controlador
  - `PUT /empresas/{id}/giro`: 200 con el DTO (servicio mockeado); 403 sin permiso; 422 cuando el servicio lanza ReglaNegocio (giro invalido o datos del vertical); 404 cuando lanza RecursoNoEncontrado. No re-testear la logica del servicio.
  - _Requirements: 2.2, 2.4, 2.5, 2.6_

- [ ] 2. Frontend: servicio y resolucion de nombre de giro
- [ ] 2.1 `EmpresasService.cambiarGiro(id, giroId)`
  - PUT `/empresas/{id}/giro` con `{ giroId }` -> Empresa.
  - _Requirements: 2.1, 3.3_
- [ ] 2.2 Resolucion id->nombre de giro en la vista de empresas
  - Cargar giros activos (GirosService.listar) una vez; util `nombreGiro(id)` que devuelve el nombre visible o un marcador neutro (nunca el UUID).
  - _Requirements: 1.1, 1.2, 1.3, 1.4_
- [ ] 2.3 Pruebas del servicio
  - `cambiarGiro` hace el PUT correcto con `{ giroId }`.
  - _Requirements: 2.1_

- [ ] 3. Frontend: mostrar y cambiar el giro en el super_admin
- [ ] 3.1 Mostrar el giro en el listado/detalle
  - Columna/campo "Giro" con el nombre del giro (no UUID) en la vista de Empresas del super_admin.
  - _Requirements: 1.1, 1.2, 1.3_
- [ ] 3.2 Accion "Cambiar giro"
  - Accion (dialog/panel) con `mat-select` de giros ACTIVOS por nombre, con el actual indicado; al confirmar llama `cambiarGiro`; exito -> actualiza fila + notifica; error 422 -> muestra el mensaje del backend sin romper. Gated por el permiso de plataforma que ya usa la pantalla para acciones sensibles. Sin UUIDs.
  - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5_
- [ ] 3.3 Pruebas de la vista
  - Muestra el nombre del giro; la accion abre el selector y llama al servicio; 422 muestra mensaje; sin permiso no aparece; axe WCAG.
  - _Requirements: 1.1, 3.1, 3.2, 3.3, 4.2_

- [ ] 4. Verificacion integral
- [ ] 4.1 Build + pruebas backend
  - `mvn -o test -Dtest=EmpresaControllerTest` (Start-Process one-shot, leer log) y build; suite en verde.
  - _Requirements: 4.1, 4.2_
- [ ] 4.2 Build + pruebas frontend
  - `ng build` (0 errores) y specs tocadas en aislamiento (one-shot, sin watch).
  - _Requirements: 4.1, 4.2, 4.3_
- [ ] 4.3 Verificacion viva (local)
  - Ver el giro de una empresa; cambiar el giro de una empresa sin datos de vertical (exito); intentar en una con datos (422 claro).
  - _Requirements: 1.1, 3.2, 3.3_

## Task Dependency Graph

```
1.1 -> 1.2
2.1 -> 2.3 ; 2.2 -> 3.1
2.1, 2.2 -> 3.2 -> 3.3
todo -> 4.1, 4.2 -> 4.3
```

```json
{
  "waves": [
    { "wave": 1, "tasks": ["1.1", "2.1", "2.2"] },
    { "wave": 2, "tasks": ["1.2", "2.3", "3.1"] },
    { "wave": 3, "tasks": ["3.2"] },
    { "wave": 4, "tasks": ["3.3"] },
    { "wave": 5, "tasks": ["4.1", "4.2"] },
    { "wave": 6, "tasks": ["4.3"] }
  ]
}
```

## Notes

- El servicio `cambiarGiro` YA existe: NO reimplementar su logica; solo exponer el endpoint.
- Reutilizar el permiso `empresa:cambiar_estado` (super_admin). Sin permiso ni migracion nuevos.
- Sin UUIDs visibles: giro por nombre; id no resuelto -> marcador neutro.
- Frontend: ng build + `ng test --watch=false --include=<spec>` one-shot; NO watch, NO full suite,
  NO ng serve. Backend: mvn -o (evitar clean); no relanzar la app; no matar el LSP.
- Preservar el comportamiento verificado (alta, editar datos, activar/suspender, modulos).
- Archivos UTF-8 sin BOM.