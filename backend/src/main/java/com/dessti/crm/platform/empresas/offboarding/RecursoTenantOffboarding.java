package com.dessti.crm.platform.empresas.offboarding;

import java.util.UUID;

/**
 * SPI (punto de extension) que cada modulo de negocio implementa para
 * participar en el <strong>offboarding</strong> de una Empresa (Req 69):
 * exportar sus datos por {@code tenant_id} (Req 69.1) y, tras el Periodo_Gracia,
 * eliminarlos o anonimizarlos acotados a ese {@code tenant_id} (Req 69.3),
 * preservando los comprobantes fiscales bajo retencion (Req 69.4).
 *
 * <h2>Diseno extensible (framework, no lista fija de tablas)</h2>
 * <p>El {@code ServicioOffboarding} <em>no</em> conoce las tablas de negocio:
 * inyecta la lista de <strong>todos</strong> los beans que implementan esta SPI
 * ({@code List<RecursoTenantOffboarding>}) y los itera. Cada modulo futuro
 * (clientes, cotizaciones, facturas, nomina, contabilidad, etc., bloques 15+)
 * registra <em>un</em> bean por recurso que sepa exportar y borrar/anonimizar sus
 * propios datos del tenant. Mientras esos modulos no existan, la lista puede
 * estar practicamente vacia (solo con el exportador de metadatos de plataforma,
 * {@code ExportadorMetadatosEmpresa}) y el framework sigue siendo correcto: cada
 * modulo que se anada se integra sin tocar {@code ServicioOffboarding}.</p>
 *
 * <h2>Aislamiento por tenant (Req 69.5, 23)</h2>
 * <p>El servicio invoca estos metodos <strong>dentro del ambito RLS del tenant
 * objetivo</strong> (fija {@code app.current_tenant} a {@code tenantId} en la
 * transaccion), de modo que la Row-Level Security de PostgreSQL refuerza que la
 * operacion solo alcance filas de esa Empresa. Ademas, cada implementacion
 * <strong>debe</strong> filtrar explicitamente por {@code tenant_id} en sus
 * consultas y <em>nunca</em> emitir un {@code DELETE}/{@code UPDATE} sin ese
 * predicado (defensa en profundidad, fail-closed).</p>
 *
 * <h2>Preservacion fiscal (Req 69.4)</h2>
 * <p>Los recursos que representan comprobantes fiscales bajo retencion legal
 * (Factura (CFDI), Recibo_Nomina y Poliza_Contable) deben declarar
 * {@link #esComprobanteFiscal()} {@code = true}. Para estos, el servicio
 * <em>omite</em> la eliminacion: se exportan e igualmente se conservan conforme
 * al periodo de retencion aplicable (o se documenta su archivado seguro). Su
 * implementacion de {@link #eliminarOAnonimizar(UUID)} no debe borrar el
 * comprobante; puede devolver {@code 0} o anonimizar unicamente campos NO
 * fiscales.</p>
 */
public interface RecursoTenantOffboarding {

    /**
     * Nombre canonico del recurso que este componente exporta/elimina (por
     * ejemplo {@code "cliente"}, {@code "factura"}, {@code "empresa"}). Se usa
     * como clave en la exportacion estructurada y en el detalle de auditoria del
     * alcance (Req 69.6). Debe ser estable y unico entre las implementaciones.
     *
     * @return el nombre del recurso; no {@code null} ni en blanco.
     */
    String nombreRecurso();

    /**
     * Indica si el recurso es un <strong>comprobante fiscal</strong> bajo
     * retencion legal (Factura (CFDI), Recibo_Nomina o Poliza_Contable) que debe
     * <em>preservarse</em> aun cuando se eliminen otros datos (Req 69.4).
     *
     * <p>Por defecto {@code false} (recurso ordinario, elegible para eliminacion
     * o anonimizacion). Un recurso fiscal ({@code true}) se exporta pero
     * <strong>nunca</strong> se elimina durante el offboarding.</p>
     *
     * @return {@code true} si es un comprobante fiscal a preservar; {@code false}
     *         en caso contrario.
     */
    default boolean esComprobanteFiscal() {
        return false;
    }

    /**
     * Exporta los datos de este recurso pertenecientes a la Empresa indicada, en
     * una estructura serializable a un formato estructurado y procesable (por
     * ejemplo JSON) — Req 69.1.
     *
     * <p>La ejecucion ocurre bajo el ambito RLS del tenant objetivo; la
     * implementacion debe, ademas, filtrar por {@code tenantId} de forma
     * explicita. El valor devuelto <strong>no debe contener secretos</strong>
     * (contrasenas, hashes, claves, tokens) — Req 10.10, 11.3.</p>
     *
     * @param tenantId identificador de la Empresa objetivo; no {@code null}.
     * @return una estructura serializable (por ejemplo {@code Map}, {@code List}
     *         o un DTO/record) con los datos del recurso para ese tenant; puede
     *         ser una coleccion vacia si no hay datos, nunca {@code null}.
     */
    Object exportar(UUID tenantId);

    /**
     * Elimina o anonimiza los datos de este recurso pertenecientes a la Empresa
     * indicada, acotados estrictamente a su {@code tenant_id} (Req 69.3, 69.5).
     *
     * <p>Se invoca <strong>solo</strong> para recursos que no sean comprobantes
     * fiscales ({@link #esComprobanteFiscal()} {@code == false}); el servicio
     * omite la llamada para los fiscales (Req 69.4). La ejecucion ocurre bajo el
     * ambito RLS del tenant objetivo; la implementacion debe filtrar por
     * {@code tenantId} explicitamente y <em>nunca</em> ejecutar un borrado sin
     * ese predicado (fail-closed).</p>
     *
     * @param tenantId identificador de la Empresa objetivo; no {@code null}.
     * @return el numero de registros eliminados o anonimizados (>= 0), para el
     *         alcance auditado (Req 69.6).
     */
    long eliminarOAnonimizar(UUID tenantId);
}
