# Plan de implementación — Contabilidad Electrónica SAT (Anexo 24)

Ejecución incremental, un bloque a la vez, verificando build + specs de backend y
frontend en los hitos. Cada tarea referencia los requisitos que cubre.

- [ ] 1. Migración V71: columna de amarre, catálogo agrupador SAT y permisos
  - Añadir columna `codigo_agrupador_sat` a `cuenta_contable` (+ índice por tenant).
  - Crear tabla `codigo_agrupador_sat_catalogo` (dato de plataforma, SIN RLS) y
    sembrar un subconjunto amplio y representativo del Apartado B del Anexo 24
    (niveles 1 y 2, naturaleza D/A, jerarquía padre).
  - Sembrar permisos `contabilidad_electronica:{leer,exportar}` y
    `cuenta_contable:actualizar` (ON CONFLICT DO NOTHING) y enlazarlos al rol
    `contabilidad`.
  - _Requisitos: 1.1, 1.2, 7.2, 7.3_

- [ ] 2. Dominio del amarre en `cuenta_contable`
  - Añadir campo `codigoAgrupadorSat` y método `amarrarCodigoAgrupador(codigo, actor)`
    a `CuentaContable`; getters. Incluirlo en `CuentaContableDto`.
  - _Requisitos: 1.1, 1.4, 1.5_

- [ ] 3. Catálogo agrupador SAT: entidad, repositorio, puerto y REST de consulta
  - Entidad `CodigoAgrupadorSat` (sin RLS), `CodigoAgrupadorSatRepository`,
    `CatalogoAgrupadorSatPort` + adapter, `CodigoAgrupadorSatDto`.
  - `CatalogoAgrupadorSatController` `GET /contabilidad/codigos-agrupadores-sat?q=`
    (perm `contabilidad_electronica:leer`).
  - _Requisitos: 1.2, 1.3_

- [ ] 4. Amarre por cuenta: servicio + endpoint PATCH + auditoría
  - Método de aplicación `amarrarCodigoAgrupador(cuentaId, codigo)` que valida el
    código contra el catálogo (422 si no existe), aplica y audita.
  - `PATCH /contabilidad/cuentas/{id}/codigo-agrupador`
    (perm `cuenta_contable:actualizar`).
  - _Requisitos: 1.3, 1.6, 1.7_

- [ ] 5. Generadores XML de dominio (puros) + utilidades
  - `XmlUtil`, `GeneradorCatalogoXml`, `GeneradorBalanzaXml`, `GeneradorPolizasXml`
    con StAX; modelos de entrada (`CuentaCatalogoSat`, `RenglonBalanzaSat`,
    `PolizaSat`/`TransaccionSat`, `EncabezadoSat`). Deterministas, UTF-8, 2 decimales.
  - _Requisitos: 2.1, 2.3, 3.1, 3.3, 4.1, 4.3, 7.6_

- [ ] 6. Puerto de datos fiscales de la Empresa
  - `DatosFiscalesEmpresaPort` (RFC del tenant) + adapter sobre `EmpresaRepository`.
  - _Requisitos: 2.2, 3.2, 4.2, 7.1_

- [ ] 7. Servicio de aplicación `ServicioContabilidadElectronica`
  - Vista previa y exportación de Catálogo, Balanza y Pólizas; cálculo de saldo
    inicial/final reutilizando pólizas/reportes; DTOs de preview y `ArchivoXmlDto`;
    auditoría de exportaciones; 422 si la balanza no cuadra al exportar.
  - _Requisitos: 2.4, 2.5, 2.7, 3.4, 3.5, 3.7, 4.4, 4.6, 5.1, 5.2, 5.3_

- [ ] 8. Controlador REST de Contabilidad Electrónica
  - `ContabilidadElectronicaController` con los 6 endpoints (preview + xml de
    catálogo/balanza/pólizas), `@PreAuthorize` con gating de módulo y permisos,
    descargas con `Content-Disposition`.
  - _Requisitos: 2.5, 2.6, 3.5, 3.6, 4.4, 4.5, 5.4_

- [ ] 9. Pruebas backend + build verde
  - Property tests de los 3 generadores (formato/namespace/orden/escape/2 dec.) y
    de la coherencia de la balanza; IT de migración/siembra + RLS del amarre;
    IT de endpoints (200/403/422). Ejecutar `mvnw -o test` de las clases nuevas y
    `mvnw -o -DskipTests clean package` (JDK 21) verde.
  - _Requisitos: 7.4, 7.5, 7.6_

- [ ] 10. Frontend: servicio y modelos
  - `contabilidad-electronica.service.ts` (previews, descargas blob, catálogo
    agrupadores) + modelos + spec del servicio.
  - _Requisitos: 6.2, 6.5_

- [ ] 11. Frontend: vista Contabilidad Electrónica (SAT)
  - Componente con selector de periodo y tres tarjetas (preview + descarga),
    chips de advertencia, estados carga/vacío/error; ruta lazy protegida por
    permiso; estilo enterprise; es-MX; WCAG AA.
  - _Requisitos: 6.1, 6.2, 6.4, 6.5_

- [ ] 12. Frontend: amarre de código agrupador en catálogo de cuentas
  - Vista/diálogo de catálogo de cuentas con autocompletar de código agrupador y
    PATCH del amarre (crear la vista mínima si no existe).
  - _Requisitos: 6.3_

- [ ] 13. Frontend: navegación + build/specs verdes
  - Ítem "Contabilidad Electrónica (SAT)" en `navigation.ts` (sección Contabilidad
    y finanzas, gating módulo+permiso). Specs de la vista/servicio; `ng test` de lo
    nuevo y `ng build --configuration production` verde.
  - _Requisitos: 6.1, 6.6_

- [ ] 14. Verificación end-to-end en local
  - Desplegar JAR + dist local, login como usuario con rol `contabilidad`, amarrar
    una cuenta, generar los tres XML y validar que descargan con el nombre y
    contenido correctos. Confirmar 403 sin permiso.
  - _Requisitos: 1.*, 2.*, 3.*, 4.*, 5.*, 6.*, 7.*_
