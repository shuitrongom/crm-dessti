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
 * de {@link PaqueteSuscripcion Paquetes de Suscripcion} por parte del
 * {@code super_admin} (Req 3, Req 10).
 *
 * <h2>Rediseno {@code plan-vs-suscripcion-contratacion}</h2>
 * <p>El Paquete de Suscripcion es el catalogo de <strong>contratos de corto
 * plazo</strong> (duracion de un año o menos, {@code duracionDias &le; 365}) con
 * opcion de <strong>periodo de prueba</strong>, complementario y excluyente del
 * {@link Plan} (contratos de largo plazo, mayores a un año). Este servicio es el
 * <strong>espejo</strong> de {@link ServicioPlanes}: comparte exactamente las
 * mismas reglas de Giro, moneda y modulos, delegadas en el helper compartido
 * {@link CatalogoModulosGiroValidacion} para no duplicar logica.</p>
 *
 * <p>En el alta y la actualizacion valida que:</p>
 * <ul>
 *   <li>el <strong>Giro</strong> exista (404 si no);</li>
 *   <li>la <strong>moneda</strong> exista y este activa (422 si no), via el
 *       helper;</li>
 *   <li>cada <strong>clave de modulo</strong> del mapa de precios exista en el
 *       catalogo de la plataforma y pertenezca al Giro del Paquete o al Nucleo
 *       Comun (422 si es de OTRO Giro), via el helper;</li>
 *   <li>la <strong>duracion del contrato</strong> ({@code 0 < duracionDias &le;
 *       365}) y la <strong>configuracion de prueba</strong> ({@code
 *       duracionPruebaMeses > 0} y prueba &le; contrato cuando admite prueba) sean
 *       coherentes; estas reglas viven en la entidad {@link PaqueteSuscripcion}
 *       (Req 3.3, 3.4, 3.5, 3.6, 9.1) y afloran como {@link ReglaNegocioException}
 *       (422).</li>
 * </ul>
 *
 * <h2>Autorizacion (plataforma)</h2>
 * <p>Las operaciones se exponen bajo los permisos {@code suscripcion:*} (D8, sin
 * permisos nuevos), que solo posee el {@code super_admin}. La aplicacion las
 * expone via el controlador REST {@code /paquetes-suscripcion}.</p>
 *
 * <h2>Auditoria</h2>
 * <p>Cada alta, modificacion y eliminacion de Paquete se registra via
 * {@link AuditoriaPort} como evento de <em>plataforma</em> (sin tenant,
 * {@link EventoAuditoria#dePlataforma}) sobre el recurso
 * {@code paquete_suscripcion}.</p>
 */
@Service
public class ServicioPaquetesSuscripcion {

    /** Recurso de auditoria de nivel plataforma asociado a los Paquetes de Suscripcion. */
    static final String RECURSO_PAQUETE = "paquete_suscripcion";

    private final PaqueteSuscripcionRepository paqueteSuscripcionRepository;
    private final GiroRepository giroRepository;
    private final CatalogoModulosGiroValidacion catalogoModulosGiroValidacion;
    private final SuscripcionRepository suscripcionRepository;
    private final AuditoriaPort auditoria;

    public ServicioPaquetesSuscripcion(PaqueteSuscripcionRepository paqueteSuscripcionRepository,
                                       GiroRepository giroRepository,
                                       CatalogoModulosGiroValidacion catalogoModulosGiroValidacion,
                                       SuscripcionRepository suscripcionRepository,
                                       AuditoriaPort auditoria) {
        this.paqueteSuscripcionRepository = paqueteSuscripcionRepository;
        this.giroRepository = giroRepository;
        this.catalogoModulosGiroValidacion = catalogoModulosGiroValidacion;
        this.suscripcionRepository = suscripcionRepository;
        this.auditoria = auditoria;
    }

    /**
     * Define un nuevo Paquete de Suscripcion con su Giro, moneda, precio por
     * modulo, duracion de contrato y configuracion de prueba (Req 3.1).
     *
     * <p>La duracion ({@code &le; 365}) y la coherencia de la prueba las valida la
     * entidad {@link PaqueteSuscripcion} (Req 3.3, 3.4, 3.5, 3.6, 9.1),
     * aflorando como {@link ReglaNegocioException} (422).</p>
     *
     * @param comando datos del Paquete.
     * @return el DTO del Paquete creado.
     * @throws ReglaNegocioException        si faltan datos, {@code maxUsuarios < 0},
     *                                      la moneda es invalida/inactiva, algun
     *                                      modulo no pertenece al Giro/Nucleo, la
     *                                      duracion esta fuera de {@code (0, 365]}
     *                                      o la configuracion de prueba es
     *                                      incoherente.
     * @throws RecursoNoEncontradoException si el Giro indicado no existe.
     * @throws ConflictoUnicidadException   si ya existe un Paquete con ese nombre.
     */
    @Transactional
    public PaqueteSuscripcionDto crearPaquete(CrearPaqueteSuscripcionCommand comando) {
        String actor = actorActual();
        validarComando(comando == null ? null : comando.nombre(),
                comando == null ? 0 : comando.maxUsuarios(), comando);

        String nombre = comando.nombre().strip();
        if (paqueteSuscripcionRepository.existsByNombre(nombre)) {
            throw new ConflictoUnicidadException(
                    "Ya existe una Suscripcion con el nombre '" + nombre + "'.");
        }

        Giro giro = cargarGiro(comando.giroId());
        String moneda = catalogoModulosGiroValidacion.validarMonedaActiva(comando.monedaCodigo());
        Map<String, BigDecimal> precios =
                catalogoModulosGiroValidacion.validarModulosDelGiro(comando.preciosPorModulo(), giro);

        PaqueteSuscripcion paquete = PaqueteSuscripcion.crear(nombre, comando.maxUsuarios(),
                giro.getId(), moneda, precios, comando.duracionDias(),
                comando.admitePrueba(), comando.duracionPruebaMeses(), actor);
        PaqueteSuscripcion guardado = guardarTraduciendoUnicidad(paquete, nombre);

        auditarPlataforma(actor, "crear", detalle(guardado));
        return PaqueteSuscripcionDto.de(guardado);
    }

    /**
     * Actualiza un Paquete de Suscripcion existente con su Giro, moneda, precio
     * por modulo, duracion de contrato y configuracion de prueba (Req 3.1).
     *
     * @param paqueteId identificador del Paquete.
     * @param comando   nuevos datos.
     * @return el DTO del Paquete actualizado.
     * @throws RecursoNoEncontradoException si el Paquete o el Giro no existen.
     * @throws ReglaNegocioException        si faltan datos, {@code maxUsuarios < 0},
     *                                      la moneda es invalida/inactiva, algun
     *                                      modulo no pertenece al Giro/Nucleo, la
     *                                      duracion esta fuera de {@code (0, 365]}
     *                                      o la configuracion de prueba es
     *                                      incoherente.
     * @throws ConflictoUnicidadException   si el nuevo nombre choca con otro
     *                                      Paquete.
     */
    @Transactional
    public PaqueteSuscripcionDto actualizarPaquete(UUID paqueteId, ActualizarPaqueteSuscripcionCommand comando) {
        String actor = actorActual();
        validarComando(comando == null ? null : comando.nombre(),
                comando == null ? 0 : comando.maxUsuarios(), comando);

        PaqueteSuscripcion paquete = cargar(paqueteId);
        String nombre = comando.nombre().strip();
        // Anticipa el conflicto de nombre solo si cambia a uno ya usado por otro Paquete.
        if (!nombre.equals(paquete.getNombre()) && paqueteSuscripcionRepository.existsByNombre(nombre)) {
            throw new ConflictoUnicidadException(
                    "Ya existe una Suscripcion con el nombre '" + nombre + "'.");
        }

        Giro giro = cargarGiro(comando.giroId());
        String moneda = catalogoModulosGiroValidacion.validarMonedaActiva(comando.monedaCodigo());
        Map<String, BigDecimal> precios =
                catalogoModulosGiroValidacion.validarModulosDelGiro(comando.preciosPorModulo(), giro);

        paquete.actualizar(nombre, comando.maxUsuarios(), giro.getId(), moneda, precios,
                comando.duracionDias(), comando.admitePrueba(), comando.duracionPruebaMeses(), actor);
        PaqueteSuscripcion guardado = guardarTraduciendoUnicidad(paquete, nombre);

        auditarPlataforma(actor, "actualizar", detalle(guardado));
        return PaqueteSuscripcionDto.de(guardado);
    }

    /**
     * Consulta puntual de un Paquete de Suscripcion (Req 3.1).
     *
     * @param paqueteId identificador del Paquete.
     * @return el DTO del Paquete.
     * @throws RecursoNoEncontradoException si el Paquete no existe.
     */
    @Transactional(readOnly = true)
    public PaqueteSuscripcionDto consultarPaquete(UUID paqueteId) {
        return PaqueteSuscripcionDto.de(cargar(paqueteId));
    }

    /**
     * Listado paginado de Paquetes de Suscripcion (Req 3.1, 10.3).
     *
     * @param pageable parametros de paginacion ya acotados (20/100).
     * @return la pagina de Paquetes (entidades); el controlador la proyecta a DTO.
     */
    @Transactional(readOnly = true)
    public Page<PaqueteSuscripcion> listarPaquetes(Pageable pageable) {
        return paqueteSuscripcionRepository.findAll(pageable);
    }

    /**
     * Elimina definitivamente un Paquete de Suscripcion (Req 10.3). Solo es
     * posible si ningun Contrato ({@link Suscripcion}) lo referencia: si al menos
     * una Empresa lo tiene asignado, la eliminacion se rechaza con
     * {@link ReglaNegocioException} (422) informando el numero de Empresas
     * afectadas y como resolverlo. En otro caso, borra el Paquete y audita la
     * operacion (evento de plataforma).
     *
     * @param paqueteId identificador del Paquete a eliminar.
     * @throws RecursoNoEncontradoException si el Paquete no existe (404).
     * @throws ReglaNegocioException        si algun Contrato referencia el Paquete
     *                                      (422).
     */
    @Transactional
    public void eliminarPaquete(UUID paqueteId) {
        String actor = actorActual();
        PaqueteSuscripcion paquete = cargar(paqueteId);

        long empresasConPaquete = suscripcionRepository.countByPaqueteSuscripcionId(paqueteId);
        if (empresasConPaquete > 0) {
            throw new ReglaNegocioException(
                    "No se puede eliminar la Suscripcion '" + paquete.getNombre() + "': " + empresasConPaquete
                            + " Empresa(s) la tienen asignada. Primero cambia la contratacion de esas empresas.");
        }

        paqueteSuscripcionRepository.delete(paquete);
        auditarPlataforma(actor, "eliminar", detalle(paquete));
    }

    // ------------------------------------------------------------------
    // Reglas internas
    // ------------------------------------------------------------------

    private PaqueteSuscripcion cargar(UUID paqueteId) {
        return paqueteSuscripcionRepository.findById(paqueteId)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No se encontro la Suscripcion solicitada."));
    }

    /**
     * Carga el Giro del Paquete; un Giro inexistente produce 404.
     *
     * @param giroId identificador del Giro; obligatorio.
     * @return el Giro.
     * @throws ReglaNegocioException        si {@code giroId} es {@code null}.
     * @throws RecursoNoEncontradoException si el Giro no existe.
     */
    private Giro cargarGiro(UUID giroId) {
        if (giroId == null) {
            throw new ReglaNegocioException("El Giro de la Suscripcion es obligatorio.");
        }
        return giroRepository.findById(giroId)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No se encontro el Giro indicado para la Suscripcion."));
    }

    private static void validarComando(String nombre, int maxUsuarios, Object comando) {
        if (comando == null) {
            throw new ReglaNegocioException("Los datos de la Suscripcion son obligatorios.");
        }
        if (nombre == null || nombre.isBlank()) {
            throw new ReglaNegocioException("El nombre de la Suscripcion es obligatorio.");
        }
        if (maxUsuarios < 0) {
            throw new ReglaNegocioException("El numero maximo de Usuarios no puede ser negativo.");
        }
    }

    private PaqueteSuscripcion guardarTraduciendoUnicidad(PaqueteSuscripcion paquete, String nombre) {
        try {
            return paqueteSuscripcionRepository.saveAndFlush(paquete);
        } catch (DataIntegrityViolationException ex) {
            // Carrera concurrente contra el indice unico uq_paquete_suscripcion_nombre (V64).
            throw new ConflictoUnicidadException(
                    "Ya existe una Suscripcion con el nombre '" + nombre + "'.");
        }
    }

    private static String detalle(PaqueteSuscripcion paquete) {
        return "paquete_suscripcion '" + paquete.getNombre() + "' (id=" + paquete.getId()
                + ", giro_id=" + paquete.getGiroId()
                + ", moneda=" + paquete.getMonedaCodigo()
                + ", max_usuarios=" + paquete.getMaxUsuarios()
                + ", duracion_dias=" + paquete.getDuracionDias()
                + ", admite_prueba=" + paquete.isAdmitePrueba()
                + ", duracion_prueba_meses=" + paquete.getDuracionPruebaMeses()
                + ", modulos=" + paquete.getModulosHabilitados()
                + ", total=" + paquete.getTotal().toPlainString() + ")";
    }

    private void auditarPlataforma(String actor, String accion, String detalle) {
        auditoria.registrar(
                EventoAuditoria.dePlataforma(actor, accion, RECURSO_PAQUETE, detalle, null, null));
    }

    private String actorActual() {
        return AutenticacionActual.obtener()
                .map(Authentication::getName)
                .filter(nombre -> nombre != null && !nombre.isBlank())
                .orElse("sistema");
    }
}
