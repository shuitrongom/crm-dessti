package com.dessti.crm.platform.empresas;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dessti.crm.platform.audit.AuditoriaPort;
import com.dessti.crm.platform.audit.EventoAuditoria;
import com.dessti.crm.platform.error.ConflictoUnicidadException;
import com.dessti.crm.platform.error.RecursoNoEncontradoException;
import com.dessti.crm.platform.error.ReglaNegocioException;
import com.dessti.crm.platform.giros.adapter.out.persistence.GiroRepository;
import com.dessti.crm.platform.giros.domain.Giro;
import com.dessti.crm.platform.security.rbac.AutenticacionActual;

/**
 * Servicio de aplicacion de <strong>plataforma</strong> que gobierna la gestion
 * de {@link Plan Planes} por parte del {@code super_admin} (Req 25).
 *
 * <h2>Rediseno {@code plataforma-multigiro}</h2>
 * <p>Un Plan pertenece a un Giro, se cotiza en una moneda y define un precio por
 * modulo; su total es la suma de esos precios. El servicio valida, en el alta y
 * la actualizacion, que:</p>
 * <ul>
 *   <li>el <strong>Giro</strong> exista (404 si no);</li>
 *   <li>la <strong>moneda</strong> exista y este activa (422 si no);</li>
 *   <li>cada <strong>clave de modulo</strong> del mapa de precios exista en el
 *       catalogo de la plataforma y pertenezca al Giro del Plan o al Nucleo Comun
 *       (422 si un modulo especifico es de OTRO Giro; los modulos de Nucleo
 *       siempre se permiten).</li>
 * </ul>
 *
 * <h2>Autorizacion (plataforma)</h2>
 * <p>Las operaciones exigen los permisos {@code plan:*} (V5), que solo posee el
 * {@code super_admin}. La aplicacion las expone via el controlador REST.</p>
 *
 * <h2>Auditoria (Req 25.5)</h2>
 * <p>Cada alta y modificacion de Plan se registra via {@link AuditoriaPort} como
 * evento de <em>plataforma</em> (sin tenant, {@link EventoAuditoria#dePlataforma}).</p>
 */
@Service
public class ServicioPlanes {

    /** Recurso de auditoria/RBAC de nivel plataforma asociado a los Planes. */
    static final String RECURSO_PLAN = "plan";

    private final PlanRepository planRepository;
    private final GiroRepository giroRepository;
    private final CatalogoModulosGiroValidacion catalogoModulosGiroValidacion;
    private final SuscripcionRepository suscripcionRepository;
    private final AuditoriaPort auditoria;

    public ServicioPlanes(PlanRepository planRepository,
                          GiroRepository giroRepository,
                          CatalogoModulosGiroValidacion catalogoModulosGiroValidacion,
                          SuscripcionRepository suscripcionRepository,
                          AuditoriaPort auditoria) {
        this.planRepository = planRepository;
        this.giroRepository = giroRepository;
        this.catalogoModulosGiroValidacion = catalogoModulosGiroValidacion;
        this.suscripcionRepository = suscripcionRepository;
        this.auditoria = auditoria;
    }

    /**
     * Define un nuevo Plan con su Giro, moneda y precio por modulo (Req 25.1).
     *
     * @param comando datos del Plan.
     * @return el DTO del Plan creado.
     * @throws ReglaNegocioException        si faltan datos, {@code maxUsuarios < 0},
     *                                      la moneda es invalida/inactiva o algun
     *                                      modulo no pertenece al Giro/Nucleo.
     * @throws RecursoNoEncontradoException si el Giro indicado no existe.
     * @throws ConflictoUnicidadException   si ya existe un Plan con ese nombre.
     */
    @Transactional
    public PlanDto crearPlan(CrearPlanCommand comando) {
        String actor = actorActual();
        validarComando(comando == null ? null : comando.nombre(),
                comando == null ? 0 : comando.maxUsuarios(), comando);

        String nombre = comando.nombre().strip();
        if (planRepository.existsByNombre(nombre)) {
            throw new ConflictoUnicidadException(
                    "Ya existe un Plan con el nombre '" + nombre + "'.");
        }

        Giro giro = cargarGiro(comando.giroId());
        String moneda = catalogoModulosGiroValidacion.validarMonedaActiva(comando.monedaCodigo());
        Map<String, BigDecimal> precios =
                catalogoModulosGiroValidacion.validarModulosDelGiro(comando.preciosPorModulo(), giro);

        Plan plan = Plan.crear(nombre, comando.maxUsuarios(), comando.duracionDias(),
                giro.getId(), moneda, precios, actor);
        Plan guardado = guardarTraduciendoUnicidad(plan, nombre);

        auditarPlataforma(actor, "crear", detalle(guardado));
        return PlanDto.de(guardado);
    }

    /**
     * Actualiza un Plan existente con su Giro, moneda y precio por modulo (Req 25.1).
     *
     * @param planId  identificador del Plan.
     * @param comando nuevos datos.
     * @return el DTO del Plan actualizado.
     * @throws RecursoNoEncontradoException si el Plan o el Giro no existen.
     * @throws ReglaNegocioException        si faltan datos, {@code maxUsuarios < 0},
     *                                      la moneda es invalida/inactiva o algun
     *                                      modulo no pertenece al Giro/Nucleo.
     * @throws ConflictoUnicidadException   si el nuevo nombre choca con otro Plan.
     */
    @Transactional
    public PlanDto actualizarPlan(UUID planId, ActualizarPlanCommand comando) {
        String actor = actorActual();
        validarComando(comando == null ? null : comando.nombre(),
                comando == null ? 0 : comando.maxUsuarios(), comando);

        Plan plan = cargar(planId);
        String nombre = comando.nombre().strip();
        // Anticipa el conflicto de nombre solo si cambia a uno ya usado por otro Plan.
        if (!nombre.equals(plan.getNombre()) && planRepository.existsByNombre(nombre)) {
            throw new ConflictoUnicidadException(
                    "Ya existe un Plan con el nombre '" + nombre + "'.");
        }

        Giro giro = cargarGiro(comando.giroId());
        String moneda = catalogoModulosGiroValidacion.validarMonedaActiva(comando.monedaCodigo());
        Map<String, BigDecimal> precios =
                catalogoModulosGiroValidacion.validarModulosDelGiro(comando.preciosPorModulo(), giro);

        plan.actualizar(nombre, comando.maxUsuarios(), comando.duracionDias(),
                giro.getId(), moneda, precios, actor);
        Plan guardado = guardarTraduciendoUnicidad(plan, nombre);

        auditarPlataforma(actor, "actualizar", detalle(guardado));
        return PlanDto.de(guardado);
    }

    /**
     * Consulta puntual de un Plan (Req 25.1).
     *
     * @param planId identificador del Plan.
     * @return el DTO del Plan.
     * @throws RecursoNoEncontradoException si el Plan no existe.
     */
    @Transactional(readOnly = true)
    public PlanDto consultarPlan(UUID planId) {
        return PlanDto.de(cargar(planId));
    }

    /**
     * Listado paginado de Planes (Req 25.1).
     *
     * @param pageable parametros de paginacion ya acotados (20/100).
     * @return la pagina de Planes (entidades); el controlador la proyecta a DTO.
     */
    @Transactional(readOnly = true)
    public Page<Plan> listarPlanes(Pageable pageable) {
        return planRepository.findAll(pageable);
    }

    /**
     * Elimina definitivamente un Plan (Req 25.1). Solo es posible si ninguna
     * Suscripcion lo referencia: si al menos una Empresa lo tiene asignado, la
     * eliminacion se rechaza con {@link ReglaNegocioException} (422) informando el
     * numero de Empresas afectadas y como resolverlo. En otro caso, borra el Plan
     * y audita la operacion (evento de plataforma, Req 25.5).
     *
     * @param planId identificador del Plan a eliminar.
     * @throws RecursoNoEncontradoException si el Plan no existe (404).
     * @throws ReglaNegocioException        si alguna Suscripcion referencia el
     *                                      Plan (422).
     */
    @Transactional
    public void eliminarPlan(UUID planId) {
        String actor = actorActual();
        Plan plan = cargar(planId);

        long empresasConPlan = suscripcionRepository.countByPlanId(planId);
        if (empresasConPlan > 0) {
            throw new ReglaNegocioException(
                    "No se puede eliminar el Plan '" + plan.getNombre() + "': " + empresasConPlan
                            + " Empresa(s) lo tienen asignado. Primero cambia el plan de esas empresas.");
        }

        planRepository.delete(plan);
        auditarPlataforma(actor, "eliminar", detalle(plan));
    }

    // ------------------------------------------------------------------
    // Reglas internas
    // ------------------------------------------------------------------

    private Plan cargar(UUID planId) {
        return planRepository.findById(planId)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No se encontro el Plan solicitado."));
    }

    /**
     * Carga el Giro del Plan; un Giro inexistente produce 404.
     *
     * @param giroId identificador del Giro; obligatorio.
     * @return el Giro.
     * @throws ReglaNegocioException        si {@code giroId} es {@code null}.
     * @throws RecursoNoEncontradoException si el Giro no existe.
     */
    private Giro cargarGiro(UUID giroId) {
        if (giroId == null) {
            throw new ReglaNegocioException("El Giro del Plan es obligatorio.");
        }
        return giroRepository.findById(giroId)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No se encontro el Giro indicado para el Plan."));
    }

    private static void validarComando(String nombre, int maxUsuarios, Object comando) {
        if (comando == null) {
            throw new ReglaNegocioException("Los datos del Plan son obligatorios.");
        }
        if (nombre == null || nombre.isBlank()) {
            throw new ReglaNegocioException("El nombre del Plan es obligatorio.");
        }
        if (maxUsuarios < 0) {
            throw new ReglaNegocioException("El numero maximo de Usuarios no puede ser negativo.");
        }
    }

    private Plan guardarTraduciendoUnicidad(Plan plan, String nombre) {
        try {
            return planRepository.saveAndFlush(plan);
        } catch (DataIntegrityViolationException ex) {
            // Carrera concurrente contra el indice unico uq_plan_nombre (V1).
            throw new ConflictoUnicidadException(
                    "Ya existe un Plan con el nombre '" + nombre + "'.");
        }
    }

    private static String detalle(Plan plan) {
        return "plan '" + plan.getNombre() + "' (id=" + plan.getId()
                + ", giro_id=" + plan.getGiroId()
                + ", moneda=" + plan.getMonedaCodigo()
                + ", max_usuarios=" + plan.getMaxUsuarios()
                + ", modulos=" + plan.getModulosHabilitados()
                + ", total=" + plan.getTotal().toPlainString() + ")";
    }

    private void auditarPlataforma(String actor, String accion, String detalle) {
        auditoria.registrar(
                EventoAuditoria.dePlataforma(actor, accion, RECURSO_PLAN, detalle, null, null));
    }

    private String actorActual() {
        return AutenticacionActual.obtener()
                .map(Authentication::getName)
                .filter(nombre -> nombre != null && !nombre.isBlank())
                .orElse("sistema");
    }
}
