package com.dessti.crm.contabilidad.electronica.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dessti.crm.contabilidad.electronica.domain.modelo.CuentaCatalogoSat;
import com.dessti.crm.contabilidad.electronica.domain.modelo.PolizaSat;
import com.dessti.crm.contabilidad.electronica.domain.modelo.RenglonBalanzaSat;
import com.dessti.crm.contabilidad.electronica.domain.modelo.TransaccionSat;
import com.dessti.crm.contabilidad.electronica.domain.xml.EncabezadoSat;
import com.dessti.crm.contabilidad.electronica.domain.xml.GeneradorBalanzaXml;
import com.dessti.crm.contabilidad.electronica.domain.xml.GeneradorCatalogoXml;
import com.dessti.crm.contabilidad.electronica.domain.xml.GeneradorPolizasXml;
import com.dessti.crm.contabilidad.polizas.adapter.out.persistence.CuentaContableRepository;
import com.dessti.crm.contabilidad.polizas.adapter.out.persistence.PolizaContableRepository;
import com.dessti.crm.contabilidad.polizas.domain.CuentaContable;
import com.dessti.crm.contabilidad.polizas.domain.MovimientoPoliza;
import com.dessti.crm.contabilidad.polizas.domain.NaturalezaCuenta;
import com.dessti.crm.contabilidad.polizas.domain.PolizaContable;
import com.dessti.crm.contabilidad.reportes.adapter.out.persistence.ReportesContablesRepository;
import com.dessti.crm.contabilidad.reportes.adapter.out.persistence.SaldoCuentaProjection;
import com.dessti.crm.platform.audit.AuditoriaPort;
import com.dessti.crm.platform.audit.EventoAuditoria;
import com.dessti.crm.platform.error.ReglaNegocioException;
import com.dessti.crm.platform.security.rbac.AutenticacionActual;
import com.dessti.crm.platform.tenant.TenantContext;

/**
 * Servicio de aplicacion de la <strong>Contabilidad Electronica SAT</strong>
 * (Anexo 24): compone la vista previa y genera los XML oficiales del Catalogo de
 * Cuentas (Req 2), la Balanza de Comprobacion (Req 3) y las Polizas del Periodo
 * (Req 4), reutilizando el catalogo de cuentas y las polizas ya persistidas.
 *
 * <h2>Aislamiento y auditoria</h2>
 * <p>Todas las consultas se acotan al tenant vigente (filtro de Hibernate + RLS de
 * V33); el {@code tenant_id} y el RFC provienen del {@link TenantContext} /
 * {@link DatosFiscalesEmpresaPort}, nunca de la peticion (Req 23.4, 7.1). Cada
 * exportacion se audita (Req 2.7, 3.7, 4.6).</p>
 */
@Service
public class ServicioContabilidadElectronica {

    /** Version del esquema de Contabilidad Electronica del SAT. */
    static final String VERSION_SAT = "1.3";

    /** Escala monetaria (dos decimales). */
    private static final int ESCALA = 2;

    private static final String RECURSO = "contabilidad_electronica";

    private final CuentaContableRepository cuentaContableRepository;
    private final PolizaContableRepository polizaContableRepository;
    private final ReportesContablesRepository reportesContablesRepository;
    private final DatosFiscalesEmpresaPort datosFiscales;
    private final AuditoriaPort auditoria;

    public ServicioContabilidadElectronica(
            CuentaContableRepository cuentaContableRepository,
            PolizaContableRepository polizaContableRepository,
            ReportesContablesRepository reportesContablesRepository,
            DatosFiscalesEmpresaPort datosFiscales,
            AuditoriaPort auditoria) {
        this.cuentaContableRepository = cuentaContableRepository;
        this.polizaContableRepository = polizaContableRepository;
        this.reportesContablesRepository = reportesContablesRepository;
        this.datosFiscales = datosFiscales;
        this.auditoria = auditoria;
    }

    // ==================================================================
    // Catalogo de cuentas (Req 2)
    // ==================================================================

    /**
     * Vista previa del Catalogo_XML (Req 5.1, 5.2): cuenta las cuentas activas
     * amarradas y lista los codigos de las cuentas activas SIN amarrar.
     *
     * @return la vista previa del catalogo.
     */
    @Transactional(readOnly = true)
    public VistaPreviaCatalogoDto vistaPreviaCatalogo() {
        List<CuentaContable> activas = cuentaContableRepository.listarTodasOrdenadas(true);
        List<String> sinAmarrar = new ArrayList<>();
        int amarradas = 0;
        for (CuentaContable c : activas) {
            if (c.getCodigoAgrupadorSat() == null || c.getCodigoAgrupadorSat().isBlank()) {
                sinAmarrar.add(c.getCodigo());
            } else {
                amarradas++;
            }
        }
        return new VistaPreviaCatalogoDto(amarradas, sinAmarrar);
    }

    /**
     * Genera el Catalogo_XML del SAT (Req 2). Solo incluye las cuentas activas con
     * codigo agrupador amarrado (las no amarradas se reportan en la vista previa). Si
     * ninguna cuenta esta amarrada, rechaza con 422 (no tiene sentido un catalogo
     * vacio para el SAT). Audita la exportacion (Req 2.7).
     *
     * @param anio anio de inicio de vigencia del catalogo.
     * @param mes  mes de inicio de vigencia (1-12).
     * @return el archivo XML del catalogo (nombre {@code <RFC><AAAA><MM>CT.xml}).
     */
    @Transactional(readOnly = true)
    public ArchivoXmlDto exportarCatalogo(int anio, int mes) {
        validarPeriodo(anio, mes);
        String rfc = rfcDelTenant();
        List<CuentaCatalogoSat> cuentas = new ArrayList<>();
        for (CuentaContable c : cuentaContableRepository.listarTodasOrdenadas(true)) {
            String agrup = c.getCodigoAgrupadorSat();
            if (agrup == null || agrup.isBlank()) {
                continue;
            }
            int nivel = c.getCodigo() != null && c.getCodigo().contains(".") ? 2 : 1;
            cuentas.add(new CuentaCatalogoSat(
                    agrup, c.getCodigo(), c.getNombre(), nivel, naturSat(c.getNaturaleza())));
        }
        if (cuentas.isEmpty()) {
            throw new ReglaNegocioException(
                    "No hay Cuentas_Contables activas amarradas a un codigo agrupador del SAT. "
                            + "Amarre al menos una cuenta antes de exportar el catalogo.");
        }
        EncabezadoSat encabezado = EncabezadoSat.de(VERSION_SAT, rfc, anio, mes);
        String xml = GeneradorCatalogoXml.generar(encabezado, cuentas);
        auditarExportacion("catalogo", anio, mes, cuentas.size());
        return new ArchivoXmlDto(nombreArchivo(rfc, anio, mes, "CT"), xml);
    }

    // ==================================================================
    // Balanza de comprobacion (Req 3)
    // ==================================================================

    /**
     * Vista previa de la Balanza_XML (Req 5.1, 5.3): numero de cuentas, totales del
     * periodo y si la balanza cuadra.
     *
     * @param anio anio del periodo.
     * @param mes  mes del periodo (1-12).
     * @return la vista previa de la balanza.
     */
    @Transactional(readOnly = true)
    public VistaPreviaBalanzaDto vistaPreviaBalanza(int anio, int mes) {
        validarPeriodo(anio, mes);
        List<RenglonBalanzaSat> renglones = construirBalanza(anio, mes);
        BigDecimal totalDebe = BigDecimal.ZERO.setScale(ESCALA, RoundingMode.HALF_UP);
        BigDecimal totalHaber = BigDecimal.ZERO.setScale(ESCALA, RoundingMode.HALF_UP);
        for (RenglonBalanzaSat r : renglones) {
            totalDebe = totalDebe.add(r.debe());
            totalHaber = totalHaber.add(r.haber());
        }
        boolean cuadra = totalDebe.compareTo(totalHaber) == 0;
        return new VistaPreviaBalanzaDto(renglones.size(), totalDebe, totalHaber, cuadra);
    }

    /**
     * Genera la Balanza_XML del SAT (Req 3). Si la balanza del periodo no cuadra
     * (total de cargos != total de abonos), rechaza con 422 informando la diferencia
     * (el SAT rechazaria una balanza descuadrada). Audita la exportacion (Req 3.7).
     *
     * @param anio anio del periodo.
     * @param mes  mes del periodo (1-12).
     * @return el archivo XML de la balanza (nombre {@code <RFC><AAAA><MM>BN.xml}).
     */
    @Transactional(readOnly = true)
    public ArchivoXmlDto exportarBalanza(int anio, int mes) {
        validarPeriodo(anio, mes);
        String rfc = rfcDelTenant();
        List<RenglonBalanzaSat> renglones = construirBalanza(anio, mes);

        BigDecimal totalDebe = BigDecimal.ZERO.setScale(ESCALA, RoundingMode.HALF_UP);
        BigDecimal totalHaber = BigDecimal.ZERO.setScale(ESCALA, RoundingMode.HALF_UP);
        for (RenglonBalanzaSat r : renglones) {
            totalDebe = totalDebe.add(r.debe());
            totalHaber = totalHaber.add(r.haber());
        }
        if (totalDebe.compareTo(totalHaber) != 0) {
            BigDecimal diferencia = totalDebe.subtract(totalHaber).setScale(ESCALA, RoundingMode.HALF_UP);
            throw new ReglaNegocioException(
                    "La balanza del periodo no cuadra: los cargos (" + totalDebe.toPlainString()
                            + ") difieren de los abonos (" + totalHaber.toPlainString() + ") en "
                            + diferencia.toPlainString() + ". Corrija las polizas antes de exportar.");
        }
        EncabezadoSat encabezado = EncabezadoSat.de(VERSION_SAT, rfc, anio, mes);
        String xml = GeneradorBalanzaXml.generar(encabezado, "N", renglones);
        auditarExportacion("balanza", anio, mes, renglones.size());
        return new ArchivoXmlDto(nombreArchivo(rfc, anio, mes, "BN"), xml);
    }

    // ==================================================================
    // Polizas del periodo (Req 4)
    // ==================================================================

    /**
     * Vista previa del Polizas_XML (Req 5.1): numero de polizas y transacciones del
     * periodo.
     *
     * @param anio anio del periodo.
     * @param mes  mes del periodo (1-12).
     * @return la vista previa de polizas.
     */
    @Transactional(readOnly = true)
    public VistaPreviaPolizasDto vistaPreviaPolizas(int anio, int mes) {
        validarPeriodo(anio, mes);
        YearMonth ym = YearMonth.of(anio, mes);
        List<PolizaContable> polizas = polizaContableRepository.listarPorPeriodo(
                ym.atDay(1), ym.atEndOfMonth());
        int transacciones = 0;
        for (PolizaContable p : polizas) {
            transacciones += p.getRenglones().size();
        }
        return new VistaPreviaPolizasDto(polizas.size(), transacciones);
    }

    /**
     * Genera el Polizas_XML del SAT (Req 4). Audita la exportacion (Req 4.6).
     *
     * @param anio anio del periodo.
     * @param mes  mes del periodo (1-12).
     * @return el archivo XML de polizas (nombre {@code <RFC><AAAA><MM>PL.xml}).
     */
    @Transactional(readOnly = true)
    public ArchivoXmlDto exportarPolizas(int anio, int mes) {
        validarPeriodo(anio, mes);
        String rfc = rfcDelTenant();
        YearMonth ym = YearMonth.of(anio, mes);
        Map<java.util.UUID, CuentaContable> cuentasPorId = new LinkedHashMap<>();
        for (CuentaContable c : cuentaContableRepository.listarTodasOrdenadas(null)) {
            cuentasPorId.put(c.getId(), c);
        }
        List<PolizaContable> polizas = polizaContableRepository.listarPorPeriodo(
                ym.atDay(1), ym.atEndOfMonth());
        List<PolizaSat> modelo = new ArrayList<>();
        for (PolizaContable p : polizas) {
            List<TransaccionSat> transacciones = new ArrayList<>();
            for (MovimientoPoliza m : p.getRenglones()) {
                CuentaContable cuenta = cuentasPorId.get(m.getCuentaContableId());
                String numCta = cuenta != null ? cuenta.getCodigo() : "";
                String desCta = cuenta != null ? cuenta.getNombre() : "";
                transacciones.add(new TransaccionSat(
                        numCta, desCta, p.getConcepto(), m.getCargo(), m.getAbono()));
            }
            modelo.add(new PolizaSat(
                    "P" + p.getFecha() + "-" + p.getId(), p.getFecha(), p.getConcepto(), transacciones));
        }
        EncabezadoSat encabezado = EncabezadoSat.de(VERSION_SAT, rfc, anio, mes);
        String xml = GeneradorPolizasXml.generar(encabezado, "AF", modelo);
        auditarExportacion("polizas", anio, mes, modelo.size());
        return new ArchivoXmlDto(nombreArchivo(rfc, anio, mes, "PL"), xml);
    }

    // ==================================================================
    // Reglas internas
    // ==================================================================

    /**
     * Construye los renglones de la balanza del periodo por cuenta: saldo inicial
     * (movimientos acumulados ANTES del periodo, con signo por naturaleza),
     * cargos/abonos del periodo y saldo final. Reutiliza la agregacion de
     * {@link ReportesContablesRepository}. Determinista (ordenado por codigo).
     */
    private List<RenglonBalanzaSat> construirBalanza(int anio, int mes) {
        YearMonth ym = YearMonth.of(anio, mes);
        LocalDate primerDia = ym.atDay(1);
        LocalDate ultimoDia = ym.atEndOfMonth();

        // Saldo inicial: agregado de TODO lo anterior al periodo, por cuenta.
        Map<String, SaldoAgregado> previos = indexarPorCodigo(
                reportesContablesRepository.agregarSaldosPorCuenta(null, primerDia.minusDays(1)));
        // Movimientos del periodo, por cuenta.
        Map<String, SaldoAgregado> delPeriodo = indexarPorCodigo(
                reportesContablesRepository.agregarSaldosPorCuenta(primerDia, ultimoDia));

        // Union de codigos: cuentas con saldo previo o con movimiento en el periodo.
        Map<String, SaldoAgregado> union = new LinkedHashMap<>();
        previos.forEach(union::putIfAbsent);
        delPeriodo.forEach(union::putIfAbsent);

        List<RenglonBalanzaSat> renglones = new ArrayList<>();
        for (String codigo : union.keySet().stream().sorted().toList()) {
            SaldoAgregado prev = previos.get(codigo);
            SaldoAgregado per = delPeriodo.get(codigo);
            NaturalezaCuenta naturaleza = per != null ? per.naturaleza
                    : (prev != null ? prev.naturaleza : NaturalezaCuenta.DEUDORA);

            BigDecimal saldoIni = saldoSegunNaturaleza(
                    prev != null ? prev.cargos : BigDecimal.ZERO,
                    prev != null ? prev.abonos : BigDecimal.ZERO,
                    naturaleza);
            BigDecimal debe = escala(per != null ? per.cargos : BigDecimal.ZERO);
            BigDecimal haber = escala(per != null ? per.abonos : BigDecimal.ZERO);
            BigDecimal delta = saldoSegunNaturaleza(debe, haber, naturaleza);
            BigDecimal saldoFin = escala(saldoIni.add(delta));

            renglones.add(new RenglonBalanzaSat(codigo, saldoIni, debe, haber, saldoFin));
        }
        return renglones;
    }

    private Map<String, SaldoAgregado> indexarPorCodigo(List<SaldoCuentaProjection> proyecciones) {
        Map<String, SaldoAgregado> mapa = new LinkedHashMap<>();
        for (SaldoCuentaProjection p : proyecciones) {
            mapa.put(p.getCodigo(), new SaldoAgregado(
                    escala(p.getCargos()), escala(p.getAbonos()),
                    NaturalezaCuenta.desdeValorBd(p.getNaturaleza())));
        }
        return mapa;
    }

    /**
     * Saldo con signo segun la naturaleza: deudora = cargos - abonos; acreedora =
     * abonos - cargos. El resultado se presenta como magnitud del saldo de la cuenta.
     */
    private BigDecimal saldoSegunNaturaleza(BigDecimal cargos, BigDecimal abonos,
                                            NaturalezaCuenta naturaleza) {
        BigDecimal c = cargos == null ? BigDecimal.ZERO : cargos;
        BigDecimal a = abonos == null ? BigDecimal.ZERO : abonos;
        BigDecimal saldo = (naturaleza == NaturalezaCuenta.ACREEDORA)
                ? a.subtract(c)
                : c.subtract(a);
        return escala(saldo);
    }

    private BigDecimal escala(BigDecimal v) {
        return (v == null ? BigDecimal.ZERO : v).setScale(ESCALA, RoundingMode.HALF_UP);
    }

    private String naturSat(NaturalezaCuenta naturaleza) {
        return naturaleza == NaturalezaCuenta.ACREEDORA ? "A" : "D";
    }

    private void validarPeriodo(int anio, int mes) {
        if (anio < 2000 || anio > 2999) {
            throw new ReglaNegocioException("El anio del periodo es invalido.");
        }
        if (mes < 1 || mes > 12) {
            throw new ReglaNegocioException("El mes del periodo debe estar entre 1 y 12.");
        }
    }

    private String rfcDelTenant() {
        return datosFiscales.rfcDe(TenantContext.require());
    }

    private String nombreArchivo(String rfc, int anio, int mes, String sufijo) {
        return rfc + anio + String.format("%02d", mes) + sufijo + ".xml";
    }

    private void auditarExportacion(String tipo, int anio, int mes, int elementos) {
        auditoria.registrar(EventoAuditoria.deTenant(
                TenantContext.require(), actorActual(), "exportar", RECURSO,
                "exportacion de Contabilidad Electronica '" + tipo + "' [periodo=" + anio + "-"
                        + String.format("%02d", mes) + ", elementos=" + elementos + "]", null, null));
    }

    private String actorActual() {
        return AutenticacionActual.obtener()
                .map(Authentication::getName)
                .filter(nombre -> nombre != null && !nombre.isBlank())
                .orElse("sistema");
    }

    /** Agregado interno de cargos/abonos y naturaleza de una cuenta. */
    private record SaldoAgregado(BigDecimal cargos, BigDecimal abonos, NaturalezaCuenta naturaleza) {
    }
}
