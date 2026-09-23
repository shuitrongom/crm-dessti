# Requirements Document

_(Documento de requisitos - Redes sociales: conexiones de cuentas y canales ampliados)_

## Introduction

El modulo Social/Omnicanal de `plataforma-multigiro` tiene cuatro vistas (Bandeja, Publicaciones,
Campanas, Analitica) pero HOY se ven vacias porque no existe una pantalla para CONECTAR las
cuentas de redes sociales de la Empresa. El backend ya expone el endpoint para registrar cuentas
(`POST /social/cuentas-canal`), pero el frontend nunca construyo la vista. Ademas, el dominio de
canales solo soporta tres redes (WhatsApp, Messenger, Instagram) y el negocio necesita ademas
**Facebook** y **TikTok**.

Esta especificacion: (1) amplia los canales soportados a cinco (WhatsApp, Facebook, Instagram,
Messenger, TikTok); (2) agrega una pantalla de "Conexiones" para que el administrador registre y
gestione las cuentas de cada red; (3) reorganiza el modulo social y extiende los filtros por canal
en Publicaciones, Campanas y Analitica; y (4) deja el punto de integracion real (APIs oficiales)
claramente PREPARADO para cuando la Empresa disponga de credenciales, sin bloquear el uso interno.

Se preservan las reglas de la plataforma: multi-tenant con aislamiento por Empresa, RBAC por
permiso atomico y modulo contratado (`redes-sociales`), UI en espanol, responsiva, WCAG AA, tokens
del sistema de diseno, sin exponer credenciales ni UUIDs al usuario.

## Glossary

- **Canal_Social:** red social soportada (WhatsApp, Facebook, Instagram, Messenger, TikTok).
- **Cuenta_Canal_Social (Conexion):** la cuenta/pagina/perfil de la Empresa en un Canal_Social
  (por ejemplo, un numero de WhatsApp Business, una pagina de Facebook, un perfil de Instagram o
  TikTok), con un identificador externo, un nombre y una referencia de credencial (nunca la
  credencial en claro).
- **Publicacion_Social:** contenido programado/publicado por una Cuenta_Canal_Social.
- **Campana_Publicitaria:** campana de pago con presupuesto y periodo, opcionalmente ligada a un
  Canal_Social.
- **Bandeja_Unificada:** mensajeria entrante/saliente de las cuentas conectadas.
- **Referencia de credencial:** apuntador (por ejemplo, el nombre de un secreto) a las credenciales
  gestionadas fuera de la base de datos; el sistema NUNCA almacena ni muestra la credencial.

## Requirements

### Requirement 1: Canales soportados ampliados a cinco redes

**User Story:** Como administrador de una Empresa, quiero conectar WhatsApp, Facebook, Instagram,
Messenger y TikTok, para gestionar todas mis redes desde un solo lugar.

#### Acceptance Criteria

1. EL sistema DEBE soportar los canales: WhatsApp, Facebook, Instagram, Messenger y TikTok.
2. CUANDO se registra una Cuenta_Canal_Social ENTONCES el canal DEBE poder ser cualquiera de los
   cinco.
3. LOS filtros y selectores por canal en Publicaciones, Campanas y Analitica DEBEN ofrecer los
   cinco canales.
4. CADA canal DEBE mostrarse con una etiqueta legible en espanol y un icono representativo; el
   color NO DEBE ser el unico portador de significado (WCAG AA).
5. LA ampliacion NO DEBE romper las cuentas/publicaciones/campanas existentes de los tres canales
   previos (migracion no destructiva).

### Requirement 2: Pantalla de Conexiones (gestion de cuentas de red)

**User Story:** Como administrador de una Empresa, quiero una pantalla donde conectar y ver mis
cuentas de redes sociales, para que el resto del modulo (publicaciones, bandeja, analitica) tenga
cuentas con las cuales operar.

#### Acceptance Criteria

1. EL modulo social DEBE incluir una vista "Conexiones" que liste las Cuentas_Canal_Social del
   tenant, mostrando canal (icono + etiqueta), nombre, identificador externo y estado (activa).
2. LA vista DEBE permitir registrar una nueva conexion capturando: canal (los cinco), nombre,
   identificador externo y una referencia de credencial; y enviarla a `POST /social/cuentas-canal`.
3. LA vista NO DEBE mostrar ni pedir la credencial en claro; solo su referencia (Req 11 de la
   plataforma).
4. CUANDO ya existe una cuenta para el mismo canal e identificador ENTONCES el sistema DEBE
   informar el conflicto (409) con un mensaje claro, sin duplicar.
5. LA vista DEBE presentar estados de carga, vacio y error mediante los componentes compartidos,
   ser responsiva y cumplir WCAG AA, en espanol y sin UUIDs visibles.
6. EL acceso a la vista DEBE estar protegido por el modulo contratado `redes-sociales` y el permiso
   atomico correspondiente (`cuenta_canal_social:listar` para ver; `crear` para registrar).

### Requirement 3: Reorganizacion del modulo social

**User Story:** Como usuario del modulo social, quiero un menu claro y ordenado, para encontrar
rapido cada funcion.

#### Acceptance Criteria

1. EL menu del modulo social DEBE ordenar las vistas de forma logica: Conexiones primero (es el
   punto de entrada), luego Publicaciones, Bandeja, Campanas y Analitica.
2. CUANDO la Empresa no tiene ninguna conexion ENTONCES Publicaciones/Bandeja/Campanas/Analitica
   DEBEN mostrar un estado vacio con una llamada a la accion clara que lleve a "Conexiones".
3. LA navegacion DEBE respetar el gating por modulo y permiso ya existente.

### Requirement 4: Publicaciones, Campanas y Analitica reconocen los cinco canales

**User Story:** Como usuario del modulo social, quiero que las publicaciones, campanas y analitica
funcionen con las cinco redes, para operar Facebook y TikTok igual que las demas.

#### Acceptance Criteria

1. LA vista Publicaciones DEBE permitir elegir una cuenta emisora de cualquiera de los cinco
   canales y filtrar por los cinco.
2. LA vista Campanas DEBE permitir asociar (opcional) cualquiera de los cinco canales y filtrar por
   ellos.
3. LA vista Analitica DEBE poder segmentar/mostrar metricas por los cinco canales.
4. EL alta de una Publicacion_Social DEBE seguir requiriendo una cuenta conectada (si no hay
   cuentas, el alta permanece deshabilitada con una indicacion de conectar una cuenta primero).

### Requirement 5: Integracion real preparada (no bloqueante)

**User Story:** Como due#o del negocio, quiero que la conexion real con las APIs oficiales quede
preparada, para activarla cuando tenga las credenciales de cada plataforma, sin rehacer la app.

#### Acceptance Criteria

1. EL registro de una conexion DEBE aceptar una referencia de credencial que apunte a las
   credenciales gestionadas fuera de la BD, dejando el punto de integracion listo.
2. LA publicacion y el envio reales hacia cada red DEBEN estar encapsulados tras un puerto/adaptador
   de forma que se pueda cablear el proveedor real (Meta Graph API para WhatsApp/Facebook/Instagram/
   Messenger; API de TikTok) sin cambiar las vistas.
3. MIENTRAS no haya proveedor real cableado, el sistema DEBE degradar de forma controlada
   (registrar el intento) sin romper el flujo, y el documento DEBE indicar QUE credenciales se
   necesitan por plataforma.

### Requirement 6: Calidad y no regresion

#### Acceptance Criteria

1. LOS cambios DEBEN compilar (backend `mvn package`; frontend `ng build`) sin errores.
2. LA suite de pruebas existente DEBE permanecer en verde, agregando pruebas para el nuevo canal,
   la pantalla de Conexiones y los filtros ampliados.
3. LA UI DEBE respetar tokens, ser responsiva y cumplir WCAG AA; en espanol; sin UUIDs visibles;
   sin exponer credenciales.