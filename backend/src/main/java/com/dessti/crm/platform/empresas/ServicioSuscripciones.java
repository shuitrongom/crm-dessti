package com.dessti.crm.platform.empresas;

import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dessti.crm.platform.audit.AuditoriaPort;
import com.dessti.crm.platform.audit.EventoAuditoria;
import com.dessti.crm.platform.error.RecursoNoEncontradoException;
import com.dessti.crm.platform.error.ReglaNegocioException;
import com.dessti.crm.platform.security.rbac.AutenticacionActual;
import com.dessti.crm.platform.tenant.TenantSessionInitializer;

/**
 * Servicio de aplicacion de <strong>plataforma</strong> que gobierna la gestion
 * de {@link Suscripcion Suscripciones} de las Empresas por el {@code super_admin}
 * (Req 25).
 *
 * <h2>Operaciones (Req 25.2)</h2>
 * <ul>
 *   <li><strong>crearSuscripcion:</strong> asocia una Empresa con un Plan,
 *       registrando la Suscripcion en estado {@code activa} con su periodo de
 *       vigencia. Valida que la Empresa y el Plan existan.</li>
 *   <li><strong>activar / suspender / cancelar:</strong> transiciones de estado
 *       de la Suscripcion. {@code cancelada} es final.</li>
 *   <li><strong>actualizarVigencia:</strong> ajusta el periodo de vigencia.</li>
 *   <li><strong>consultar / listarPorEmpresa:</strong> consulta puntual y por
 *       Empresa.</li>
 * </ul>
 *
 * <h2>Auditoria (Req 25.5)</h2>
 * <p>Cada alta y cambio de una Suscripcion se registra via {@link AuditoriaPort}.
 * Aunque la Suscripcion esta ligada a una Empresa, la operacion la ejecuta el
 * {@code super_admin} de plataforma; se audita como evento de <em>plataforma</em>
 * ({@link EventoAuditoria#dePlataforma}) referenciando la Empresa/tenant afectada
 * en el detalle, en UTC.</p>
 */
@Service
public class ServicioSuscripciones {

    /** Recurso de auditoria/RBAC de nivel plataforma asociado a las Suscripciones. */
    static final String RECURSO_SUSCRIPCION = "suscripcion";

    private final SuscripcionRepository suscripcionRepository;
    private final EmpresaRepository empresaRepository;
    private final PlanRepository planRepository;
    private final PaqueteSuscripcionRepository paqueteSuscripcionRepository;
    private final AuditoriaPort auditoria;
    private final TenantSessionInitializer tenantSession;

    public ServicioSuscripciones(SuscripcionRepository suscripcionRepository,
                                 EmpresaRepository empresaRepository,
                                 PlanRepository planRepository,
                                 PaqueteSuscripcionRepository paqueteSuscripcionRepository,
                                 AuditoriaPort auditoria,
                                 TenantSessionInitializer tenantSession) {
        this.suscripcionRepository = suscripcionRepository;
        this.empresaRepository = empresaRepository;
        this.planRepository = planRepository;
        this.paqueteSuscripcionRepository = paqueteSuscripcionRepository;
        this.auditoria = auditoria;
        this.tenantSession = tenantSession;
    }

    /**
     * Asocia una Suscripcion entre una Empresa y un Plan (Req 25.2). La
     * Suscripcion se crea en estado {@code activa}.
     *
     * @param comando datos de la Suscripcion (Empresa, Plan y vigencia).
     * @return el DTO de la Suscripcion creada.
     * @throws ReglaNegocioException        si faltan datos o la vigencia es invalida.
     * @throws RecursoNoEncontradoException si la Empresa o el Plan no existen.
     */
    @Transactional
    public SuscripcionDto crearSuscripcion(CrearSuscripcionCommand comando) {
        String actor = actorActual();
        if (comando == null) {
            throw new ReglaNegocioException("Los datos de la Suscripcion son obligatorios.");
        }
        if (comando.tenantId() == null) {
            throw new ReglaNegocioException("La Empresa (tenant) es obligatoria.");
        }
        if (comando.planId() == null) {
            throw new ReglaNegocioException("El Plan es obligatorio.");
        }
        if (!empresaRepository.existsById(comando.tenantId())) {
            throw new RecursoNoEncontradoException("No se encontro la Empresa indicada.");
        }
        if (!planRepository.existsById(comando.planId())) {
            throw new RecursoNoEncontradoException("No se encontro el Plan indicado.");
        }

        Suscripcion suscripcion = Suscripcion.crear(
                comando.tenantId(), comando.planId(),
                comando.vigenciaInicio(), comando.vigenciaFin(), actor);
        Suscripcion guardada = suscripcionRepository.save(suscripcion);

        auditar(actor, "crear",
                "creada suscripcion (id=" + guardada.getId() + ") tenant_id=" + guardada.getTenantId()
                        + " plan_id=" + guardada.getPlanId() + " estado=" + guardada.getEstado().valorBd());
        return SuscripcionDto.de(guardada);
    }

    /**
     * Activa una Suscripcion (Req 25.2).
     *
     * @param suscripcionId identificador de la Suscripcion.
     * @return el DTO de la Suscripcion activada.
     * @throws RecursoNoEncontradoException si la Suscripcion no existe.
     * @throws ReglaNegocioException        si la Suscripcion esta cancelada.
     */
    @Transactional
    public SuscripcionDto activar(UUID suscripcionId) {
        return cambiarEstado(suscripcionId, "activar");
    }

    /**
     * Suspende una Suscripcion (Req 25.2).
     *
     * @param suscripcionId identificador de la Suscripcion.
     * @return el DTO de la Suscripcion suspendida.
     * @throws RecursoNoEncontradoException si la Suscripcion no existe.
     * @throws ReglaNegocioException        si la Suscripcion esta cancelada.
     */
    @Transactional
    public SuscripcionDto suspender(UUID suscripcionId) {
        return cambiarEstado(suscripcionId, "suspender");
    }

    /**
     * Cancela una Suscripcion (Req 25.2); es un estado final.
     *
     * @param suscripcionId identificador de la Suscripcion.
     * @return el DTO de la Suscripcion cancelada.
     * @throws RecursoNoEncontradoException si la Suscripcion no existe.
     */
    @Transactional
    public SuscripcionDto cancelar(UUID suscripcionId) {
        return cambiarEstado(suscripcionId, "cancelar");
    }

    /**
     * Actualiza el periodo de vigencia de una Suscripcion (Req 25.2).
     *
     * @param suscripcionId  identificador de la Suscripcion.
     * @param vigenciaInicio nuevo inicio; obligatorio.
     * @param vigenciaFin    nuevo fin; opcional ({@code null} = sin fin).
     * @return el DTO de la Suscripcion con la vigencia actualizada.
     * @throws RecursoNoEncontradoException si la Suscripcion no existe.
     * @throws ReglaNegocioException        si la vigencia es invalida.
     */
    @Transactional
    public SuscripcionDto actualizarVigencia(UUID suscripcionId,
                                             LocalDate vigenciaInicio, LocalDate vigenciaFin) {
        String actor = actorActual();
        Suscripcion suscripcion = cargar(suscripcionId);
        suscripcion.actualizarVigencia(vigenciaInicio, vigenciaFin, actor);
        Suscripcion guardada = suscripcionRepository.save(suscripcion);
        auditar(actor, "actualizar_vigencia",
                "actualizada vigencia de suscripcion (id=" + guardada.getId() + ") tenant_id="
                        + guardada.getTenantId() + " vigencia=[" + guardada.getVigenciaInicio()
                        + ", " + guardada.getVigenciaFin() + "]");
        return SuscripcionDto.de(guardada);
    }

    /**
     * Activa la facturacion de un Contrato en periodo de prueba (Req 8.1-8.4):
     * transiciona de {@link EstadoSuscripcion#EN_PRUEBA EN_PRUEBA} a
     * {@link EstadoSuscripcion#ACTIVA ACTIVA} (la entidad exige el estado
     * EN_PRUEBA; desde cualquier otro estado responde 422).
     *
     * <p><strong>Valores por defecto (Req 8.2):</strong></p>
     * <ul>
     *   <li>Si {@code inicioFacturacion} es {@code null}, se fija el
     *       <strong>primer dia del mes siguiente</strong> a hoy
     *       ({@code LocalDate.now().plusMonths(1).withDayOfMonth(1)}).</li>
     *   <li>Si {@code nuevaVigenciaFin} es {@code null}, se resuelve el fin de
     *       vigencia <strong>conforme a la duracion del Paquete</strong> (D6): se
     *       carga el {@link PaqueteSuscripcion} del Contrato (cuando es de tipo
     *       {@link TipoInstrumento#SUSCRIPCION}) y se calcula
     *       {@code vigenciaInicio + duracionDias}. Se toma la
     *       {@code vigenciaInicio} del Contrato como ancla (fecha desde la que
     *       corre el compromiso), coherente con "el Contrato deriva su
     *       vigenciaFin del catalogo". Si el Paquete no puede resolverse (p. ej.
     *       Contrato de tipo PLAN o Paquete inexistente), {@code nuevaVigenciaFin}
     *       queda como venga (posiblemente {@code null} = sin fin).</li>
     * </ul>
     *
     * @param suscripcionId    identificador del Contrato.
     * @param inicioFacturacion fecha de arranque de la facturacion; {@code null} =
     *                          primer dia del mes siguiente a hoy.
     * @param nuevaVigenciaFin  nuevo fin de vigencia; {@code null} = derivar de la
     *                          duracion del Paquete (si es resoluble).
     * @return el DTO del Contrato con la facturacion activada.
     * @throws RecursoNoEncontradoException si el Contrato no existe.
     * @throws ReglaNegocioException        si el Contrato no esta EN_PRUEBA (422).
     */
    @Transactional
    public SuscripcionDto activarFacturacion(UUID suscripcionId, LocalDate inicioFacturacion,
                                             LocalDate nuevaVigenciaFin) {
        String actor = actorActual();
        Suscripcion suscripcion = cargar(suscripcionId);

        LocalDate inicio = (inicioFacturacion != null)
                ? inicioFacturacion
                : LocalDate.now().plusMonths(1).withDayOfMonth(1);

        LocalDate finVigencia = (nuevaVigenciaFin != null)
                ? nuevaVigenciaFin
                : resolverFinPorDuracionPaquete(suscripcion);

        suscripcion.activarFacturacion(inicio, finVigencia, actor);
        Suscripcion guardada = suscripcionRepository.save(suscripcion);
        auditar(actor, "activar_facturacion",
                "activada facturacion de suscripcion (id=" + guardada.getId() + ") tenant_id="
                        + guardada.getTenantId() + "; inicio_facturacion=" + guardada.getInicioFacturacion()
                        + " vigencia_fin=" + guardada.getVigenciaFin()
                        + " estado=" + guardada.getEstado().valorBd());
        return SuscripcionDto.de(guardada);
    }

    /**
     * Extiende el periodo de prueba de un Contrato en curso (Req 8.2, 8.3):
     * ajusta su {@code vigenciaFin} manteniendo el estado
     * {@link EstadoSuscripcion#EN_PRUEBA EN_PRUEBA}. Solo aplica a Contratos que
     * estan efectivamente en prueba; en cualquier otro estado responde 422.
     *
     * @param suscripcionId   identificador del Contrato.
     * @param nuevaVigenciaFin nuevo fin de la prueba; si se indica no puede ser
     *                         anterior al inicio de vigencia.
     * @return el DTO del Contrato con la prueba extendida.
     * @throws RecursoNoEncontradoException si el Contrato no existe.
     * @throws ReglaNegocioException        si el Contrato no esta EN_PRUEBA o si la
     *                                      vigencia resultante es invalida (422).
     */
    @Transactional
    public SuscripcionDto extenderPrueba(UUID suscripcionId, LocalDate nuevaVigenciaFin) {
        String actor = actorActual();
        Suscripcion suscripcion = cargar(suscripcionId);
        if (suscripcion.getEstado() != EstadoSuscripcion.EN_PRUEBA) {
            throw new ReglaNegocioException("Solo se puede extender una prueba en curso.");
        }
        // Mantiene el estado EN_PRUEBA; solo se mueve el fin de vigencia. La
        // entidad valida el rango (fin >= inicio) al actualizar la vigencia.
        suscripcion.actualizarVigencia(suscripcion.getVigenciaInicio(), nuevaVigenciaFin, actor);
        Suscripcion guardada = suscripcionRepository.save(suscripcion);
        auditar(actor, "extender_prueba",
                "extendida prueba de suscripcion (id=" + guardada.getId() + ") tenant_id="
                        + guardada.getTenantId() + " vigencia=[" + guardada.getVigenciaInicio()
                        + ", " + guardada.getVigenciaFin() + "] estado=" + guardada.getEstado().valorBd());
        return SuscripcionDto.de(guardada);
    }

    /**
     * Convierte un Contrato de suscripcion a un Contrato de Plan (Req 9.2):
     * crea un <strong>nuevo Contrato de tipo {@link TipoInstrumento#PLAN}</strong>
     * para la misma Empresa (vigente desde hoy, sin fin) y <strong>cancela el
     * Contrato origen</strong> para preservar la exclusividad de "un solo
     * Contrato vigente por Empresa" (Req 1.3): tras la conversion la Empresa
     * queda con exactamente un Contrato vigente (el de Plan).
     *
     * @param suscripcionId identificador del Contrato origen (de suscripcion) a convertir.
     * @param planId        Plan destino del nuevo Contrato; debe existir.
     * @return el DTO del nuevo Contrato de tipo PLAN.
     * @throws RecursoNoEncontradoException si el Contrato origen o el Plan destino no existen.
     * @throws ReglaNegocioException        si no se puede construir el nuevo Contrato (XOR/vigencia).
     */
    @Transactional
    public SuscripcionDto convertirAPlan(UUID suscripcionId, UUID planId) {
        String actor = actorActual();
        Suscripcion origen = cargar(suscripcionId);
        if (planId == null) {
            throw new ReglaNegocioException("El Plan destino es obligatorio.");
        }
        if (!planRepository.existsById(planId)) {
            throw new RecursoNoEncontradoException("No se encontro el Plan destino indicado.");
        }

        // Nuevo Contrato de Plan para la misma Empresa, vigente desde hoy y sin fin.
        Suscripcion nuevoPlan = Suscripcion.crearDePlan(origen.getTenantId(), planId,
                LocalDate.now(), null, actor);
        // Cierra el Contrato origen para preservar la exclusividad (un solo
        // Contrato vigente por Empresa, Req 1.3).
        origen.cancelar(actor);

        suscripcionRepository.save(origen);
        Suscripcion guardadoPlan = suscripcionRepository.save(nuevoPlan);
        auditar(actor, "convertir_a_plan",
                "convertida suscripcion (id=" + origen.getId() + ") tenant_id=" + origen.getTenantId()
                        + " a contrato de plan (id=" + guardadoPlan.getId() + ") plan_id=" + planId
                        + "; origen cancelado");
        return SuscripcionDto.de(guardadoPlan);
    }

    /**
     * Fija la moneda de facturacion de la renta de modulos de una Empresa sobre
     * su Suscripcion ACTIVA (monetizacion, V23). El codigo se normaliza a
     * mayusculas (ISO 4217).
     *
     * @param tenantId     Empresa (tenant) cuya Suscripcion activa se ajusta.
     * @param monedaCodigo codigo ISO 4217 de la moneda de facturacion; obligatorio.
     * @return el DTO de la Suscripcion actualizada.
     * @throws RecursoNoEncontradoException si la Empresa no tiene Suscripcion activa.
     * @throws ReglaNegocioException        si el codigo de moneda es invalido.
     */
    @Transactional
    public SuscripcionDto fijarMonedaFacturacion(UUID tenantId, String monedaCodigo) {
        String actor = actorActual();
        if (tenantId == null) {
            throw new ReglaNegocioException("La Empresa (tenant) es obligatoria.");
        }
        // El super_admin opera en contexto de plataforma (sin app.current_tenant).
        // `suscripcion` tiene RLS (tenant_isolation, V2/V53); se fija el tenant
        // destino en esta transaccion (SET LOCAL) ANTES de leer/actualizar su
        // Suscripcion activa, para que la fila sea visible (mismo patron probado
        // en ServicioEmpresas.crearEmpresa) y no falle con un falso 404.
        tenantSession.applyTenant(tenantId);
        Suscripcion suscripcion = suscripcionRepository
                .findFirstByTenantIdAndEstadoOrderByIdAsc(tenantId, EstadoSuscripcion.ACTIVA)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "La Empresa no tiene una Suscripcion activa."));
        suscripcion.fijarMonedaFacturacion(monedaCodigo, actor);
        Suscripcion guardada = suscripcionRepository.save(suscripcion);
        auditar(actor, "fijar_moneda_facturacion",
                "moneda de facturacion de suscripcion (id=" + guardada.getId() + ") tenant_id="
                        + guardada.getTenantId() + " => " + guardada.getMonedaFacturacion());
        return SuscripcionDto.de(guardada);
    }

    /**
     * Actualiza el subconjunto de modulos habilitados para una Empresa sobre su
     * Suscripcion ACTIVA (Req 25.4, 11.1, 11.2). Valida que el override sea
     * SIEMPRE un subconjunto de los modulos del <strong>instrumento contratado</strong>
     * (Plan o Paquete de suscripcion), segun el {@link TipoInstrumento} del
     * Contrato (V64): antes solo se validaba contra el {@link Plan}; ahora
     * tambien funciona cuando el Contrato es de tipo
     * {@link TipoInstrumento#SUSCRIPCION} (valida contra los modulos del
     * {@link PaqueteSuscripcion}).
     *
     * <p><strong>Semantica null-vs-vacio:</strong></p>
     * <ul>
     *   <li>{@code modulos == null} = limpia el override: la Empresa vuelve a
     *       HEREDAR todos los modulos del instrumento contratado.</li>
     *   <li>{@code modulos} (posiblemente vacio) = fija EXACTAMENTE ese
     *       subconjunto; el vacio significa cero modulos habilitados. Debe ser
     *       subconjunto de los modulos del instrumento (si no, 422).</li>
     * </ul>
     *
     * @param tenantId Empresa (tenant) cuya Suscripcion activa se ajusta.
     * @param modulos  subconjunto de modulos, o {@code null} para heredar del instrumento.
     * @return el DTO de la Suscripcion actualizada.
     * @throws RecursoNoEncontradoException si la Empresa no tiene Suscripcion
     *                                      activa o el instrumento asociado no existe.
     * @throws ReglaNegocioException        si algun modulo no pertenece al instrumento (422).
     */
    @Transactional
    public SuscripcionDto actualizarModulosEmpresa(UUID tenantId, Set<String> modulos) {
        String actor = actorActual();
        if (tenantId == null) {
            throw new ReglaNegocioException("La Empresa (tenant) es obligatoria.");
        }
        // Fija el tenant destino en la transaccion antes de leer/actualizar la
        // Suscripcion activa: `suscripcion` esta protegida por RLS y el super_admin
        // no tiene tenant fijado en contexto de plataforma (evita falso 404).
        tenantSession.applyTenant(tenantId);
        Suscripcion suscripcion = suscripcionRepository
                .findFirstByTenantIdAndEstadoOrderByIdAsc(tenantId, EstadoSuscripcion.ACTIVA)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "La Empresa no tiene una Suscripcion activa."));

        // Normalizacion de dependencias ANTES de validar el subconjunto (Req 8.1, 8.3, 8.4).
        //
        // Por que aqui y no en el dominio (Suscripcion.asignarModulos): la validacion de
        // subconjunto contra el instrumento contratado (Plan/Paquete) vive en ESTE servicio
        // (exigirSubconjuntoDelInstrumento), que es quien conoce el instrumento; el dominio
        // (asignarModulos) solo normaliza (recorte/minusculas/dedup) y almacena el override,
        // sin conocer el instrumento. Por eso el punto correcto para aplicar el cierre de
        // dependencias es el servicio, justo antes de validar.
        //
        // Efecto: si el override incluye 'inventario-avanzado', se agrega 'operacion' (cierre
        // transitivo del catalogo). Como el instrumento ya incluye 'operacion' (normalizado al
        // persistir Plan/Paquete, tarea 3.6), la dependencia agregada NO es rechazada por
        // "no pertenece al instrumento". El 422 se conserva cuando el override pide un modulo
        // que el instrumento realmente no ofrece (ni por dependencia).
        //
        // Se preserva la semantica null-vs-vacio: null (heredar) no se toca; una coleccion
        // (incluida la vacia) se normaliza y se usa tanto para validar como para asignar.
        Set<String> modulosNormalizados = (modulos == null)
                ? null
                : com.dessti.crm.platform.modulos.CatalogoDependenciasModulos.normalizar(modulos);
        if (modulosNormalizados != null) {
            exigirSubconjuntoDelInstrumento(modulosNormalizados, suscripcion);
        }
        suscripcion.asignarModulos(modulosNormalizados, actor);
        Suscripcion guardada = suscripcionRepository.save(suscripcion);

        String detalleModulos = (modulos == null)
                ? "modulos=heredados_del_plan"
                : "modulos=" + guardada.getModulosHabilitados();
        auditar(actor, "actualizar_modulos",
                "actualizados modulos de suscripcion (id=" + guardada.getId() + ") tenant_id="
                        + guardada.getTenantId() + "; " + detalleModulos);
        return SuscripcionDto.de(guardada);
    }

    /**
     * Consulta puntual de una Suscripcion (Req 25.2).
     *
     * @param suscripcionId identificador de la Suscripcion.
     * @return el DTO de la Suscripcion.
     * @throws RecursoNoEncontradoException si la Suscripcion no existe.
     */
    @Transactional(readOnly = true)
    public SuscripcionDto consultar(UUID suscripcionId) {
        return SuscripcionDto.de(cargar(suscripcionId));
    }

    /**
     * Lista las Suscripciones de una Empresa (Req 25.2).
     *
     * @param tenantId Empresa (tenant) titular.
     * @return las Suscripciones de la Empresa, proyectadas a DTO.
     */
    @Transactional(readOnly = true)
    public java.util.List<SuscripcionDto> listarPorEmpresa(UUID tenantId) {
        if (tenantId == null) {
            throw new ReglaNegocioException("La Empresa (tenant) es obligatoria.");
        }
        // Listado de plataforma sobre una Empresa concreta: se fija el tenant en
        // la transaccion para que la RLS de `suscripcion` (V2/V53) no oculte sus
        // filas al super_admin (sin tenant en contexto de plataforma).
        tenantSession.applyTenant(tenantId);
        return suscripcionRepository.findByTenantIdOrderByIdAsc(tenantId).stream()
                .map(SuscripcionDto::de)
                .toList();
    }

    // ------------------------------------------------------------------
    // Reglas internas
    // ------------------------------------------------------------------

    private SuscripcionDto cambiarEstado(UUID suscripcionId, String accion) {
        String actor = actorActual();
        Suscripcion suscripcion = cargar(suscripcionId);
        EstadoSuscripcion anterior = suscripcion.getEstado();
        switch (accion) {
            case "activar" -> suscripcion.activar(actor);
            case "suspender" -> suscripcion.suspender(actor);
            case "cancelar" -> suscripcion.cancelar(actor);
            default -> throw new IllegalArgumentException("Accion de estado desconocida: " + accion);
        }
        Suscripcion guardada = suscripcionRepository.save(suscripcion);
        auditar(actor, accion,
                accion + " suscripcion (id=" + guardada.getId() + ") tenant_id="
                        + guardada.getTenantId() + "; estado " + anterior.valorBd()
                        + " -> " + guardada.getEstado().valorBd());
        return SuscripcionDto.de(guardada);
    }

    private Suscripcion cargar(UUID suscripcionId) {
        return suscripcionRepository.findById(suscripcionId)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No se encontro la Suscripcion solicitada."));
    }

    /**
     * Resuelve el nuevo fin de vigencia de un Contrato al activar su facturacion,
     * <strong>conforme a la duracion del Paquete</strong> (D6): si el Contrato es
     * de tipo {@link TipoInstrumento#SUSCRIPCION} y su Paquete es resoluble,
     * devuelve {@code vigenciaInicio + duracionDias}; en caso contrario devuelve
     * {@code null} (sin fin), para que la vigencia quede como venga.
     *
     * @param suscripcion Contrato cuya facturacion se esta activando.
     * @return el fin de vigencia derivado de la duracion del Paquete, o
     *         {@code null} si no es resoluble.
     */
    private LocalDate resolverFinPorDuracionPaquete(Suscripcion suscripcion) {
        UUID paqueteId = suscripcion.getPaqueteSuscripcionId();
        if (suscripcion.getTipoInstrumento() != TipoInstrumento.SUSCRIPCION || paqueteId == null) {
            return null;
        }
        return paqueteSuscripcionRepository.findById(paqueteId)
                .map(paquete -> suscripcion.getVigenciaInicio().plusDays(paquete.getDuracionDias()))
                .orElse(null);
    }

    /**
     * Valida que el override de modulos sea un subconjunto de los modulos del
     * <strong>instrumento contratado</strong> (Req 11.1, 11.2): Plan o Paquete de
     * suscripcion, segun el {@link TipoInstrumento} del Contrato. Reutiliza
     * {@link ModulosPlanValidacion} para Contratos de tipo PLAN y aplica una
     * validacion equivalente contra {@link PaqueteSuscripcion#getModulosHabilitados()}
     * para Contratos de tipo SUSCRIPCION.
     *
     * @param modulos     subconjunto de modulos a validar; nunca {@code null} aqui.
     * @param suscripcion Contrato cuyo instrumento acota los modulos permitidos.
     * @throws RecursoNoEncontradoException si el instrumento (Plan/Paquete) no existe.
     * @throws ReglaNegocioException        si algun modulo no pertenece al instrumento (422).
     */
    private void exigirSubconjuntoDelInstrumento(Set<String> modulos, Suscripcion suscripcion) {
        if (suscripcion.getTipoInstrumento() == TipoInstrumento.SUSCRIPCION) {
            PaqueteSuscripcion paquete = paqueteSuscripcionRepository
                    .findById(suscripcion.getPaqueteSuscripcionId())
                    .orElseThrow(() -> new RecursoNoEncontradoException(
                            "No se encontro el Paquete de suscripcion del Contrato."));
            exigirSubconjuntoDeModulos(modulos, paquete.getModulosHabilitados());
        } else {
            Plan plan = planRepository.findById(suscripcion.getPlanId())
                    .orElseThrow(() -> new RecursoNoEncontradoException(
                            "No se encontro el Plan de la Suscripcion."));
            ModulosPlanValidacion.exigirSubconjuntoDelPlan(modulos, plan);
        }
    }

    /**
     * Valida que {@code modulosElegidos} sea un subconjunto de {@code permitidos}
     * (modulos del instrumento). Espeja la logica de
     * {@link ModulosPlanValidacion#exigirSubconjuntoDelPlan} para el caso del
     * Paquete de suscripcion: normaliza (recorte + minusculas), descarta
     * nulos/vacios y enumera los modulos infractores.
     *
     * @param modulosElegidos subconjunto elegido para la Empresa.
     * @param permitidos      modulos habilitados por el instrumento contratado.
     * @throws ReglaNegocioException si algun modulo elegido no pertenece al instrumento (422).
     */
    private static void exigirSubconjuntoDeModulos(Set<String> modulosElegidos,
                                                   java.util.List<String> permitidos) {
        java.util.Set<String> infractores = new java.util.LinkedHashSet<>();
        for (String modulo : modulosElegidos) {
            if (modulo == null) {
                continue;
            }
            String normalizado = modulo.strip().toLowerCase(java.util.Locale.ROOT);
            if (normalizado.isEmpty()) {
                continue;
            }
            if (!permitidos.contains(normalizado)) {
                infractores.add(normalizado);
            }
        }
        if (!infractores.isEmpty()) {
            throw new ReglaNegocioException(
                    "Los siguientes modulos no estan habilitados por la Suscripcion y no pueden "
                            + "seleccionarse para la Empresa: " + String.join(", ", infractores) + ".");
        }
    }

    private void auditar(String actor, String accion, String detalle) {
        auditoria.registrar(
                EventoAuditoria.dePlataforma(actor, accion, RECURSO_SUSCRIPCION, detalle, null, null));
    }

    private String actorActual() {
        return AutenticacionActual.obtener()
                .map(Authentication::getName)
                .filter(nombre -> nombre != null && !nombre.isBlank())
                .orElse("sistema");
    }
}
