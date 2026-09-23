package com.dessti.crm.contabilidad.reportes.application;

import java.time.LocalDate;
import java.util.List;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dessti.crm.contabilidad.polizas.domain.NaturalezaCuenta;
import com.dessti.crm.contabilidad.polizas.domain.TipoCuentaContable;
import com.dessti.crm.contabilidad.reportes.adapter.out.persistence.ReportesContablesRepository;
import com.dessti.crm.contabilidad.reportes.adapter.out.persistence.SaldoCuentaProjection;
import com.dessti.crm.contabilidad.reportes.domain.BalanceGeneral;
import com.dessti.crm.contabilidad.reportes.domain.BalanzaComprobacion;
import com.dessti.crm.contabilidad.reportes.domain.EstadoResultados;
import com.dessti.crm.contabilidad.reportes.domain.EstadosFinancieros;
import com.dessti.crm.contabilidad.reportes.domain.SaldoCuenta;
import com.dessti.crm.platform.audit.AuditoriaPort;
import com.dessti.crm.platform.audit.EventoAuditoria;
import com.dessti.crm.platform.security.rbac.AutenticacionActual;
import com.dessti.crm.platform.tenant.TenantContext;

/**
 * Servicio de aplicacion de <strong>solo lectura</strong> que deriva los estados
 * financieros de un periodo —balance general, estado de resultados y balanza de
 * comprobacion— a partir de las Polizas_Contables del periodo (Req 47). No muta
 * ningun dato de origen (Req 47.2): agrega los saldos por Cuenta_Contable via
 * {@link ReportesContablesRepository} y los deriva con el componente PURO
 * {@link EstadosFinancieros}.
 *
 * <h2>Ecuacion contable (Property 17, Req 47.3)</h2>
 * <p>El {@link BalanceGeneral} derivado cumple {@code activo == pasivo + capital}
 * (con el resultado del ejercicio integrado en el capital) porque cada
 * Poliza_Contable esta balanceada (Property 16). La logica que lo garantiza vive en
 * el dominio puro; este servicio solo agrega los datos y proyecta el resultado.</p>
 *
 * <h2>Autorizacion, aislamiento y auditoria</h2>
 * <ul>
 *   <li><strong>403 sin permiso (Req 47.5):</strong> las rutas del controlador
 *       exigen {@code estado_financiero:{leer,exportar}} via {@code @PreAuthorize};
 *       sin el permiso contable se responde 403.</li>
 *   <li><strong>Multi-tenant (Req 23):</strong> el {@code tenant_id} se deriva del
 *       {@link TenantContext} (nunca de la peticion, Req 23.4); el filtro de
 *       Hibernate y la RLS acotan las agregaciones al tenant.</li>
 *   <li><strong>Auditoria (Req 47.6):</strong> cada consulta y cada exportacion se
 *       audita via {@link AuditoriaPort}.</li>
 * </ul>
 */
@Service
public class ServicioEstadosFinancieros {

    /** Tipo de recurso de auditoria/RBAC de los estados financieros. */
    static final String RECURSO = "estado_financiero";

    private final ReportesContablesRepository reportesContablesRepository;
    private final AuditoriaPort auditoria;

    public ServicioEstadosFinancieros(ReportesContablesRepository reportesContablesRepository,
                                      AuditoriaPort auditoria) {
        this.reportesContablesRepository = reportesContablesRepository;
        this.auditoria = auditoria;
    }

    /**
     * Deriva el <strong>balance general</strong> del periodo (Req 47.1, 47.3;
     * Property 17). Audita la consulta (o la exportacion) (Req 47.6).
     *
     * @param desde     inicio del periodo (inclusivo); {@code null} no filtra.
     * @param hasta     fin del periodo (inclusivo); {@code null} no filtra.
     * @param exportar  {@code true} si la consulta es una exportacion (Req 47.4, 47.6).
     * @return el DTO del balance general; cumple {@code activo == pasivo + capital}.
     */
    @Transactional(readOnly = true)
    public BalanceGeneralDto balanceGeneral(LocalDate desde, LocalDate hasta, boolean exportar) {
        List<SaldoCuenta> saldos = agregarSaldos(desde, hasta);
        BalanceGeneral balance = EstadosFinancieros.balanceGeneral(saldos);
        auditarConsulta("balance_general", desde, hasta, exportar);
        return BalanceGeneralDto.de(balance, desde, hasta);
    }

    /**
     * Deriva el <strong>estado de resultados</strong> del periodo (Req 47.1). Audita
     * la consulta (o la exportacion) (Req 47.6).
     *
     * @param desde    inicio del periodo (inclusivo); {@code null} no filtra.
     * @param hasta    fin del periodo (inclusivo); {@code null} no filtra.
     * @param exportar {@code true} si la consulta es una exportacion.
     * @return el DTO del estado de resultados.
     */
    @Transactional(readOnly = true)
    public EstadoResultadosDto estadoDeResultados(LocalDate desde, LocalDate hasta,
                                                  boolean exportar) {
        List<SaldoCuenta> saldos = agregarSaldos(desde, hasta);
        EstadoResultados estado = EstadosFinancieros.estadoDeResultados(saldos);
        auditarConsulta("estado_resultados", desde, hasta, exportar);
        return EstadoResultadosDto.de(estado, desde, hasta);
    }

    /**
     * Deriva la <strong>balanza de comprobacion</strong> del periodo (Req 47.1).
     * Audita la consulta (o la exportacion) (Req 47.6).
     *
     * @param desde    inicio del periodo (inclusivo); {@code null} no filtra.
     * @param hasta    fin del periodo (inclusivo); {@code null} no filtra.
     * @param exportar {@code true} si la consulta es una exportacion.
     * @return el DTO de la balanza de comprobacion.
     */
    @Transactional(readOnly = true)
    public BalanzaComprobacionDto balanzaDeComprobacion(LocalDate desde, LocalDate hasta,
                                                        boolean exportar) {
        List<SaldoCuenta> saldos = agregarSaldos(desde, hasta);
        BalanzaComprobacion balanza = EstadosFinancieros.balanzaDeComprobacion(saldos);
        auditarConsulta("balanza_comprobacion", desde, hasta, exportar);
        return BalanzaComprobacionDto.de(balanza, desde, hasta);
    }

    // ------------------------------------------------------------------
    // Reglas internas
    // ------------------------------------------------------------------

    /**
     * Agrega los saldos por Cuenta_Contable del periodo (solo lectura) y los traduce
     * al modelo de dominio {@link SaldoCuenta}.
     */
    private List<SaldoCuenta> agregarSaldos(LocalDate desde, LocalDate hasta) {
        return reportesContablesRepository.agregarSaldosPorCuenta(desde, hasta).stream()
                .map(ServicioEstadosFinancieros::aSaldoCuenta)
                .toList();
    }

    private static SaldoCuenta aSaldoCuenta(SaldoCuentaProjection proyeccion) {
        return SaldoCuenta.de(
                proyeccion.getCuentaId(),
                proyeccion.getCodigo(),
                proyeccion.getNombre(),
                TipoCuentaContable.desdeValorBd(proyeccion.getTipo()),
                NaturalezaCuenta.desdeValorBd(proyeccion.getNaturaleza()),
                proyeccion.getCargos(),
                proyeccion.getAbonos());
    }

    private void auditarConsulta(String reporte, LocalDate desde, LocalDate hasta,
                                 boolean exportar) {
        String accion = exportar ? "exportar" : "consultar";
        String detalle = "estado financiero '" + reporte + "' [desde=" + desde
                + ", hasta=" + hasta + ", exportar=" + exportar + "]";
        auditoria.registrar(EventoAuditoria.deTenant(
                TenantContext.require(), actorActual(), accion, RECURSO, detalle, null, null));
    }

    private String actorActual() {
        return AutenticacionActual.obtener()
                .map(Authentication::getName)
                .filter(nombre -> nombre != null && !nombre.isBlank())
                .orElse("sistema");
    }
}
