package com.dessti.crm.contabilidad.polizas.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dessti.crm.contabilidad.polizas.adapter.out.persistence.PeriodoContableRepository;
import com.dessti.crm.contabilidad.polizas.domain.PeriodoContable;
import com.dessti.crm.contabilidad.reportes.adapter.out.persistence.ReportesContablesRepository;
import com.dessti.crm.contabilidad.reportes.adapter.out.persistence.SaldoCuentaProjection;
import com.dessti.crm.platform.audit.AuditoriaPort;
import com.dessti.crm.platform.audit.EventoAuditoria;
import com.dessti.crm.platform.error.ConflictoUnicidadException;
import com.dessti.crm.platform.error.ReglaNegocioException;
import com.dessti.crm.platform.error.TransicionInvalidaException;
import com.dessti.crm.platform.security.rbac.AutenticacionActual;
import com.dessti.crm.platform.tenant.TenantContext;

/**
 * Servicio de aplicacion del <strong>cierre de periodo contable</strong> (candado
 * contable). Gobierna la transicion abierto/cerrado de los periodos mensuales del
 * tenant e implementa {@link PeriodoContablePort}, el contrato que
 * {@link ServicioContabilidad} consulta para bloquear polizas en periodos cerrados.
 *
 * <h2>Operaciones</h2>
 * <ul>
 *   <li><strong>cerrarPeriodo:</strong> valida primero que la balanza del periodo
 *       cuadre (suma de cargos = suma de abonos del mes, mismo criterio que la
 *       Contabilidad Electronica); si no cuadra, rechaza con 422. Marca el periodo
 *       cerrado y audita. 409 si ya estaba cerrado.</li>
 *   <li><strong>reabrirPeriodo:</strong> reabre un periodo cerrado con motivo
 *       obligatorio (422 si falta) y audita. 409 si no estaba cerrado.</li>
 *   <li><strong>consultarAnio:</strong> devuelve los 12 meses del anio con su estado
 *       (los no materializados como abiertos).</li>
 *   <li><strong>estaCerrado:</strong> consulta pura del candado.</li>
 * </ul>
 *
 * <p>Replica el patron de auditoria/actor/tenant de {@link ServicioContabilidad}.</p>
 */
@Service
public class ServicioCierrePeriodo implements PeriodoContablePort {

    /** Tipo de recurso de auditoria/RBAC del Periodo_Contable. */
    static final String RECURSO_PERIODO = "periodo_contable";

    /** Escala monetaria para la validacion de cuadre (NUMERIC(18,2)). */
    private static final int ESCALA = 2;

    private final PeriodoContableRepository periodoContableRepository;
    private final ReportesContablesRepository reportesContablesRepository;
    private final AuditoriaPort auditoria;

    public ServicioCierrePeriodo(PeriodoContableRepository periodoContableRepository,
                                 ReportesContablesRepository reportesContablesRepository,
                                 AuditoriaPort auditoria) {
        this.periodoContableRepository = periodoContableRepository;
        this.reportesContablesRepository = reportesContablesRepository;
        this.auditoria = auditoria;
    }

    // ------------------------------------------------------------------
    // Puerto del candado (consumido por ServicioContabilidad)
    // ------------------------------------------------------------------

    /**
     * {@inheritDoc}
     *
     * <p>Un periodo sin fila materializada se considera ABIERTO ({@code false}).</p>
     */
    @Override
    @Transactional(readOnly = true)
    public boolean estaCerrado(int anio, int mes) {
        return periodoContableRepository.buscarPorAnioMes(anio, mes)
                .map(PeriodoContable::estaCerrado)
                .orElse(false);
    }

    // ------------------------------------------------------------------
    // Cierre / reapertura
    // ------------------------------------------------------------------

    /**
     * Cierra el periodo mensual {@code (anio, mes)} del tenant. Antes de cerrar,
     * valida que la balanza del periodo cuadre; si no cuadra, rechaza con 422 sin
     * cerrar. Audita el cierre.
     *
     * @param anio anio del periodo (2000..2100).
     * @param mes  mes del periodo (1..12).
     * @return el DTO del periodo cerrado.
     * @throws ReglaNegocioException       si el anio/mes estan fuera de rango o la
     *                                     balanza del periodo no cuadra (422).
     * @throws TransicionInvalidaException si el periodo ya estaba cerrado (409).
     */
    @Transactional
    public PeriodoContableDto cerrarPeriodo(int anio, int mes) {
        String actor = actorActual();
        PeriodoContable.validarAnioMes(anio, mes);
        validarCuadreDelPeriodo(anio, mes);

        Optional<PeriodoContable> existente = periodoContableRepository.buscarPorAnioMes(anio, mes);
        PeriodoContable periodo;
        if (existente.isPresent()) {
            periodo = existente.get();
            // Lanza 409 (TransicionInvalidaException) si ya esta cerrado.
            periodo.cerrar(actor);
        } else {
            periodo = PeriodoContable.crearCerrado(anio, mes, actor);
        }

        PeriodoContable guardado;
        try {
            guardado = periodoContableRepository.save(periodo);
        } catch (DataIntegrityViolationException ex) {
            // Carrera contra UNIQUE (tenant_id, anio, mes): otro cierre concurrente gano.
            throw new ConflictoUnicidadException(
                    "El periodo " + etiqueta(anio, mes) + " ya fue cerrado por otra operacion.");
        }
        auditar(actor, "cerrar", guardado.getId().toString(),
                "cerrado Periodo_Contable [" + etiqueta(anio, mes) + "]");
        return PeriodoContableDto.de(guardado);
    }

    /**
     * Reabre el periodo mensual {@code (anio, mes)} del tenant indicando un motivo
     * obligatorio. Audita la reapertura.
     *
     * @param anio   anio del periodo.
     * @param mes    mes del periodo (1..12).
     * @param motivo motivo de la reapertura; obligatorio y no vacio.
     * @return el DTO del periodo reabierto.
     * @throws ReglaNegocioException       si el motivo es vacio o el anio/mes estan
     *                                     fuera de rango (422).
     * @throws TransicionInvalidaException si el periodo no estaba cerrado (409).
     */
    @Transactional
    public PeriodoContableDto reabrirPeriodo(int anio, int mes, String motivo) {
        String actor = actorActual();
        PeriodoContable.validarAnioMes(anio, mes);
        if (motivo == null || motivo.isBlank()) {
            throw new ReglaNegocioException(
                    "La reapertura de un periodo requiere indicar un motivo.");
        }

        PeriodoContable periodo = periodoContableRepository.buscarPorAnioMes(anio, mes)
                .orElseThrow(() -> new TransicionInvalidaException(
                        "El periodo " + etiqueta(anio, mes)
                                + " no esta cerrado; no puede reabrirse."));
        // Lanza 409 si no esta cerrado, 422 si el motivo es vacio.
        periodo.reabrir(motivo, actor);
        PeriodoContable guardado = periodoContableRepository.save(periodo);
        auditar(actor, "reabrir", guardado.getId().toString(),
                "reabierto Periodo_Contable [" + etiqueta(anio, mes) + ", motivo="
                        + guardado.getMotivoReapertura() + "]");
        return PeriodoContableDto.de(guardado);
    }

    /**
     * Devuelve los 12 meses del anio con su estado. Los meses no materializados
     * (sin fila) se devuelven como ABIERTOS por defecto.
     *
     * @param anio anio a consultar (2000..2100).
     * @return la lista de los 12 periodos del anio, ordenada por mes.
     * @throws ReglaNegocioException si el anio esta fuera de rango (422).
     */
    @Transactional(readOnly = true)
    public List<PeriodoContableDto> consultarAnio(int anio) {
        PeriodoContable.validarAnioMes(anio, 1);
        List<PeriodoContable> materializados = periodoContableRepository.listarPorAnio(anio);
        PeriodoContableDto[] porMes = new PeriodoContableDto[13]; // indices 1..12
        for (PeriodoContable p : materializados) {
            porMes[p.getMes()] = PeriodoContableDto.de(p);
        }
        List<PeriodoContableDto> resultado = new ArrayList<>(12);
        for (int mes = 1; mes <= 12; mes++) {
            resultado.add(porMes[mes] != null
                    ? porMes[mes]
                    : PeriodoContableDto.abiertoPorDefecto(anio, mes));
        }
        return resultado;
    }

    // ------------------------------------------------------------------
    // Reglas internas
    // ------------------------------------------------------------------

    /**
     * Valida que la balanza del periodo {@code (anio, mes)} cuadre: la suma de los
     * cargos de los movimientos del mes debe igualar la suma de los abonos. Reutiliza
     * la agregacion por cuenta de {@link ReportesContablesRepository} (mismo criterio
     * que la Balanza del Anexo 24). Si no cuadra, rechaza con 422.
     */
    private void validarCuadreDelPeriodo(int anio, int mes) {
        YearMonth ym = YearMonth.of(anio, mes);
        LocalDate primerDia = ym.atDay(1);
        LocalDate ultimoDia = ym.atEndOfMonth();
        List<SaldoCuentaProjection> saldos =
                reportesContablesRepository.agregarSaldosPorCuenta(primerDia, ultimoDia);

        BigDecimal totalCargos = BigDecimal.ZERO.setScale(ESCALA, RoundingMode.HALF_UP);
        BigDecimal totalAbonos = BigDecimal.ZERO.setScale(ESCALA, RoundingMode.HALF_UP);
        for (SaldoCuentaProjection s : saldos) {
            totalCargos = totalCargos.add(valor(s.getCargos()));
            totalAbonos = totalAbonos.add(valor(s.getAbonos()));
        }
        totalCargos = totalCargos.setScale(ESCALA, RoundingMode.HALF_UP);
        totalAbonos = totalAbonos.setScale(ESCALA, RoundingMode.HALF_UP);

        if (totalCargos.compareTo(totalAbonos) != 0) {
            BigDecimal diferencia = totalCargos.subtract(totalAbonos)
                    .setScale(ESCALA, RoundingMode.HALF_UP);
            throw new ReglaNegocioException(
                    "No se puede cerrar el periodo " + etiqueta(anio, mes)
                            + ": la balanza no cuadra. Los cargos (" + totalCargos.toPlainString()
                            + ") difieren de los abonos (" + totalAbonos.toPlainString() + ") en "
                            + diferencia.toPlainString() + ". Corrija las polizas antes de cerrar.");
        }
    }

    private static BigDecimal valor(BigDecimal v) {
        return (v == null) ? BigDecimal.ZERO : v;
    }

    private String etiqueta(int anio, int mes) {
        return String.format("%04d-%02d", anio, mes);
    }

    private void auditar(String actor, String accion, String recursoId, String detalle) {
        auditoria.registrar(EventoAuditoria.deTenant(
                TenantContext.require(), actor, accion, RECURSO_PERIODO,
                detalle + " [id=" + recursoId + "]", null, null));
    }

    private String actorActual() {
        return AutenticacionActual.obtener()
                .map(Authentication::getName)
                .filter(nombre -> nombre != null && !nombre.isBlank())
                .orElse("sistema");
    }
}
