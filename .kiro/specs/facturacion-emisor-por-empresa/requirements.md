# Requirements Document

_(Documento de requisitos - Emisor por Empresa en documentos y autocompletado de direcciones)_

## Introduction

Esta especificacion corrige dos defectos observados en produccion de la plataforma
`plataforma-multigiro`, ambos relacionados con la experiencia enterprise de las Empresas
inquilinas (tenants):

1. **Emisor incorrecto en el PDF de Cotizacion (defecto conceptual).** El documento PDF que
   una Empresa genera para SU cliente muestra a **Dess-TI** como emisor (nombre, RFC y datos de
   contacto de la plataforma). Esto es incorrecto: Dess-TI es el proveedor del software que
   cobra la renta del sistema a las Empresas; jamas debe aparecer como emisor de los documentos
   comerciales que una Empresa emite a sus propios clientes. El emisor de esos documentos debe
   ser **la Empresa** (el tenant), con sus propios datos fiscales.

2. **Autocompletado de direcciones no funcional (defecto de UX).** El buscador de direcciones
   de los formularios (Cliente, Empresa, Mi empresa) devuelve "Sin resultados" para direcciones
   locales de Mexico y, ademas, su panel de sugerencias se traslapa visualmente con los campos
   siguientes (Ciudad / Calle y numero), rompiendo la apariencia enterprise.

Ambas correcciones deben preservar el comportamiento existente verificado (backend y frontend),
seguir el sistema de diseno (tokens), ser responsivas, cumplir WCAG AA y estar en espanol. No
deben mostrarse ni permitir capturar UUIDs al usuario.

## Glossary

- **Dess-TI / plataforma:** el proveedor del software (super_admin) que renta los modulos del
  sistema a las Empresas. Tiene su propia facturacion de renta (`FacturaRenta`) hacia las Empresas.
- **Empresa (tenant):** organizacion cliente que usa el sistema para operar su negocio y emitir
  sus propios documentos comerciales (cotizaciones, facturas) a SUS clientes.
- **Emisor:** la organizacion que emite un documento. En la factura de renta el emisor es Dess-TI;
  en una cotizacion/factura de una Empresa el emisor es esa Empresa.
- **Datos fiscales de la Empresa:** razon social (
ombre`), `rfc`, 
ombre_comercial`,
  `email_contacto`, `sitio_web` y direccion desglosada (`direccion_calle`, `direccion_ciudad`,
  `direccion_estado`, `direccion_cp`, `direccion_pais`), ya existentes en la tabla `empresa`
  (V1 + V54). No se requieren columnas nuevas.

## Requirements

### Requirement 1: El emisor del PDF de Cotizacion es la Empresa, nunca Dess-TI

**User Story:** Como administrador de una Empresa, quiero que las cotizaciones que
genero para mis clientes muestren los datos fiscales de MI empresa como emisor, para que el
documento sea profesional y no confunda a mi cliente con el proveedor del software.

#### Acceptance Criteria

1. CUANDO una Empresa genera el PDF de una Cotizacion ENTONCES el bloque "EMISOR" DEBE mostrar
   los datos fiscales de la Empresa del contexto autenticado (tenant), NO los de Dess-TI.
2. EL nombre del emisor DEBE ser la razon social de la Empresa (`empresa.nombre`).
3. SI la Empresa tiene 
ombre_comercial` ENTONCES el PDF DEBE mostrarlo como subtitulo/linea
   secundaria bajo la razon social.
4. EL bloque emisor DEBE incluir, cuando esten presentes: RFC, direccion fiscal (armada a partir
   de calle, ciudad, estado, CP, pais), correo (`email_contacto`) y sitio web (`sitio_web`).
5. CUANDO alguno de esos datos fiscales este vacio ENTONCES el PDF DEBE OMITIR esa linea sin
   romper el layout (degradacion elegante), mostrando solo lo que la Empresa tenga capturado.
6. EN NINGUN caso el PDF de Cotizacion de una Empresa DEBE mostrar el nombre "Dess-TI", su RFC
   (`DTI200101AB1`) ni sus datos de contacto de plataforma.
7. LA resolucion del emisor DEBE derivarse del `tenant_id` del contexto autenticado (Req 23.4),
   nunca de la peticion ni de la configuracion global de plataforma (`crm.facturacion.emisor.*`).

### Requirement 2: La factura de renta de Dess-TI conserva a Dess-TI como emisor

**User Story:** Como Dess-TI (super_admin), quiero que MI comprobante de renta hacia las
Empresas siga mostrando a Dess-TI como emisor, porque ese documento SI lo emito yo al cobrar el
servicio del sistema.

#### Acceptance Criteria

1. CUANDO se genera el PDF de una `FacturaRenta` (comprobante de renta de modulos) ENTONCES el
   emisor DEBE seguir siendo Dess-TI (la configuracion global `crm.facturacion.emisor.*`).
2. LA correccion del Requisito 1 NO DEBE alterar el comportamiento ni el contenido del PDF de
   `FacturaRenta`.
3. LOS dos flujos (factura de renta de plataforma vs. cotizacion de Empresa) DEBEN quedar
   claramente separados: el emisor de plataforma solo se usa para la factura de renta.

### Requirement 3: Aviso al administrador para completar datos fiscales

**User Story:** Como administrador de una Empresa, quiero que el sistema me avise si mis
datos fiscales estan incompletos, para completarlos y que mis cotizaciones salgan profesionales.

#### Acceptance Criteria

1. CUANDO una Empresa carece de RFC o de direccion fiscal al generar/enviar una Cotizacion
   ENTONCES el sistema DEBE seguir generando el PDF con los datos disponibles (no bloquea) Y
   DEBE informar de forma no intrusiva al usuario que complete los datos fiscales de la Empresa.
2. EL aviso DEBE indicar donde completar esos datos (perfil de "Mi empresa" para el admin de la
   Empresa; edicion de Empresa para el super_admin).
3. EL aviso NO DEBE mostrar UUIDs ni terminologia tecnica interna.

### Requirement 4: El autocompletado de direcciones devuelve sugerencias utiles

**User Story:** Como usuario que captura una direccion, quiero escribir una calle o
ciudad y ver sugerencias que rellenen automaticamente calle, ciudad, estado, codigo postal y pais,
para capturar direcciones rapido y sin errores.

#### Acceptance Criteria

1. CUANDO el usuario escribe al menos el minimo de caracteres significativos en el buscador de
   direccion ENTONCES el sistema DEBE consultar el servicio de geocoding y mostrar sugerencias
   relevantes, incluyendo direcciones de Mexico.
2. LA consulta DEBE ser tolerante a acentos, mayusculas/minusculas y espacios extra en el texto.
3. CUANDO el usuario elige una sugerencia ENTONCES los campos calle y numero, ciudad, estado,
   codigo postal y pais DEBEN rellenarse con los datos de esa sugerencia.
4. CUANDO el servicio no devuelva coincidencias ENTONCES el sistema DEBE mostrar un estado
   "Sin resultados" claro Y permitir que el usuario capture la direccion manualmente sin bloqueo.
5. ANTE un error de red o del servicio ENTONCES el sistema DEBE degradar sin lanzar (lista vacia)
   y permitir la captura manual.
6. EL servicio de geocoding DEBE ser gratuito y sin clave de API (OpenStreetMap), invocado desde
   el navegador sin pasar por el proxy `/api` de la plataforma.

### Requirement 5: El panel de sugerencias no se traslapa con otros campos

**User Story:** Como usuario, quiero que la lista de sugerencias de direccion se muestre
por encima del formulario sin encimarse con los campos siguientes, para leer y elegir con
claridad.

#### Acceptance Criteria

1. CUANDO se muestran sugerencias de direccion ENTONCES el panel DEBE renderizarse por ENCIMA de
   los campos siguientes (Ciudad, Calle y numero, etc.), sin traslape visual.
2. EL panel DEBE tener un fondo solido (no transparente) y un z-index adecuado para no dejar ver
   el contenido de abajo a traves de el.
3. EL panel DEBE ser accesible por teclado (navegar con flechas, elegir con Enter, cerrar con Esc)
   y cumplir WCAG AA (contraste, roles/aria de las opciones).
4. EL comportamiento DEBE ser consistente en TODOS los formularios que usan el autocompletado
   (Cliente, crear/editar Empresa, perfil de Mi empresa) y responsivo en movil.

### Requirement 6: Preservacion de calidad y no regresion

#### Acceptance Criteria

1. LOS cambios DEBEN compilar sin errores (backend `mvn package`; frontend 
g build`).
2. LA suite de pruebas existente DEBE permanecer en verde (backend ~1115+; frontend), agregando
   pruebas para el emisor por Empresa y el autocompletado.
3. LOS documentos generados DEBEN respetar tokens del sistema de diseno, ser responsivos y cumplir
   WCAG AA; toda la UI y textos en espanol; sin mostrar UUIDs al usuario.