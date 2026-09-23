# Requirements Document

_(Documento de requisitos - Giro de la Empresa visible y editable por el super_admin)_

## Introduction

Cada Empresa (tenant) pertenece a un Giro (vertical de negocio) que determina que recursos y modulos
de vertical le aplican. Hoy el Giro se asigna al CREAR la empresa (el alta exige `giroId`), pero en
el ambito del super_admin NO se VE el giro de una empresa existente (ni en el listado ni al editar),
y NO se puede CAMBIAR desde la interfaz. El backend ya tiene el servicio de dominio `cambiarGiro`
con sus reglas de negocio (el nuevo giro debe existir y estar activo; el cambio se rechaza si la
empresa ya tiene datos del vertical actual, para no dejar datos huerfanos), pero ese servicio NO
esta expuesto por un endpoint REST.

Esta especificacion cierra ese hueco: (1) MOSTRAR el giro de la empresa en las vistas del super_admin
(listado y/o edicion), por su NOMBRE (nunca el UUID); y (2) permitir CAMBIAR el giro desde el
super_admin mediante un endpoint REST que invoque el servicio ya existente, con una UI clara que
respete las reglas de negocio (incluido el mensaje cuando el cambio no es posible por datos del
vertical). Se preservan las reglas de la plataforma: solo el super_admin (permiso de plataforma),
espanol, WCAG AA, tokens, sin exponer UUIDs.

## Glossary

- **Giro (vertical):** actividad/rubro de negocio de una Empresa (p. ej. anuncios luminosos,
  carpinteria, manufactura). Determina los recursos de vertical aplicables.
- **Giro Base vs Completo:** un giro puede tener reglas de negocio/modulos especificos (Completo) o
  no (Base); el alta ya distingue esto informativamente.
- **Datos del vertical:** informacion de negocio que la empresa genero bajo su giro actual; su
  existencia bloquea el cambio de giro (regla del backend).
- **super_admin:** rol de plataforma (Dess-TI) que administra empresas, planes y giros.

## Requirements

### Requirement 1: Ver el Giro de la Empresa en el super_admin

**User Story:** Como super_admin, quiero ver a que giro pertenece cada empresa, para administrarlas
con contexto y detectar giros mal asignados.

#### Acceptance Criteria

1. EL listado de Empresas del super_admin DEBE mostrar el Giro de cada empresa por su NOMBRE
   (nombre visible), no por su identificador.
2. EL detalle/edicion de una Empresa DEBE mostrar el Giro actual de la empresa por su nombre.
3. EN NINGUN caso se DEBE mostrar el UUID del giro al usuario.
4. SI el giro de una empresa no se puede resolver a un nombre ENTONCES se DEBE mostrar un marcador
   neutro, nunca el UUID.

### Requirement 2: Endpoint REST para cambiar el Giro (backend)

**User Story:** Como plataforma, quiero exponer el cambio de giro por REST, para que el super_admin
pueda ejecutarlo desde la interfaz con las reglas de negocio ya definidas.

#### Acceptance Criteria

1. EL backend DEBE exponer un endpoint (p. ej. `PUT /empresas/{id}/giro`) que invoque el servicio
   de dominio `cambiarGiro` ya existente.
2. EL endpoint DEBE protegerse con un permiso de plataforma del super_admin (reutilizando
   `empresa:cambiar_estado`, ya sembrado y asignado a super_admin, coherente con activar/suspender/
   modulos); NO se introduce ningun permiso nuevo sin sembrar.
3. EL endpoint DEBE recibir el nuevo `giroId` en el cuerpo y devolver el `EmpresaDto` actualizado.
4. CUANDO el nuevo giro no existe o esta inactivo ENTONCES DEBE responder 422 con un mensaje claro.
5. CUANDO la empresa tiene datos del vertical de su giro actual ENTONCES el cambio DEBE rechazarse
   con 422 y un mensaje que lo explique (regla ya implementada en el servicio).
6. CUANDO la empresa no existe ENTONCES DEBE responder 404.
7. EL cambio DEBE auditarse (giro anterior y nuevo), como ya hace el servicio.

### Requirement 3: UI para cambiar el Giro desde el super_admin

**User Story:** Como super_admin, quiero cambiar el giro de una empresa desde la interfaz eligiendo
el nuevo giro de una lista, para corregir o reasignar el vertical sin tocar la base de datos.

#### Acceptance Criteria

1. LA vista de administracion de Empresas DEBE ofrecer una accion "Cambiar giro" para una empresa
   (gobernada por el permiso de plataforma correspondiente).
2. LA accion DEBE presentar un selector de Giros ACTIVOS por NOMBRE (sin UUID), con el giro actual
   indicado.
3. AL confirmar, DEBE invocar el endpoint y, en exito, reflejar el nuevo giro y notificar; en error
   (422 por giro invalido o por datos del vertical) DEBE mostrar el mensaje del backend sin romper
   la vista.
4. LA UI NO DEBE permitir teclear identificadores; el giro se elige de la lista.
5. LA accion DEBE estar disponible desde el listado o el detalle de la empresa de forma clara.

### Requirement 4: Calidad y no regresion

#### Acceptance Criteria

1. LOS cambios DEBEN compilar (backend y frontend) sin errores.
2. LA suite de pruebas existente DEBE permanecer en verde, agregando pruebas para el endpoint de
   cambio de giro y la UI.
3. LA UI DEBE respetar tokens, ser responsiva y cumplir WCAG AA; en espanol; SIN UUIDs visibles.
4. LOS cambios DEBEN preservar el comportamiento verificado (alta de empresa, editar datos, activar/
   suspender, modulos).