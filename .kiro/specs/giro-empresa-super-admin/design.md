# Design Document

_(Documento de diseno - Giro de la Empresa visible y editable por el super_admin)_

## Overview

Cambio acotado cross-stack que aprovecha lo ya existente:
- BACKEND: el servicio `ServicioEmpresas.cambiarGiro(empresaId, nuevoGiroId)` YA existe con todas
  las reglas (giro activo; rechazo si hay datos del vertical actual; auditoria). Solo falta EXPONERLO
  con un endpoint REST en `EmpresaController`. El `EmpresaDto` ya incluye `giroId`.
- FRONTEND: el modelo `Empresa` ya trae `giroId`; existe `GirosService.listar` y `EmpresasService`.
  Falta: (a) MOSTRAR el nombre del giro en el listado/edicion del super_admin (resolviendo id->nombre
  contra el catalogo de giros), y (b) una accion "Cambiar giro" con selector de giros activos que
  llame al nuevo endpoint.

No hay migracion nueva ni permiso nuevo (se reutiliza `empresa:cambiar_estado`, como activar/
suspender/modulos).

## Architecture

```
BACKEND
  PUT /empresas/{id}/giro   body { giroId }   [empresa:cambiar_estado]
    -> ServicioEmpresas.cambiarGiro(id, giroId)   (YA existe)
       - valida giro activo (422), rechaza si hay datos del vertical actual (422), 404 si no existe
       - audita giro anterior/nuevo
    -> devuelve EmpresaDto (ya incluye giroId)

FRONTEND (features/plataforma/empresas)
  GirosService.listar(activo=true) -> mapa giroId -> nombreVisible (para mostrar sin UUID)
  Listado/edicion: muestra nombre del giro (no UUID)
  Accion "Cambiar giro": dialog con select de giros activos (por nombre) -> EmpresasService.cambiarGiro(id, giroId)
```

## Components and Interfaces

### Backend
- `CambiarGiroRequest` (record): `{ @NotNull UUID giroId }` en el paquete rest de empresas.
- `EmpresaController`: nuevo `@PutMapping("/{id}/giro")` con
  `@PreAuthorize("@autorizador.tiene('empresa','cambiar_estado')")` que llama
  `servicioEmpresas.cambiarGiro(id, request.giroId())` y devuelve `EmpresaDto`. Sigue el patron de
  `actualizarModulos`/`activar`/`suspender` (mismos imports, misma forma de respuesta).
- No se toca el servicio (ya implementado). No se toca la BD.

### Frontend
- `EmpresasService.cambiarGiro(id: string, giroId: string): Observable<Empresa>` -> PUT
  `/empresas/{id}/giro` con `{ giroId }`.
- Resolucion de nombre de giro: la vista de empresas carga los giros activos (GirosService.listar)
  una vez y arma `Map<giroId, nombreVisible>`; una util `nombreGiro(id)` devuelve el nombre o un
  marcador neutro (nunca el UUID). Se usa en la columna/campo "Giro" del listado y del detalle.
- Accion "Cambiar giro": un dialog (o panel) que:
  - recibe la empresa (con su `giroId` actual),
  - muestra un `mat-select` de giros ACTIVOS por nombre, con el actual preseleccionado/indicado,
  - al confirmar invoca `cambiarGiro`; en exito recarga/actualiza la fila y notifica; en error 422
    muestra el mensaje del backend (mensajeDeError) sin romper.
  - gated por el permiso de plataforma que ya usa la pantalla para acciones sensibles
    (el super_admin lo tiene; reutiliza el patron de activar/suspender de la vista de empresas).
- Mostrar el giro tambien en el dialogo de EDITAR empresa como dato (solo lectura) o via la accion
  "Cambiar giro" desde el listado — se elige la ubicacion mas coherente con la pantalla actual
  (preferible: columna "Giro" en el listado + accion "Cambiar giro"). No se convierte el giro en un
  campo del formulario de datos descriptivos (ese PUT no cambia giro por diseno).

## Data Models

Sin cambios de datos. `EmpresaDto.giroId` ya existe; `Giro` tiene id + nombreVisible + clave +
activo. No hay migracion.

## Error Handling

- Giro inexistente/inactivo -> 422; la UI muestra el mensaje.
- Empresa con datos del vertical actual -> 422 con mensaje explicativo; la UI lo muestra y no cambia.
- Empresa inexistente -> 404.
- Nombre de giro no resuelto en el mapa -> marcador neutro, nunca UUID.

## Testing Strategy

### Backend
- Slice del controlador (`EmpresaControllerTest` o nuevo): `PUT /empresas/{id}/giro` 200 con el DTO
  (servicio mockeado), 403 sin permiso, 422 cuando el servicio lanza ReglaNegocio (giro invalido o
  datos del vertical), 404 cuando lanza RecursoNoEncontrado. No se re-testea la logica del servicio
  (ya cubierta).

### Frontend
- `EmpresasService` spec: `cambiarGiro` hace PUT a `/empresas/{id}/giro` con `{ giroId }`.
- Vista de empresas spec: muestra el nombre del giro (no UUID); la accion "Cambiar giro" abre el
  selector y al confirmar llama al servicio; un 422 muestra el mensaje; sin permiso la accion no
  aparece; axe WCAG.
- Specs existentes de empresas/crear/editar permanecen verdes.

## Verification

- Backend: `mvn -o test -Dtest=EmpresaControllerTest` + build.
- Frontend: `ng build` (0 errores) + specs tocadas en aislamiento (one-shot, sin watch).
- Vivo: como super_admin, ver el giro de una empresa en el listado; cambiar el giro de una empresa
  SIN datos de vertical (exito); intentar cambiarlo en una con datos (mensaje 422 claro).

## Correctness Properties

### Property 1: Giro visible sin UUID - toda vista del super_admin que muestre el giro de una empresa
  lo presenta por su nombre; un id no resuelto cae a un marcador neutro, jamas el UUID.

**Validates: Requirements 1.1, 1.2, 1.3, 1.4**

### Property 2: Cambio de giro delega en el servicio - el endpoint solo invoca `cambiarGiro`; todas
  las reglas (giro activo, rechazo por datos del vertical, auditoria) las impone el servicio; el
  endpoint no las reimplementa.

**Validates: Requirements 2.1, 2.5, 2.7**

### Property 3: Errores propagados con fidelidad - un giro invalido/inactivo o datos del vertical
  producen 422 y la UI muestra el mensaje del backend; una empresa inexistente produce 404.

**Validates: Requirements 2.4, 2.5, 2.6, 3.3**

### Property 4: Solo super_admin - el endpoint exige el permiso de plataforma; un rol de empresa
  recibe 403 y la accion no se ofrece en su UI.

**Validates: Requirements 2.2, 3.1**