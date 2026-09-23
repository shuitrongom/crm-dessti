# Requirements Document

_(Documento de requisitos - Integracion enterprise de los bloques Comercial, Redes Sociales y Estrategia)_

## Introduction

Los tres bloques listos para pruebas (Comercial/CRM, Redes Sociales y Estrategia) funcionan hoy de
forma aislada: cada pantalla opera bien por si sola, pero NO cuentan una historia de negocio
conectada. En una plataforma enterprise, los modulos deben relacionarse: un cliente muestra su
actividad comercial, una conversacion de redes puede convertirse en un lead, una oportunidad fluye
a cotizacion, y la estrategia se nutre de resultados reales.

El backend ya tiene MUCHOS de estos enlaces a nivel de datos (cliente->oportunidad->cotizacion,
oportunidad/cotizacion->canal de venta, partida->producto, producto<->lista de precios con
sugerencia de precio automatica), pero la INTERFAZ no los expone: el usuario no ve la "ficha 360"
del cliente, no asigna/filtra canal desde la UI, ni percibe la trazabilidad entre pantallas.

Esta especificacion "alimenta" y CONECTA los submenus de los tres bloques para que cada pantalla se
relacione con las demas de forma natural y util, priorizando aprovechar lo que ya existe en backend
y agregando lo minimo necesario. Se preservan las reglas de la plataforma: multi-tenant con
aislamiento por Empresa, RBAC por permiso atomico y modulo contratado, UI en espanol, responsiva,
WCAG AA, tokens del sistema de diseno, sin exponer UUIDs ni credenciales al usuario.

## Glossary

- **Ficha 360 del Cliente:** vista de un cliente que reune, ademas de sus datos, sus contactos,
  oportunidades, cotizaciones y (si aplica) conversaciones de redes asociadas.
- **Pipeline:** embudo de oportunidades por etapa.
- **Conversion:** transformar una oportunidad ganada en una cotizacion (ya existe en backend).
- **Canal de venta:** etiqueta de origen/via comercial (Directo, Referido, Redes, etc.) para
  clasificar y segmentar oportunidades/cotizaciones.
- **Lead social:** cliente/oportunidad creado a partir de una conversacion de redes sociales.
- **OKR:** objetivos y resultados clave del bloque Estrategia.

## Requirements

### Requirement 1: Ficha 360 del Cliente (Comercial conectado a si mismo)

**User Story:** Como usuario comercial, quiero abrir un cliente y ver toda su actividad (contactos,
oportunidades y cotizaciones) en un solo lugar, para entender la relacion completa sin saltar entre
pantallas.

#### Acceptance Criteria

1. CUANDO se consulta un Cliente ENTONCES la vista DEBE mostrar, ademas de sus datos, sus
   Contactos, sus Oportunidades y sus Cotizaciones asociadas (listas resumidas).
2. CADA elemento relacionado DEBE permitir navegar a su detalle (p. ej. abrir la oportunidad o la
   cotizacion) sin teclear identificadores.
3. LA vista DEBE mostrar indicadores utiles del cliente (p. ej. numero de oportunidades abiertas,
   valor total en pipeline, ultima cotizacion) cuando los datos esten disponibles.
4. LAS listas relacionadas DEBEN respetar el permiso del usuario: si no puede listar cotizaciones,
   esa seccion no se muestra.
5. LA vista NO DEBE exponer UUIDs; las relaciones se muestran por nombre/folio/titulo.

### Requirement 2: Oportunidad conectada (canal, responsable y salto a cotizacion)

**User Story:** Como vendedor, quiero clasificar mi oportunidad por canal de venta y saltar a su
cotizacion desde la misma pantalla, para trabajar el pipeline de forma fluida.

#### Acceptance Criteria

1. LA vista de Oportunidades DEBE permitir asignar/cambiar el Canal_Venta de una oportunidad desde
   la UI (selector, sin UUID) usando el endpoint ya existente.
2. LA vista DEBE permitir FILTRAR el pipeline por Canal_Venta (y conservar los filtros existentes).
3. CUANDO una oportunidad ya fue convertida ENTONCES la vista DEBE ofrecer un acceso directo a su
   Cotizacion generada.
4. LA vista DEBE mostrar el Cliente de cada oportunidad como enlace navegable a su ficha 360.
5. LAS acciones DEBEN respetar la separacion de funciones: aprobar/avanzar etapa segun permiso; un
   rol operativo no aprueba lo que no le corresponde.

### Requirement 3: Cotizacion conectada (cliente, origen y catalogo)

**User Story:** Como usuario comercial, quiero que la cotizacion muestre de donde viene (oportunidad
y canal) y que las partidas se apoyen en el catalogo de productos y precios, para armarla rapido y
con trazabilidad.

#### Acceptance Criteria

1. EL detalle de una Cotizacion DEBE mostrar su Cliente (enlace a ficha 360) y, si proviene de una
   conversion, la Oportunidad de origen (enlace).
2. LA vista DEBE permitir asignar/mostrar el Canal_Venta de la cotizacion (sin UUID).
3. CUANDO se agrega una partida eligiendo un Producto y se deja el precio vacio ENTONCES el sistema
   DEBE sugerir el precio desde la Lista_Precios vigente (comportamiento ya soportado por el
   backend), y la UI DEBE dejarlo claro al usuario.
4. EL detalle DEBE conservar las acciones existentes (PDF, correo, WhatsApp, aprobacion por rol).

### Requirement 4: Producto conectado a precios y su uso comercial

**User Story:** Como responsable de catalogo, quiero ver desde un producto sus precios por lista y
entender su disponibilidad para cotizar, para mantener el catalogo coherente.

#### Acceptance Criteria

1. LA vista de Producto DEBE mostrar los precios del producto por Lista_Precios (si el usuario tiene
   permiso de listar precios).
2. LA vista de Listas de precios DEBE permitir asignar precio a un producto mediante selector (sin
   UUID), reflejando la relacion producto<->lista.
3. LOS productos DEBEN ser seleccionables al armar partidas de cotizacion (ya soportado); la UI
   DEBE mantener esa conexion clara.

### Requirement 5: Redes Sociales conectado al CRM (lead social)

**User Story:** Como agente de redes, quiero convertir una conversacion en un cliente/oportunidad o
vincularla a uno existente, para no perder oportunidades que llegan por redes.

#### Acceptance Criteria

1. DESDE la Bandeja (o el detalle de una Conversacion) el sistema DEBE permitir VINCULAR la
   conversacion a un Cliente existente (selector, sin UUID) o CREAR un Cliente/Oportunidad a partir
   de ella (lead social), respetando permisos.
2. CUANDO una conversacion se vincula/convierte ENTONCES la relacion DEBE quedar registrada de modo
   que la ficha 360 del Cliente pueda reflejar el origen social.
3. LA vista de Publicaciones/Campanas DEBE poder asociarse (opcional) a un Canal_Venta para
   segmentar el origen comercial, cuando aplique.
4. SI el modulo redes-sociales no esta contratado ENTONCES estas conexiones no se muestran (gating).
5. NINGUNA conexion DEBE exponer credenciales ni UUIDs.

### Requirement 6: Estrategia conectada a resultados reales

**User Story:** Como direccion, quiero que los objetivos estrategicos se relacionen con datos reales
del negocio (p. ej. pipeline comercial), para que la estrategia refleje la operacion y no sea solo
texto.

#### Acceptance Criteria

1. LA vista de Estrategia/OKR DEBE poder mostrar indicadores derivados de la operacion comercial
   disponible (p. ej. valor total en pipeline, cotizaciones del periodo) como apoyo a los
   resultados clave, cuando el modulo comercial este contratado.
2. CUANDO el modulo comercial NO este contratado ENTONCES la estrategia DEBE seguir funcionando
   solo con captura manual (degradacion; sin romper).
3. LA pantalla de Inicio DEBE reflejar de forma coherente la estrategia capturada y, si hay datos,
   un resumen comercial (conectando Estrategia con Comercial).
4. LOS indicadores mostrados NO DEBEN exponer datos de otras empresas (multi-tenant) ni UUIDs.

### Requirement 7: Navegacion y trazabilidad transversal

**User Story:** Como usuario, quiero moverme entre entidades relacionadas con un clic, para navegar
la informacion como en un sistema enterprise.

#### Acceptance Criteria

1. LOS enlaces entre entidades (cliente<->oportunidad<->cotizacion, producto<->precio,
   conversacion<->cliente) DEBEN ser navegables con un clic, sin teclear identificadores.
2. LA navegacion DEBE respetar el gating por modulo y por permiso en cada salto.
3. LOS estados de carga, vacio y error DEBEN usar los componentes compartidos y ser accesibles.

### Requirement 8: Calidad y no regresion

#### Acceptance Criteria

1. LOS cambios DEBEN compilar (backend y frontend) sin errores.
2. LA suite de pruebas existente DEBE permanecer en verde, agregando pruebas para las nuevas
   relaciones y vistas.
3. LA UI DEBE respetar tokens, ser responsiva y cumplir WCAG AA; en espanol; sin UUIDs ni
   credenciales visibles.
4. LOS cambios DEBEN preservar el comportamiento ya verificado en produccion de los tres bloques.