package com.dessti.crm.comercial.canalventa.application;

import java.util.Locale;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dessti.crm.comercial.canalventa.adapter.out.persistence.CanalVentaRepository;
import com.dessti.crm.comercial.canalventa.domain.CanalVenta;
import com.dessti.crm.platform.audit.AuditoriaPort;
import com.dessti.crm.platform.audit.EventoAuditoria;
import com.dessti.crm.platform.error.ConflictoUnicidadException;
import com.dessti.crm.platform.error.RecursoNoEncontradoException;
import com.dessti.crm.platform.error.ReglaNegocioException;
import com.dessti.crm.platform.security.rbac.AutenticacionActual;
import com.dessti.crm.platform.tenant.TenantContext;

/**
 * Servicio de aplicacion que gobierna el ciclo de vida de los {@link CanalVenta}
 * del catalogo comercial (Req 63.1). Replica el patron establecido por
 * {@code ServicioProductos}/{@code ServicioClientes}.
 *
 * <h2>Operaciones (Req 63.1)</h2>
 * <ul>
 *   <li><strong>crearCanal:</strong> valida el nombre obligatorio, rechaza el
 *       nombre duplicado entre canales activos del tenant (409, doble defensa:
 *       comprobacion previa + traduccion de la violacion del indice unico parcial
 *       ante carreras) y audita el alta.</li>
 *   <li><strong>actualizarCanal:</strong> revalida y persiste; 404 si el canal no
 *       existe/inactivo/otro tenant; 409 si el nuevo nombre colisiona con otro
 *       canal activo.</li>
 *   <li><strong>desactivarCanal:</strong> borrado logico ({@code activo=false})
 *       conservando el historico (preserva la clasificacion para reportes,
 *       Req 63.2); audita.</li>
 *   <li><strong>consultarCanal (Req 4.3, 23.3):</strong> consulta puntual; 404 +
 *       auditoria del intento si no es accesible.</li>
 *   <li><strong>listarCanales:</strong> listado paginado (20/100) filtrable por
 *       nombre sin distinguir mayusculas.</li>
 * </ul>
 *
 * <h2>Aislamiento multi-tenant (Req 23) y auditoria (Req 63.3)</h2>
 * <p>El {@code tenant_id} se deriva del {@link TenantContext} (nunca de la
 * peticion, Req 23.4). Cada creacion, modificacion o baja se registra via
 * {@link AuditoriaPort} como evento de tenant ({@link EventoAuditoria#deTenant})
 * con el actor derivado del contexto de seguridad (Req 10.10). La auditoria de la
 * <em>asignacion</em> del canal a una Oportunidad/Cotizacion (Req 63.3) la
 * realizan sus respectivos servicios.</p>
 */
@Service
public class ServicioCanalesVenta {

    /** Tipo de recurso de auditoria/RBAC del Canal_Venta. */
    static final String RECURSO_CANAL_VENTA = "canal_venta";

    private final CanalVentaRepository canalVentaRepository;
    private final AuditoriaPort auditoria;

    public ServicioCanalesVenta(CanalVentaRepository canalVentaRepository, AuditoriaPort auditoria) {
        this.canalVentaRepository = canalVentaRepository;
        this.auditoria = auditoria;
    }

    /**
     * Da de alta un Canal_Venta con el nombre obligatorio validado y unico entre
     * los canales activos del tenant (Req 63.1, 23.6).
     *
     * @param comando datos del canal a crear.
     * @return el DTO del canal creado.
     * @throws ReglaNegocioException si faltan o son invalidos los datos (422).
     * @throws ConflictoUnicidadException si ya existe un canal activo con el
     *         mismo nombre en el tenant (409, Req 23.6).
     */
    @Transactional
    public CanalVentaDto crearCanal(CrearCanalVentaCommand comando) {
        String actor = actorActual();
        if (comando == null) {
            throw new ReglaNegocioException("Los datos del canal de venta son obligatorios.");
        }
        // Construye y valida el canal (Req 63.1) antes de la comprobacion de
        // unicidad, para que un dato invalido produzca 422 y no 409.
        CanalVenta canal = CanalVenta.crear(comando.nombre(), comando.descripcion(), actor);

        if (canalVentaRepository.existePorNombreActivo(normalizar(canal.getNombre()))) {
            throw new ConflictoUnicidadException(
                    "Ya existe un canal de venta activo con el nombre '" + canal.getNombre() + "'.");
        }

        CanalVenta guardado = guardarTraduciendoUnicidad(canal);
        auditar(actor, "crear", guardado.getId(),
                "creado canal de venta '" + guardado.getNombre() + "'");
        return CanalVentaDto.de(guardado);
    }

    /**
     * Actualiza los datos de un Canal_Venta activo del tenant (Req 63.1).
     *
     * @param canalId identificador del canal.
     * @param comando nuevos datos.
     * @return el DTO del canal actualizado.
     * @throws RecursoNoEncontradoException si no existe, esta inactivo o es de
     *         otro tenant (404, Req 23.3).
     * @throws ConflictoUnicidadException si el nuevo nombre colisiona con otro
     *         canal activo del tenant (409, Req 23.6).
     */
    @Transactional
    public CanalVentaDto actualizarCanal(UUID canalId, ActualizarCanalVentaCommand comando) {
        String actor = actorActual();
        if (comando == null) {
            throw new ReglaNegocioException("Los datos del canal de venta son obligatorios.");
        }
        CanalVenta canal = cargarCanalActivo(canalId, actor);
        canal.actualizar(comando.nombre(), comando.descripcion(), actor);

        // Si cambia el nombre, comprobar que no colisione con OTRO canal activo.
        if (canalVentaRepository.existePorNombreActivo(normalizar(canal.getNombre()))
                && !esMismoNombreDelCanal(canalId, canal.getNombre())) {
            throw new ConflictoUnicidadException(
                    "Ya existe un canal de venta activo con el nombre '" + canal.getNombre() + "'.");
        }

        CanalVenta guardado = guardarTraduciendoUnicidad(canal);
        auditar(actor, "actualizar", guardado.getId(),
                "actualizado canal de venta '" + guardado.getNombre() + "'");
        return CanalVentaDto.de(guardado);
    }

    /**
     * Realiza el borrado logico de un Canal_Venta activo (Req 63.1): marca
     * {@code activo=false} conservando el historico y audita.
     *
     * @param canalId identificador del canal.
     * @return el DTO del canal desactivado.
     * @throws RecursoNoEncontradoException si no existe, ya esta inactivo o es de
     *         otro tenant (404, Req 23.3).
     */
    @Transactional
    public CanalVentaDto desactivarCanal(UUID canalId) {
        String actor = actorActual();
        CanalVenta canal = cargarCanalActivo(canalId, actor);
        canal.desactivar(actor);
        CanalVenta guardado = canalVentaRepository.save(canal);
        auditar(actor, "eliminar", guardado.getId(),
                "baja logica del canal de venta '" + guardado.getNombre() + "'");
        return CanalVentaDto.de(guardado);
    }

    /**
     * Consulta puntual de un Canal_Venta activo del tenant (Req 4.3, 23.3).
     *
     * @param canalId identificador del canal.
     * @return el DTO del canal.
     * @throws RecursoNoEncontradoException si no es accesible (404).
     */
    @Transactional(readOnly = true)
    public CanalVentaDto consultarCanal(UUID canalId) {
        String actor = actorActual();
        return CanalVentaDto.de(cargarCanalActivo(canalId, actor));
    }

    /**
     * Listado paginado de Canales de Venta activos del tenant, filtrable por
     * nombre sin distinguir mayusculas (Req 63.1). Un filtro nulo/blanco lista
     * todos los canales activos.
     *
     * @param filtro   subcadena a buscar en el nombre; {@code null}/blanco lista todos.
     * @param pageable parametros de paginacion ya acotados (20/100).
     * @return la pagina de canales activos como DTOs.
     */
    @Transactional(readOnly = true)
    public Page<CanalVentaDto> listarCanales(String filtro, Pageable pageable) {
        String criterio = (filtro == null) ? "" : normalizar(filtro.strip());
        return canalVentaRepository.buscarActivosPorNombre(criterio, pageable)
                .map(CanalVentaDto::de);
    }

    // ------------------------------------------------------------------
    // Reglas internas
    // ------------------------------------------------------------------

    private CanalVenta cargarCanalActivo(UUID canalId, String actor) {
        if (canalId == null) {
            throw new RecursoNoEncontradoException("No se encontro el canal de venta solicitado.");
        }
        return canalVentaRepository.findByIdAndActivoTrue(canalId)
                .orElseGet(() -> {
                    auditarAccesoCruzado(actor, RECURSO_CANAL_VENTA, canalId);
                    throw new RecursoNoEncontradoException("No se encontro el canal de venta solicitado.");
                });
    }

    private boolean esMismoNombreDelCanal(UUID canalId, String nombre) {
        return canalVentaRepository.findByIdAndActivoTrue(canalId)
                .map(existente -> normalizar(nombre).equals(normalizar(existente.getNombre())))
                .orElse(false);
    }

    private CanalVenta guardarTraduciendoUnicidad(CanalVenta canal) {
        try {
            return canalVentaRepository.saveAndFlush(canal);
        } catch (DataIntegrityViolationException ex) {
            // Carrera concurrente contra el indice parcial
            // uq_canal_venta_nombre_activo_por_tenant (V15).
            throw new ConflictoUnicidadException(
                    "Ya existe un canal de venta activo con el nombre '" + canal.getNombre() + "'.");
        }
    }

    private static String normalizar(String valor) {
        return valor == null ? "" : valor.toLowerCase(Locale.ROOT);
    }

    private void auditar(String actor, String accion, UUID canalId, String detalle) {
        auditoria.registrar(EventoAuditoria.deTenant(
                TenantContext.require(), actor, accion, RECURSO_CANAL_VENTA,
                detalle + " [id=" + canalId + "]", null, null));
    }

    private void auditarAccesoCruzado(String actor, String recurso, UUID recursoId) {
        auditoria.registrar(EventoAuditoria.deTenant(
                TenantContext.require(), actor, "acceso_denegado", recurso,
                "intento de acceso a " + recurso + " no disponible en el tenant [id=" + recursoId + "]",
                null, null));
    }

    private String actorActual() {
        return AutenticacionActual.obtener()
                .map(Authentication::getName)
                .filter(nombre -> nombre != null && !nombre.isBlank())
                .orElse("sistema");
    }
}
