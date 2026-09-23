# Design Document

_(Documento de diseno - Emisor por Empresa en documentos y autocompletado de direcciones)_

## Overview

Se corrigen dos defectos independientes con cambios acotados y sin migraciones nuevas:

1. **Emisor del PDF de Cotizacion.** Hoy `ServicioCotizaciones.generarPdf` inyecta el
   `EmisorProperties` global (datos de plataforma = Dess-TI) al `CotizacionPdfService`. El
   diseno introduce un objeto de valor `DatosEmisor` derivado de la **Empresa del contexto**
   (tenant) y lo pasa al generador de PDF en lugar del emisor de plataforma. El
   `FacturaRentaPdfService` (comprobante de renta de Dess-TI) NO se toca: sigue usando
   `EmisorProperties`. Asi quedan claramente separados los dos flujos.

2. **Autocompletado de direcciones.** Se robustece la consulta a Photon (normalizacion del
   termino, parametros mas tolerantes) y se corrige la presentacion del panel de sugerencias
   (fondo solido + z-index + que se despliegue por encima sin empujar los campos siguientes),
   garantizando accesibilidad por teclado y consistencia en todos los formularios.

Ambos cambios preservan el comportamiento verificado, usan tokens del sistema de diseno, son
responsivos, cumplen WCAG AA y estan en espanol. No se muestran UUIDs al usuario.

## Architecture

```
COTIZACION PDF (flujo de Empresa/tenant)
  CotizacionController.descargarPdf
    -> ServicioCotizaciones.generarPdf(cotizacionId)
         - carga Cotizacion (RLS por tenant)
         - resuelve DatosCliente (puerto existente)
         - NUEVO: resuelve DatosEmisor de la Empresa del tenant
                  via EmpresaEmisorPort.emisorDeTenant(tenantId)
         -> CotizacionPdfService.generar(cotizacion, cliente, datosEmisor)

FACTURA DE RENTA (flujo de plataforma/Dess-TI) -- SIN CAMBIOS
  FacturaRentaController.descargarPdf
    -> ServicioFacturacionRenta.generarPdf
         -> FacturaRentaPdfService.generar(factura, empresa, EmisorProperties)  // Dess-TI
```

Puerto de dominio nuevo (hexagonal, coherente con `DatosClientePort`):

- `EmpresaEmisorPort` (en el paquete de aplicacion de cotizacion) con
  `Optional<DatosEmisor> emisorDeTenant(UUID tenantId)`.
- Adaptador `EmpresaEmisorAdapter` (en el modulo de plataforma/empresa) que lee la tabla
  `empresa` por su id (= tenant_id) y mapea a `DatosEmisor`. La lectura de `empresa` es dato
  de PLATAFORMA (sin RLS), pero el `tenantId` proviene SIEMPRE del `TenantContext`
  (contexto autenticado), nunca de la peticion.

## Components and Interfaces

### Backend

#### `DatosEmisor` (record, objeto de valor)
Campos derivados de la Empresa (todos opcionales salvo `nombre`):
- `nombre` (razon social, `empresa.nombre`) - obligatorio.
- `nombreComercial` (`empresa.nombre_comercial`) - opcional (subtitulo).
- `rfc` (`empresa.rfc`) - opcional en el PDF (si vacio, se omite la linea).
- `direccion` (una sola linea armada a partir de `direccion_calle`, `direccion_ciudad`,
  `direccion_estado`, `direccion_cp`, `direccion_pais`, uniendo con comas las partes no vacias)
  - opcional.
- `email` (`empresa.email_contacto`) - opcional.
- `sitioWeb` (`empresa.sitio_web`) - opcional.
- Metodo de conveniencia `datosFiscalesIncompletos()` = `rfc` vacio O `direccion` vacia
  (para el aviso del Req 3).

#### `EmpresaEmisorPort` (puerto) + `EmpresaEmisorAdapter` (adaptador)
- `Optional<DatosEmisor> emisorDeTenant(UUID tenantId)`: carga la Empresa por id; si no existe,
  `Optional.empty()`. El adaptador NO expone la entidad `Empresa`, solo el objeto de valor.

#### `CotizacionPdfService.generar(...)` (firma cambia)
- Antes: `generar(Cotizacion, DatosCliente, EmisorProperties)`.
- Despues: `generar(Cotizacion, DatosCliente, DatosEmisor)`.
- El bloque cabecera (wordmark) usa `datosEmisor.nombre()`; si hay `nombreComercial`, se agrega
  como linea secundaria en el bloque EMISOR (o como subtitulo del wordmark).
- `lineasEmisor(...)` se reescribe para consumir `DatosEmisor`: nombre (fuerte) + nombre
  comercial (si hay) + RFC + direccion + email + sitio web, OMITIENDO las lineas vacias.
- El resto del layout (cliente, metadatos, partidas, totales, pie) NO cambia. El pie deja de
  referenciar al emisor de plataforma (usa texto neutro de "documento no fiscal").

#### `ServicioCotizaciones`
- Se reemplaza la dependencia `EmisorProperties emisor` por `EmpresaEmisorPort empresaEmisor`.
- `generarPdf(...)` y `enviarPorCorreo(...)` resuelven `DatosEmisor` del tenant del contexto:
  `DatosEmisor emisor = empresaEmisor.emisorDeTenant(TenantContext.require()).orElse(DatosEmisor.minimo("Empresa"))`.
  (Fallback defensivo: si por alguna anomalia no se resuelve la Empresa, se usa un emisor minimo
  neutro; nunca Dess-TI.)
- Aviso Req 3: el metodo de generacion/envio expone en su resultado (o via log/campo del DTO de
  envio) si `datosFiscalesIncompletos()`, para que la capa REST informe al frontend. Se
  implementa devolviendo un flag `emisorIncompleto` en la respuesta de envio y/o un encabezado
  informativo en la descarga; el frontend lo traduce a un aviso no intrusivo.

### Frontend

#### `PhotonService` (robustez de la consulta - Req 4)
- Normalizar el termino antes de consultar: colapsar espacios multiples (`\s+` -> ' ') y recortar;
  mantener acentos (Photon los maneja) pero tolerar su ausencia.
- Parametros: conservar `lang=es` y `limit`; RELAJAR el sesgo geografico -- en vez de `lat/lon`
  fijos que penalizan resultados lejanos, usar `bbox` de Mexico O eliminar el sesgo estricto para
  no filtrar direcciones validas; se prioriza recall. (Decision: quitar el sesgo lat/lon fijo y,
  opcionalmente, agregar `bbox` amplio de Mexico.)
- Mantener `catchError -> []` (degradacion sin lanzar) y el minimo de caracteres.
- El mapeo GeoJSON -> `DireccionSugerida` se conserva; se agrega respaldo de `ciudad` desde
  `state`/`name` cuando faltan `city/district/county`.

#### `AddressAutocomplete` (presentacion del panel - Req 5)
- Confirmar que el panel de sugerencias usa el overlay de `MatAutocomplete` (no una lista en
  flujo). El defecto de traslape se corrige con estilos del panel:
  - `panelClass` propio (p. ej. `direccion-autocomplete-panel`) con fondo solido
    (token de superficie), `z-index` por encima del contenido y sombra de elevacion.
  - El texto de ayuda ("Empieza a escribir...") pasa a ser `<mat-hint>` del propio campo (no un
    bloque que empuje el layout), y "Sin resultados" se muestra como una opcion deshabilitada
    DENTRO del panel del autocomplete, no debajo del campo.
- Accesibilidad: `MatAutocomplete` ya expone `role="listbox"`/`option` y navegacion por teclado;
  se verifica contraste y foco. Estado "Buscando..." como opcion no seleccionable con spinner.
- El componente sigue emitiendo `direccionSeleccionada`; los formularios padre (Cliente,
  crear/editar Empresa, perfil Mi empresa) no cambian su cableado.

#### Aviso de datos fiscales incompletos (Req 3)
- En la pantalla de detalle/envio de Cotizacion, si la respuesta indica `emisorIncompleto`,
  mostrar un banner/toast no intrusivo: "Completa los datos fiscales de tu empresa (RFC y
  direccion) en Mi empresa para cotizaciones mas completas." Sin UUIDs.

## Data Models

No se crean tablas ni columnas. Se reutiliza `empresa` (V1: `nombre`, `rfc`; V54:
`nombre_comercial`, `email_contacto`, `sitio_web`, `direccion_calle/ciudad/estado/cp/pais`).
`DatosEmisor` es un objeto de valor en memoria mapeado desde esas columnas.

## Error Handling

- **Backend PDF:** si la Empresa del tenant no se resuelve, se usa un `DatosEmisor` minimo neutro
  (nombre generico) y se marca `emisorIncompleto`; el PDF se genera igual (no bloquea, Req 3.1).
  Nunca se cae a los datos de Dess-TI.
- **Autocompletado:** errores de red o del servicio -> lista vacia (sin lanzar); el usuario captura
  manualmente. "Sin resultados" es un estado informativo, no un error.

## Testing Strategy

### Backend
- `CotizacionPdfServiceTest` (ampliado): el PDF NO contiene "Dess-TI" ni el RFC de plataforma;
  contiene la razon social de la Empresa; con `nombreComercial` presente aparece como subtitulo;
  con RFC/direccion vacios se omiten sin excepcion (firma `%PDF`).
- `EmpresaEmisorAdapterTest`: mapea correctamente las columnas de `empresa` a `DatosEmisor`,
  arma la direccion en una linea y detecta `datosFiscalesIncompletos()`.
- `ServicioCotizacionesTest` (ajustado): `generarPdf`/`enviarPorCorreo` resuelven el emisor via
  `EmpresaEmisorPort` (mock) y NO usan `EmisorProperties`; se propaga el flag `emisorIncompleto`.
- `FacturaRentaPdfServiceTest` (sin cambios): sigue mostrando Dess-TI -> garantiza no regresion
  del flujo de plataforma (Req 2).

### Frontend
- `photon.service.spec.ts` (ampliado): normaliza espacios/termino; construye los parametros sin
  sesgo estricto; mapea GeoJSON con respaldos; degrada a `[]` ante error; respeta minimo de
  caracteres.
- `address-autocomplete.spec.ts` (ampliado): al elegir sugerencia emite los campos; muestra
  "Sin resultados" como opcion del panel; el panel usa `panelClass` con fondo solido; navegacion
  por teclado; axe WCAG sin violaciones.
- Especificaciones de los formularios padre (Cliente, crear/editar Empresa, Mi empresa): siguen
  en verde (no cambia su cableado); se agrega, donde aplique, la verificacion del banner de datos
  fiscales incompletos en la Cotizacion.

## Verification

- Backend: `mvn -o package -DskipTests` (0 errores) + `mvn -o test` (suite ~1115+ en verde).
- Frontend: `ng build --configuration development` (0 errores) + `ng test --watch=false`
  (verde; reintentar en aislamiento las specs axe con flakiness conocida bajo carga paralela).
- Verificacion viva: relanzar backend (sin migracion nueva) y dev server; generar una cotizacion
  y confirmar que el PDF muestra los datos de la Empresa y NO Dess-TI; probar el autocompletado
  con una direccion de Mexico y confirmar sugerencias + panel sin traslape.


## Revision R2: Geocoding via backend propio (autocompletado robusto)

Tras verificar en vivo que el servicio publico de mapas responde 200 DESDE EL SERVIDOR pero el
navegador no lo alcanza de forma fiable (fallos CORS/red del navegador que quedaban ocultos por el
`catchError -> []`), se cambia la arquitectura del autocompletado: el geocoding pasa por NUESTRO
backend en lugar de que el navegador llame directo al tercero.

### Backend
- Nuevo endpoint `GET /api/v1/geocoding/direcciones?q=<texto>` (controlador `GeocodingController`).
  - Autenticado (usuario de empresa); NO requiere permiso de modulo (es utilidad transversal de
    captura de direcciones). Sin datos sensibles.
  - Valida `q` (>= 3 caracteres tras normalizar; si no, 200 con lista vacia).
  - Delega en `ServicioGeocoding` que consulta el proveedor OSM (Photon; respaldo Nominatim con
    User-Agent) usando `java.net.http.HttpClient` (JDK 21, sin dependencias nuevas) con TIMEOUT
    (~4 s connect/read) y mapea la respuesta GeoJSON/JSON a `DireccionSugeridaDto`
    (etiqueta, calle, ciudad, estado, cp, pais).
  - Manejo de error REAL: si el proveedor falla/da timeout, se registra (log WARN) y se responde
    200 con lista vacia (el frontend degrada a "Sin resultados" + captura manual) — pero el error
    queda trazado en el servidor (ya no invisible).
  - Cache corta opcional (in-memory, TTL ~60 s por termino) para cortesia con el servicio publico.
- `DireccionSugeridaDto` (record) = { etiqueta, calle, ciudad, estado, cp, pais }.

### Frontend
- `PhotonService` se renombra conceptualmente a `GeocodingService` (o se reescribe su interior):
  ahora llama a `GET /api/v1/geocoding/direcciones?q=...` (URL RELATIVA, via proxy `/api`), NO al
  tercero. Mantiene el minimo de caracteres, el debounce (en el componente) y `catchError -> []`.
  El mapeo ya lo hace el backend, asi que el servicio solo tipa la respuesta.
- `AddressAutocomplete` no cambia su API (`direccionSeleccionada`); sigue mostrando "Buscando…"/
  "Sin resultados" dentro del panel. Se corrige el ESPACIADO para que el hint/panel no se vean
  "juntos" con el campo siguiente (margen inferior del form-field; el hint no colapsa el layout).

### Correctness (añadidos)
## Correctness Properties

### Property 1: Aislamiento del emisor - para toda Cotizacion de cualquier tenant, el PDF generado
  NUNCA contiene el nombre "Dess-TI" ni el RFC de plataforma. Invariante independiente de los
  datos de la Empresa.

**Validates: Requirements 1.1, 1.6, 2.1**
### Property 2: Emisor igual al tenant - el nombre del emisor del PDF es siempre `empresa.nombre` del tenant
  del contexto (o el fallback neutro si la Empresa no se resuelve), nunca el `EmisorProperties`
  global.

**Validates: Requirements 1.2, 1.7**
### Property 3: Degradacion sin excepcion - para cualquier combinacion de campos fiscales presentes o
  ausentes (RFC, direccion, email, sitio web, nombre comercial), `CotizacionPdfService.generar`
  produce un PDF valido (firma `%PDF`) sin lanzar; las lineas vacias simplemente no se imprimen.

**Validates: Requirements 1.3, 1.4, 1.5, 3.1**
### Property 4: No regresion de plataforma - el PDF de `FacturaRenta` sigue mostrando a Dess-TI para
  cualquier factura de renta (el flujo de plataforma no cambia).

**Validates: Requirements 2.1, 2.2, 2.3**
### Property 5: Autocompletado tolerante - para cualquier texto de entrada (con espacios extra, distinto
  casing o acentos), `PhotonService.buscar` normaliza el termino y, ante error o vacio, emite una
  lista vacia sin lanzar; con >= minimo de caracteres consulta el servicio.

**Validates: Requirements 4.1, 4.2, 4.5, 4.6**
### Property 6: Panel no invasivo - cuando hay sugerencias, el panel se superpone al contenido con fondo
  solido y no desplaza los campos siguientes del formulario.

**Validates: Requirements 5.1, 5.2, 5.4**

### Property 7: Geocoding servido por backend - Geocoding proxeado por el backend -
  el frontend consulta SOLO `/api/v1/geocoding/direcciones` (URL relativa); nunca llama al tercero
  directamente. El backend aplica timeout y, ante fallo, responde 200 con lista vacia y registra el
  error (ya no invisible).

**Validates: Requirements 4.1, 4.5, 4.6**