/**
 * Portabilidad y baja del tenant (offboarding de la Empresa) — Req 69, tarea
 * 14.4.
 *
 * <p>Este paquete implementa un <strong>marco extensible</strong> para exportar
 * (Req 69.1), conservar durante un Periodo_Gracia configurable (Req 69.2) y, tras
 * su expiracion, eliminar o anonimizar (Req 69.3) los datos de negocio de una
 * Empresa <em>acotados a su {@code tenant_id}</em> (Req 69.5, reforzado por RLS),
 * preservando los comprobantes fiscales bajo retencion (Req 69.4) y auditando
 * cada operacion (Req 69.6).</p>
 *
 * <h2>Piezas</h2>
 * <ul>
 *   <li>{@link com.dessti.crm.platform.empresas.offboarding.RecursoTenantOffboarding}
 *       — SPI que cada modulo de negocio implementa (uno por recurso) para
 *       exportar y borrar/anonimizar sus datos del tenant. El servicio inyecta
 *       {@code List<RecursoTenantOffboarding>} y los itera, sin conocer las
 *       tablas concretas: los modulos futuros (bloques 15+) se integran solo
 *       registrando un bean.</li>
 *   <li>{@link com.dessti.crm.platform.empresas.offboarding.ExportadorMetadatosEmpresa}
 *       — implementacion de referencia (metadatos NO secretos de la Empresa) que
 *       ejercita el pipeline mientras no existan modulos de negocio.</li>
 *   <li>{@link com.dessti.crm.platform.empresas.offboarding.ServicioOffboarding}
 *       — caso de uso de aplicacion que orquesta exportacion, cancelacion con
 *       Periodo_Gracia y eliminacion definitiva bajo el ambito RLS del tenant
 *       objetivo.</li>
 *   <li>{@link com.dessti.crm.platform.empresas.offboarding.OffboardingProperties}
 *       — Periodo_Gracia configurable ({@code crm.offboarding.periodo-gracia}).</li>
 * </ul>
 *
 * <h2>Decision: la Empresa como lapida (tombstone)</h2>
 * <p>La eliminacion definitiva <strong>no borra la fila {@code empresa}</strong>:
 * la Empresa permanece en estado {@code CANCELADA} como ancla de auditoria y
 * lapida del tenant; se eliminan/anonimizan sus <em>datos de negocio</em>. Asi
 * se preserva la trazabilidad del offboarding (Req 69.6) y la integridad de las
 * referencias de plataforma.</p>
 */
package com.dessti.crm.platform.empresas.offboarding;
