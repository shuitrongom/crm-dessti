/**
 * Submodulo de <strong>calculo de Nomina y CFDI de nomina</strong> del modulo
 * rhnomina (Req 41, 35, 23). Se construye sobre el submodulo base
 * {@link com.dessti.crm.rhnomina.empleado} (Empleado, Contrato_Laboral,
 * Incidencia) y sigue el patron hexagonal por submodulo:
 *
 * <ul>
 *   <li>{@code domain}                  - entidades {@code Nomina} y
 *       {@code ReciboNomina}, sus estados con maquina de estados pura
 *       ({@code EstadoNomina}, {@code EstadoReciboNomina}) y convertidores, y el
 *       motor de calculo <em>puro</em> ({@code CalculoNomina}, {@code EntradaNomina},
 *       {@code ResultadoNomina}) con las tablas fiscales versionadas
 *       ({@code TablasFiscalesNomina}).</li>
 *   <li>{@code application}             - servicio de aplicacion
 *       {@code ServicioNomina}, DTOs y comandos.</li>
 *   <li>{@code adapter.in.rest}         - controlador REST guardado por RBAC.</li>
 *   <li>{@code adapter.out.persistence} - repositorios Spring Data JPA.</li>
 * </ul>
 *
 * <h2>Alcance (Req 41)</h2>
 * <p>Calculo de percepciones (salario, tiempo extra, aguinaldo, PTU), deducciones
 * (ISR conforme a la tarifa del Art. 96 LISR, cuota obrera del IMSS e Infonavit) y
 * subsidio al empleo, con la identidad {@code neto = percepciones - deducciones +
 * subsidio} (&gt;= 0); rechazo del calculo cuando faltan datos fiscales del Empleado
 * (Req 41.3); maquina de estados de la Nomina
 * ({@code borrador -> calculada -> autorizada -> timbrada -> pagada}) y del
 * Recibo_Nomina ({@code calculado -> timbrado -> cancelado}); Timbrado de cada
 * Recibo_Nomina como CFDI de nomina via el {@code PacPort} de facturacion;
 * inmutabilidad del CFDI de nomina timbrado y su Folio_Fiscal (historico); y
 * auditoria de los cambios de estado y del Timbrado.</p>
 *
 * <h2>Tablas fiscales (revisar anualmente)</h2>
 * <p>Las tarifas de ISR/subsidio y las tasas de IMSS/Infonavit viven como constantes
 * documentadas en {@code TablasFiscalesNomina} (vigentes 2024/2026); se actualizan sin
 * tocar el motor de calculo. La cuota del IMSS es una representacion simplificada
 * (una tasa obrera unica) documentada como tal.</p>
 */
package com.dessti.crm.rhnomina.nomina;
