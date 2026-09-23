package com.dessti.crm.platform.monetizacion.application;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dessti.crm.platform.audit.AuditoriaPort;
import com.dessti.crm.platform.audit.EventoAuditoria;
import com.dessti.crm.platform.empresas.Empresa;
import com.dessti.crm.platform.empresas.EmpresaRepository;
import com.dessti.crm.platform.empresas.EstadoSuscripcion;
import com.dessti.crm.platform.empresas.Plan;
import com.dessti.crm.platform.empresas.PlanRepository;
import com.dessti.crm.platform.empresas.Suscripcion;
import com.dessti.crm.platform.empresas.SuscripcionRepository;
import com.dessti.crm.platform.error.ConflictoUnicidadException;
import com.dessti.crm.platform.error.RecursoNoEncontradoException;
import com.dessti.crm.platform.error.ReglaNegocioException;
import com.dessti.crm.platform.monetizacion.adapter.out.persistence.CatalogoModuloRepository;
import com.dessti.crm.platform.monetizacion.adapter.out.persistence.FacturaRentaRepository;
import com.dessti.crm.platform.monetizacion.domain.CatalogoModulo;
import com.dessti.crm.platform.monetizacion.domain.FacturaRenta;
import com.dessti.crm.platform.monetizacion.domain.FacturaRenta.LineaCruda;
import com.dessti.crm.platform.security.rbac.AutenticacionActual;
import com.dessti.crm.platform.tenant.TenantSessionInitializer;

/**
 * Servicio de plataforma que <strong>calcula y emite la factura de renta</strong>
 * mensual de una Empresa por sus modulos habilitados (V23, Req 24.3).
 *
 * <h2>Modulos habilitados efectivos</h2>
 * <p>Se resuelven igual que el gating ({@code PlanModulosPlanAdapter}): si la
 * Suscripcion ACTIVA de la Empresa tiene un override de modulos
 * ({@link Suscripcion#getModulosHabilitados()} != null), se factura ese
 * subconjunto; si no, se hereda el catalogo completo del {@link Plan}.</p>
 *
 * <h2>Valuacion (precios del Plan)</h2>
 * <p>Cada modulo habilitado se valora con el precio que el propio {@link Plan}
 * define en su mapa {@code preciosModulos} (clave&rarr;precio), expresado en la
 * moneda del Plan ({@link Plan#getMonedaCodigo()}). Ya NO se consultan las
 * tablas de precios de monetizacion (empresa_modulo_precio / precio_modulo): el
 * precio vive dentro del Plan. Un modulo habilitado SIN entrada en el mapa de
 * precios del Plan se OMITE de la factura (no se cobra lo desconocido); un
 * modulo con precio 0 SI se incluye (modulo gratuito, linea con importe 0). El
 * total es la suma de los precios aplicados (HALF_UP, escala 2).</p>
 *
 * <h2>Moneda (sin conversion FX)</h2>
 * <p>El proyecto no realiza conversion de divisas. La factura se emite en la
 * moneda del Plan. Si la Suscripcion ya tiene una moneda de facturacion fijada y
 * NO coincide con la del Plan, se rechaza (422) para no mezclar divisas de forma
 * silenciosa. Si la Suscripcion aun no tiene moneda de facturacion, se usa la
 * del Plan (no se fuerza al operador a fijarla antes de facturar).</p>
 *
 * <h2>Emision</h2>
 * <p>La emision persiste una {@link FacturaRenta} unica por (Empresa, periodo,
 * moneda); un duplicado del mismo periodo produce 409. Requiere que la Empresa
 * tenga una Suscripcion activa con un Plan que tenga al menos un modulo con
 * precio (si no, 422).</p>
 */
@Service
public class ServicioFacturacionRenta {

    static final String RECURSO = "factura_renta";

    private final SuscripcionRepository suscripcionRepository;
    private final PlanRepository planRepository;
    private final CatalogoModuloRepository catalogoRepository;
    private final FacturaRentaRepository facturaRepository;
    private final EmpresaRepository empresaRepository;
    private final FacturaRentaPdfService pdfService;
    private final EmisorProperties emisor;
    private final AuditoriaPort auditoria;
    private final Clock clock;
    private final TenantSessionInitializer tenantSession;

    public ServicioFacturacionRenta(SuscripcionRepository suscripcionRepository,
                                    PlanRepository planRepository,
                                    CatalogoModuloRepository catalogoRepository,
                                    FacturaRentaRepository facturaRepository,
                                    EmpresaRepository empresaRepository,
                                    FacturaRentaPdfService pdfService,
                                    EmisorProperties emisor,
                                    AuditoriaPort auditoria,
                                    Clock clock,
                                    TenantSessionInitializer tenantSession) {
        this.suscripcionRepository = suscripcionRepository;
        this.planRepository = planRepository;
        this.catalogoRepository = catalogoRepository;
        this.facturaRepository = facturaRepository;
        this.empresaRepository = empresaRepository;
        this.pdfService = pdfService;
        this.emisor = emisor;
        this.auditoria = auditoria;
        this.clock = clock;
        this.tenantSession = tenantSession;
    }

    /**
     * Calcula (sin emitir) la renta de una Empresa para un periodo: devuelve las
     * lineas valuadas y el total en la moneda de facturacion de la Empresa. Util
     * para previsualizar antes de emitir.
     *
     * @param tenantId Empresa; obligatorio.
     * @param periodo  periodo a calcular; si es {@code null}, el mes actual.
     * @return el borrador de la factura (no persistido).
     */
    @Transactional(readOnly = true)
    public FacturaRentaDto calcular(UUID tenantId, LocalDate periodo) {
        LocalDate mes = (periodo == null) ? LocalDate.now(clock).withDayOfMonth(1)
                : periodo.withDayOfMonth(1);
        Contexto ctx = resolverContexto(tenantId);
        List<LineaCruda> lineas = valuarModulos(tenantId, ctx);
        if (lineas.isEmpty()) {
            throw new ReglaNegocioException(
                    "No hay modulos habilitados con precio para facturar en la moneda "
                            + ctx.moneda() + ".");
        }
        FacturaRenta borrador = FacturaRenta.emitir(tenantId, mes, ctx.moneda(), lineas, "preview");
        return FacturaRentaDto.de(borrador);
    }

    /**
     * Emite y persiste la factura de renta de una Empresa para un periodo (V23).
     *
     * @param tenantId Empresa; obligatorio.
     * @param periodo  periodo a facturar; si es {@code null}, el mes actual.
     * @return la factura emitida.
     * @throws RecursoNoEncontradoException si la Empresa no tiene Suscripcion activa.
     * @throws ReglaNegocioException si no hay moneda de facturacion o no hay
     *                               modulos con precio para facturar.
     * @throws ConflictoUnicidadException si ya existe factura para ese periodo/moneda.
     */
    @Transactional
    public FacturaRentaDto emitir(UUID tenantId, LocalDate periodo) {
        String actor = actorActual();
        LocalDate mes = (periodo == null) ? LocalDate.now(clock).withDayOfMonth(1)
                : periodo.withDayOfMonth(1);
        Contexto ctx = resolverContexto(tenantId);

        if (facturaRepository.findByTenantIdAndPeriodoAndMonedaCodigo(tenantId, mes, ctx.moneda()).isPresent()) {
            throw new ConflictoUnicidadException(
                    "Ya existe una factura de renta para la Empresa en el periodo " + mes
                            + " y moneda " + ctx.moneda() + ".");
        }

        List<LineaCruda> lineas = valuarModulos(tenantId, ctx);
        if (lineas.isEmpty()) {
            throw new ReglaNegocioException(
                    "No hay modulos habilitados con precio para facturar en la moneda "
                            + ctx.moneda() + ".");
        }

        FacturaRenta factura = FacturaRenta.emitir(tenantId, mes, ctx.moneda(), lineas, actor);
        FacturaRenta guardada;
        try {
            guardada = facturaRepository.saveAndFlush(factura);
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictoUnicidadException(
                    "Ya existe una factura de renta para la Empresa en el periodo " + mes
                            + " y moneda " + ctx.moneda() + ".");
        }

        auditar(actor, "emitir",
                "emitida factura de renta (id=" + guardada.getId() + ") tenant_id=" + tenantId
                        + " periodo=" + mes + " moneda=" + ctx.moneda()
                        + " total=" + guardada.getTotal().toPlainString()
                        + " lineas=" + guardada.getLineas().size());
        return FacturaRentaDto.de(guardada);
    }

    /** Lista las facturas de renta de una Empresa, mas recientes primero. */
    @Transactional(readOnly = true)
    public List<FacturaRentaDto> listarPorEmpresa(UUID tenantId) {
        return facturaRepository.findByTenantIdOrderByPeriodoDesc(tenantId).stream()
                .map(FacturaRentaDto::de).toList();
    }

    /** Consulta puntual de una factura de renta. 404 si no existe. */
    @Transactional(readOnly = true)
    public FacturaRentaDto consultar(UUID facturaId) {
        return FacturaRentaDto.de(facturaRepository.findById(facturaId)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No se encontro la factura de renta solicitada.")));
    }

    /**
     * Genera el PDF PREMIUM del comprobante de renta: carga la factura (404 si no
     * existe), carga la Empresa receptora por su {@code tenant_id} y delega el
     * armado del documento en {@link FacturaRentaPdfService}, enriqueciendolo con
     * los datos del emisor (Dess-TI) y del receptor (Empresa).
     *
     * <p>Si la Empresa receptora ya no existe (por ejemplo, offboarding completado)
     * el PDF se genera igualmente en modo degradado, mostrando el identificador
     * del tenant en lugar de la ficha de la Empresa.</p>
     *
     * @param facturaId factura de renta a representar; obligatorio.
     * @return los bytes del PDF (empieza con la firma {@code %PDF}).
     * @throws RecursoNoEncontradoException si la factura no existe.
     */
    @Transactional(readOnly = true)
    public byte[] generarPdf(UUID facturaId) {
        FacturaRenta factura = facturaRepository.findById(facturaId)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No se encontro la factura de renta solicitada."));
        Empresa empresa = empresaRepository.findById(factura.getTenantId()).orElse(null);
        return pdfService.generar(factura, empresa, emisor);
    }

    // ------------------------------------------------------------------
    // Reglas internas
    // ------------------------------------------------------------------

    /**
     * Contexto de facturacion: modulos habilitados efectivos, moneda de emision
     * (la del Plan) y el mapa de precios del Plan (clave&rarr;precio) con el que
     * se valora cada modulo.
     */
    private record Contexto(List<String> clavesModulos, String moneda,
                            Map<String, BigDecimal> preciosPlan) {
    }

    private Contexto resolverContexto(UUID tenantId) {
        if (tenantId == null) {
            throw new ReglaNegocioException("La Empresa (tenant) es obligatoria.");
        }
        // La operacion la ejecuta el super_admin en contexto de PLATAFORMA (sin
        // app.current_tenant fijado). La tabla `suscripcion` tiene RLS
        // (tenant_isolation, V2/V53); sin fijar el tenant, sus filas quedarian
        // ocultas y la Suscripcion activa existente no seria visible (falso 404).
        // Se fija explicitamente el tenant destino en ESTA transaccion (SET LOCAL,
        // se revierte al terminar) ANTES de leer filas tenant-scoped, mismo patron
        // probado en ServicioEmpresas.crearEmpresa. `factura_renta` y `plan` son de
        // plataforma (SIN RLS, V1/V23), pero fijar el tenant aqui no las afecta.
        tenantSession.applyTenant(tenantId);
        Suscripcion suscripcion = suscripcionRepository
                .findFirstByTenantIdAndEstadoOrderByIdAsc(tenantId, EstadoSuscripcion.ACTIVA)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "La Empresa no tiene una Suscripcion activa que facturar."));

        // El precio de cada modulo vive dentro del Plan (nuevo modelo): se carga el
        // Plan referenciado por la Suscripcion activa.
        Plan plan = planRepository.findById(suscripcion.getPlanId())
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No se encontro el Plan de la Suscripcion."));

        // Moneda de emision (sin conversion FX): siempre la del Plan, porque los
        // precios estan expresados en esa moneda. La moneda de facturacion de la
        // Empresa solo actua como candado de coherencia:
        //   - si esta fijada y difiere de la del Plan -> 422 (no se mezclan divisas);
        //   - si aun no esta fijada -> se usa la del Plan (no se obliga a fijarla).
        String monedaPlan = plan.getMonedaCodigo();
        String monedaEmpresa = suscripcion.getMonedaFacturacion();
        if (monedaEmpresa != null && !monedaEmpresa.isBlank()
                && !monedaEmpresa.equalsIgnoreCase(monedaPlan)) {
            throw new ReglaNegocioException(
                    "La moneda de facturacion de la Empresa (" + monedaEmpresa
                            + ") no coincide con la moneda del Plan (" + monedaPlan
                            + "). Ajusta la moneda de facturacion para que coincida con el Plan.");
        }

        // Modulos habilitados efectivos: override de la Suscripcion o catalogo del Plan.
        List<String> claves = (suscripcion.getModulosHabilitados() != null)
                ? suscripcion.getModulosHabilitados()
                : plan.getModulosHabilitados();

        return new Contexto(claves, monedaPlan, plan.getPreciosModulos());
    }

    /**
     * Valua cada modulo habilitado con el precio que el Plan define para su clave.
     * Un modulo SIN entrada en el mapa de precios del Plan se omite (no se cobra lo
     * desconocido); un modulo con precio 0 se incluye como linea gratuita. El
     * nombre visible se toma del catalogo si el modulo existe alli, o la clave.
     */
    private List<LineaCruda> valuarModulos(UUID tenantId, Contexto ctx) {
        List<LineaCruda> lineas = new ArrayList<>();
        for (String clave : ctx.clavesModulos()) {
            BigDecimal precio = ctx.preciosPlan().get(clave);
            if (precio == null) {
                continue; // modulo habilitado sin precio en el Plan: se omite (sin precio en plan).
            }
            String nombre = catalogoRepository.findByClave(clave)
                    .map(CatalogoModulo::getNombre)
                    .orElse(clave);
            lineas.add(new LineaCruda(clave, nombre, precio));
        }
        return lineas;
    }

    private void auditar(String actor, String accion, String detalle) {
        auditoria.registrar(EventoAuditoria.dePlataforma(actor, accion, RECURSO, detalle, null, null));
    }

    private String actorActual() {
        return AutenticacionActual.obtener()
                .map(Authentication::getName)
                .filter(n -> n != null && !n.isBlank())
                .orElse("sistema");
    }
}