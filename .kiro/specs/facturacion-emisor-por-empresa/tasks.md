# Implementation Plan

## Overview

Dos correcciones acotadas sin migracion nueva: (A) el PDF de Cotizacion usa los datos fiscales
de la Empresa (tenant) como emisor en lugar de Dess-TI, manteniendo intacta la factura de renta
de plataforma; (B) el autocompletado de direcciones devuelve resultados y su panel deja de
traslaparse. Se agrega un aviso no intrusivo cuando la Empresa tiene datos fiscales incompletos.

## Tasks
- [x] 1. Backend: objeto de valor DatosEmisor y puerto EmpresaEmisorPort
- [x] 1.1 Crear el record `DatosEmisor` (objeto de valor)
  - Campos: `nombre` (obligatorio), `nombreComercial`, `rfc`, `direccion`, `email`, `sitioWeb` (opcionales).
  - Fabrica `minimo(String nombre)` para el fallback neutro; metodo `datosFiscalesIncompletos()` = rfc vacio O direccion vacia.
  - Normalizacion en el compact constructor (trim; nulos/blancos -> null salvo `nombre`).
  - _Requirements: 1.2, 1.3, 1.4, 3.1_
- [x] 1.2 Definir el puerto `EmpresaEmisorPort` con `Optional<DatosEmisor> emisorDeTenant(UUID tenantId)`
  - Ubicado en el paquete de aplicacion de cotizacion (coherente con `DatosClientePort`).
  - _Requirements: 1.1, 1.7_
- [x] 1.3 Implementar `EmpresaEmisorAdapter` que lee `empresa` por id y mapea a `DatosEmisor`
  - Arma `direccion` en una linea uniendo calle, ciudad, estado, CP, pais no vacios con comas.
  - No expone la entidad `Empresa`, solo el objeto de valor. `Optional.empty()` si no existe.
  - _Requirements: 1.1, 1.4, 1.7_
- [x] 1.4 Prueba `EmpresaEmisorAdapterTest`
  - Mapea columnas a `DatosEmisor`; arma la direccion; detecta `datosFiscalesIncompletos()` en casos con/sin RFC/direccion.
  - _Requirements: 1.4, 3.1_

- [x] 2. Backend: PDF de Cotizacion usa el emisor de la Empresa (no Dess-TI)
- [x] 2.1 Cambiar la firma de `CotizacionPdfService.generar` a `(Cotizacion, DatosCliente, DatosEmisor)`
  - Cabecera/wordmark usa `datosEmisor.nombre()`; bloque EMISOR reescrito para consumir `DatosEmisor` (nombre fuerte + nombre comercial si hay + RFC + direccion + email + sitio web), omitiendo lineas vacias.
  - El pie deja de referenciar el emisor de plataforma (texto neutro de documento no fiscal).
  - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5, 1.6_
- [x] 2.2 Actualizar `ServicioCotizaciones` para resolver el emisor del tenant
  - Reemplazar la dependencia `EmisorProperties` por `EmpresaEmisorPort`.
  - `generarPdf` y `enviarPorCorreo` resuelven `DatosEmisor` via `emisorDeTenant(TenantContext.require())` con fallback `DatosEmisor.minimo(...)`; nunca Dess-TI.
  - Propagar flag `emisorIncompleto` (para el aviso Req 3) en la respuesta de envio y/o encabezado de descarga.
  - _Requirements: 1.1, 1.7, 3.1, 3.2_
- [x] 2.3 Ampliar `CotizacionPdfServiceTest` (property-based donde aplique)
  - El PDF NO contiene "Dess-TI" ni el RFC de plataforma; SI contiene la razon social de la Empresa; con nombre comercial aparece como subtitulo; con RFC/direccion vacios se omiten sin excepcion (firma %PDF) para cualquier combinacion de campos.
  - _Requirements: 1.1, 1.2, 1.3, 1.5, 1.6_
- [x] 2.4 Ajustar `ServicioCotizacionesTest`
  - `generarPdf`/`enviarPorCorreo` usan `EmpresaEmisorPort` (mock) y NO `EmisorProperties`; se propaga `emisorIncompleto`.
  - _Requirements: 1.7, 3.1_
- [x] 2.5 Verificar no regresion del flujo de plataforma
  - `FacturaRentaPdfServiceTest` permanece verde: la factura de renta sigue mostrando Dess-TI.
  - _Requirements: 2.1, 2.2, 2.3_

- [x] 3. Backend: exponer el aviso de datos fiscales incompletos en REST
- [x] 3.1 Reflejar `emisorIncompleto` en el contrato REST de Cotizacion
  - En el DTO/respuesta de envio (`POST /cotizaciones/{id}/enviar-correo`) y/o un encabezado informativo en `GET /cotizaciones/{id}/pdf`.
  - Sin UUIDs ni terminologia interna en el mensaje.
  - _Requirements: 3.1, 3.2, 3.3_
- [x] 3.2 Prueba de rebanada del controlador
  - Verifica que la respuesta incluye el flag/encabezado cuando el emisor esta incompleto.
  - _Requirements: 3.1, 3.3_

- [x] 4. Frontend: robustez del PhotonService (autocompletado devuelve resultados)
- [x] 4.1 Normalizar el termino y relajar el sesgo geografico
  - Colapsar espacios multiples y recortar; quitar el sesgo fijo lat/lon (opcional bbox amplio de Mexico); conservar `lang=es`, `limit`, minimo de caracteres y `catchError -> []`.
  - Respaldos de ciudad desde state/name cuando faltan city/district/county.
  - _Requirements: 4.1, 4.2, 4.5, 4.6_
- [x] 4.2 Ampliar `photon.service.spec.ts`
  - Normaliza espacios/termino; parametros sin sesgo estricto; mapeo con respaldos; degrada a [] ante error; respeta minimo de caracteres.
  - _Requirements: 4.1, 4.2, 4.5_

- [x] 5. Frontend: panel de sugerencias sin traslape y accesible
- [x] 5.1 Corregir la presentacion del panel en `AddressAutocomplete`
  - `panelClass` propia con fondo solido (token de superficie), z-index por encima y elevacion; "Empieza a escribir..." como `<mat-hint>`; "Sin resultados" y "Buscando..." como opciones DENTRO del panel del autocomplete (no empujan el layout).
  - Verificar navegacion por teclado (flechas/Enter/Esc) y foco.
  - _Requirements: 4.3, 4.4, 5.1, 5.2, 5.3_
- [x] 5.2 Ampliar `address-autocomplete.spec.ts`
  - Al elegir sugerencia emite los campos; "Sin resultados" como opcion del panel; panel usa `panelClass` con fondo solido; navegacion por teclado; axe WCAG sin violaciones.
  - _Requirements: 4.3, 4.4, 5.1, 5.2, 5.3, 5.4_
- [x] 5.3 Confirmar consistencia en los formularios padre
  - Cliente, crear/editar Empresa y perfil Mi empresa siguen recibiendo `direccionSeleccionada` y rellenando sus campos; sus specs permanecen verdes.
  - _Requirements: 5.4_

- [x] 6. Frontend: aviso de datos fiscales incompletos en Cotizacion
- [x] 6.1 Mostrar banner/toast no intrusivo cuando `emisorIncompleto`
  - En la pantalla de detalle/envio de Cotizacion; mensaje que indica completar RFC y direccion en Mi empresa; sin UUIDs.
  - _Requirements: 3.1, 3.2, 3.3_
- [x] 6.2 Prueba del aviso
  - Con respuesta `emisorIncompleto = true` se muestra el aviso; sin el flag no aparece.
  - _Requirements: 3.1, 3.3_

- [x] 7. Verificacion integral y puesta en vivo
- [x] 7.1 Build + pruebas backend
  - `mvn -o package -DskipTests` (0 errores) y `mvn -o test` (suite ~1115+ en verde).
  - _Requirements: 6.1, 6.2_
- [x] 7.2 Build + pruebas frontend
  - `ng build --configuration development` (0 errores) y `ng test --watch=false` (verde; reintentar en aislamiento las specs axe con flakiness conocida).
  - _Requirements: 6.1, 6.2, 6.3_
- [x] 7.3 Verificacion viva
  - Relanzar backend (sin migracion nueva) y dev server; generar una cotizacion y confirmar que el PDF muestra los datos de la Empresa y NO Dess-TI; probar el autocompletado con una direccion de Mexico (sugerencias + panel sin traslape).
  - _Requirements: 1.1, 1.6, 2.1, 4.1, 5.1_


- [x] 8. Geocoding via backend propio (autocompletado robusto) [R2]
- [x] 8.1 Backend: `ServicioGeocoding` + `DireccionSugeridaDto`
  - Consulta el proveedor OSM (Photon; respaldo Nominatim con User-Agent) via `java.net.http.HttpClient` (JDK 21) con timeout ~4 s; mapea GeoJSON/JSON a `DireccionSugeridaDto` (etiqueta, calle, ciudad, estado, cp, pais). Ante fallo/timeout: log WARN + lista vacia. Cache corta opcional (TTL ~60 s).
  - _Requirements: 4.1, 4.2, 4.5, 4.6_
- [x] 8.2 Backend: `GeocodingController` `GET /api/v1/geocoding/direcciones?q=`
  - Autenticado; valida `q` (>=3 tras normalizar, si no lista vacia); 200 con la lista de sugerencias. Sin permiso de modulo (utilidad transversal).
  - _Requirements: 4.1, 4.4, 4.6_
- [x] 8.3 Backend: pruebas de `ServicioGeocoding` y del controlador
  - Servicio: mapea una respuesta simulada del proveedor; ante error/timeout devuelve lista vacia (con el HttpClient inyectado/simulado). Controlador (slice): 200 con lista; `q` corto -> lista vacia; requiere autenticacion.
  - _Requirements: 4.1, 4.5_
- [x] 8.4 Frontend: `GeocodingService` llama al backend (URL relativa `/api`)
  - Reescribir el servicio para consultar `GET /api/v1/geocoding/direcciones?q=` (proxy `/api`); ya NO llamar al tercero. Conservar minimo de caracteres y `catchError -> []`; el mapeo lo hace el backend.
  - _Requirements: 4.1, 4.6_
- [x] 8.5 Frontend: corregir espaciado del panel/hint y ampliar specs
  - Ajustar el margen inferior del form-field para que el hint/panel no se vean "juntos" con el campo siguiente. Actualizar specs del servicio (ahora golpea `/api/v1/geocoding/direcciones`) y del componente; axe WCAG.
  - _Requirements: 4.3, 5.1, 5.2, 5.4_
- [x] 8.6 Verificacion viva del autocompletado
  - Relanzar backend + dev server; escribir una direccion de Mexico (p. ej. "Plutarco Gonzalez Pliego 108 Toluca") y confirmar que aparecen sugerencias reales y el panel no se encima.
  - _Requirements: 4.1, 5.1_
## Task Dependency Graph

```
1.1 -> 1.2 -> 1.3 -> 1.4
1.1, 1.3 -> 2.1 -> 2.2 -> 2.3, 2.4
2.2 -> 2.5 (no regresion, independiente de logica pero se valida junto)
2.2 -> 3.1 -> 3.2
(frontend, independiente del backend)
4.1 -> 4.2
5.1 -> 5.2 ; 5.1 -> 5.3
3.1 -> 6.1 -> 6.2   (6 depende del contrato REST del flag)
1..6 -> 7.1, 7.2 -> 7.3
```

- Backend (1 -> 2 -> 3) y Frontend autocompletado (4, 5) pueden avanzar en paralelo.
- El aviso frontend (6) depende del contrato REST del flag (3.1).
- La verificacion integral (7) es la ultima, tras completar 1-6.

```json
{
  "waves": [
    { "wave": 1, "tasks": ["1.1", "4.1", "5.1"] },
    { "wave": 2, "tasks": ["1.2", "1.3", "4.2", "5.2", "5.3"] },
    { "wave": 3, "tasks": ["1.4", "2.1"] },
    { "wave": 4, "tasks": ["2.2"] },
    { "wave": 5, "tasks": ["2.3", "2.4", "2.5", "3.1"] },
    { "wave": 6, "tasks": ["3.2", "6.1"] },
    { "wave": 7, "tasks": ["6.2"] },
    { "wave": 8, "tasks": ["7.1", "7.2"] },
    { "wave": 9, "tasks": ["7.3"] },
    { "wave": 10, "tasks": ["8.1", "8.4"] },
    { "wave": 11, "tasks": ["8.2", "8.5"] },
    { "wave": 12, "tasks": ["8.3"] },
    { "wave": 13, "tasks": ["8.6"] }
  ]
}
```

## Notes

- SIN migracion nueva: la tabla `empresa` ya tiene los campos fiscales (V1: nombre, rfc; V54:
  nombre_comercial, email_contacto, sitio_web, direccion_*). No se crean columnas ni tablas.
- NO tocar `FacturaRentaPdfService` ni `EmisorProperties`: la factura de renta de Dess-TI se
  conserva tal cual (Req 2).
- Archivos UTF-8 sin BOM. Backend: mvn -o package/test. Frontend: ng build/test; reintentar en
  aislamiento las specs axe con flakiness conocida bajo carga paralela.
- No mostrar UUIDs al usuario; textos en espanol; tokens del sistema de diseno; WCAG AA.
- Relanzar backend (sin migracion) y dev server al final para verificacion viva.