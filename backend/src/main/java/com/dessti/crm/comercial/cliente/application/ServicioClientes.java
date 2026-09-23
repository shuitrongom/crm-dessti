package com.dessti.crm.comercial.cliente.application;

import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dessti.crm.comercial.cliente.adapter.out.persistence.ClienteRepository;
import com.dessti.crm.comercial.cliente.adapter.out.persistence.ContactoRepository;
import com.dessti.crm.comercial.cliente.domain.Cliente;
import com.dessti.crm.comercial.cliente.domain.Contacto;
import com.dessti.crm.comercial.cliente.domain.DatosBasicosCliente;
import com.dessti.crm.platform.audit.AuditoriaPort;
import com.dessti.crm.platform.audit.EventoAuditoria;
import com.dessti.crm.platform.error.ConflictoUnicidadException;
import com.dessti.crm.platform.error.RecursoNoEncontradoException;
import com.dessti.crm.platform.security.rbac.AutenticacionActual;
import com.dessti.crm.platform.tenant.TenantContext;

/**
 * Servicio de aplicacion del modulo comercial-crm que gobierna el ciclo de vida
 * de los {@link Cliente} y sus {@link Contacto} dentro de una Empresa (Req 5).
 * Es el primer modulo de negocio y establece el patron que los modulos
 * posteriores replican.
 *
 * <h2>Operaciones (Req 5)</h2>
 * <ul>
 *   <li><strong>crearCliente (Req 5.1, 5.2, 5.3):</strong> valida los datos
 *       obligatorios, rechaza el RFC duplicado entre Clientes activos (409,
 *       doble defensa: comprobacion previa {@code existsByRfcAndActivoTrue} +
 *       traduccion de la violacion del indice unico parcial ante carreras) y
 *       audita el alta.</li>
 *   <li><strong>actualizarCliente (Req 5.4):</strong> persiste los cambios y
 *       audita; 404 si el Cliente no existe/pertenece a otro tenant.</li>
 *   <li><strong>desactivarCliente (Req 5.9):</strong> borrado logico
 *       ({@code activo=false}) conservando el historico; audita.</li>
 *   <li><strong>listarClientes (Req 5.7, 5.8):</strong> listado paginado
 *       (20/100) filtrable por nombre o RFC sin distinguir mayusculas.</li>
 *   <li><strong>consultarCliente (Req 4.3, 23.3):</strong> consulta puntual;
 *       404 + auditoria del intento si el Cliente no existe o pertenece a otro
 *       tenant.</li>
 *   <li><strong>asociarContacto (Req 5.5, 5.6):</strong> asocia un Contacto solo
 *       a un Cliente activo; 404 si el Cliente no existe/otro tenant, 422 si esta
 *       inactivo; audita.</li>
 * </ul>
 *
 * <h2>Aislamiento multi-tenant en dos capas (Req 23)</h2>
 * <p>El {@code tenant_id} se deriva del {@link TenantContext} (nunca de la
 * peticion, Req 23.4) y {@code TenantScopedEntity} lo asigna al persistir. Las
 * consultas del repositorio quedan acotadas al tenant por el filtro global de
 * Hibernate (Capa 1) y por la Row-Level Security de PostgreSQL (Capa 2, V11).
 * Como consecuencia, una busqueda por {@code id} de un recurso de otro tenant
 * devuelve vacio, que este servicio traduce a 404 y <em>audita como intento de
 * acceso cruzado</em> (Req 4.3, 23.3).</p>
 *
 * <h2>Auditoria (Req 5.4, 5.9)</h2>
 * <p>Cada operacion se registra via {@link AuditoriaPort} como evento de tenant
 * ({@link EventoAuditoria#deTenant}) con el actor derivado del contexto de
 * seguridad, sin incluir secretos (Req 10.10).</p>
 */
@Service
public class ServicioClientes {

    /** Tipo de recurso de auditoria/RBAC del Cliente. */
    static final String RECURSO_CLIENTE = "cliente";

    /** Tipo de recurso de auditoria/RBAC del Contacto. */
    static final String RECURSO_CONTACTO = "contacto";

    private final ClienteRepository clienteRepository;
    private final ContactoRepository contactoRepository;
    private final AuditoriaPort auditoria;

    public ServicioClientes(ClienteRepository clienteRepository,
                            ContactoRepository contactoRepository,
                            AuditoriaPort auditoria) {
        this.clienteRepository = clienteRepository;
        this.contactoRepository = contactoRepository;
        this.auditoria = auditoria;
    }

    /**
     * Registra un Cliente con los datos obligatorios validados (Req 5.1, 5.2) y
     * un RFC unico entre los Clientes activos del tenant (Req 5.3, 23.6).
     *
     * @param comando datos del Cliente a crear.
     * @return el DTO del Cliente creado.
     * @throws com.dessti.crm.platform.error.ReglaNegocioException si faltan o
     *         son invalidos los datos obligatorios (422, Req 5.2).
     * @throws ConflictoUnicidadException si ya existe un Cliente activo con el
     *         mismo RFC en el tenant (409, Req 5.3).
     */
    @Transactional
    public ClienteDto crearCliente(CrearClienteCommand comando) {
        String actor = actorActual();
        if (comando == null) {
            throw new com.dessti.crm.platform.error.ReglaNegocioException(
                    "Los datos del Cliente son obligatorios.");
        }

        // Construye y valida el Cliente (Req 5.1, 5.2) antes de la comprobacion de
        // unicidad, para que un dato invalido produzca 422 y no 409.
        Cliente cliente = Cliente.crear(
                comando.nombre(), comando.rfc(), comando.email(), comando.telefono(),
                datosBasicosDeCreacion(comando), actor);

        // Pre-comprobacion de unicidad de RFC entre activos del tenant (Req 5.3).
        if (clienteRepository.existsByRfcAndActivoTrue(cliente.getRfc())) {
            throw new ConflictoUnicidadException(
                    "Ya existe un Cliente activo con el identificador fiscal '"
                            + cliente.getRfc() + "'.");
        }

        Cliente guardado = guardarClienteTraduciendoUnicidad(cliente);
        auditarCliente(actor, "crear", guardado.getId(),
                "creado cliente '" + guardado.getNombre() + "' (rfc=" + guardado.getRfc() + ")");
        return ClienteDto.de(guardado);
    }

    /**
     * Actualiza los datos de un Cliente activo del tenant (Req 5.4).
     *
     * @param clienteId identificador del Cliente.
     * @param comando   nuevos datos.
     * @return el DTO del Cliente actualizado.
     * @throws RecursoNoEncontradoException si el Cliente no existe, esta inactivo
     *         o pertenece a otro tenant (404, Req 23.3).
     * @throws ConflictoUnicidadException si el nuevo RFC colisiona con otro
     *         Cliente activo del tenant (409, Req 5.3).
     */
    @Transactional
    public ClienteDto actualizarCliente(UUID clienteId, ActualizarClienteCommand comando) {
        String actor = actorActual();
        if (comando == null) {
            throw new com.dessti.crm.platform.error.ReglaNegocioException(
                    "Los datos del Cliente son obligatorios.");
        }
        Cliente cliente = cargarClienteActivo(clienteId, actor);

        cliente.actualizar(comando.nombre(), comando.rfc(), comando.email(), comando.telefono(),
                datosBasicosDeActualizacion(comando), actor);

        // Si cambia el RFC, comprobar que no colisione con OTRO Cliente activo.
        if (clienteRepository.existsByRfcAndActivoTrue(cliente.getRfc())
                && !esMismoRfcDelCliente(clienteId, cliente.getRfc())) {
            throw new ConflictoUnicidadException(
                    "Ya existe un Cliente activo con el identificador fiscal '"
                            + cliente.getRfc() + "'.");
        }

        Cliente guardado = guardarClienteTraduciendoUnicidad(cliente);
        auditarCliente(actor, "actualizar", guardado.getId(),
                "actualizado cliente '" + guardado.getNombre() + "' (rfc=" + guardado.getRfc() + ")");
        return ClienteDto.de(guardado);
    }

    /**
     * Realiza el borrado logico de un Cliente activo del tenant (Req 5.9):
     * marca {@code activo=false} conservando el historico y audita.
     *
     * @param clienteId identificador del Cliente.
     * @return el DTO del Cliente desactivado.
     * @throws RecursoNoEncontradoException si el Cliente no existe, ya esta
     *         inactivo o pertenece a otro tenant (404, Req 23.3).
     */
    @Transactional
    public ClienteDto desactivarCliente(UUID clienteId) {
        String actor = actorActual();
        Cliente cliente = cargarClienteActivo(clienteId, actor);
        cliente.desactivar(actor);
        Cliente guardado = clienteRepository.save(cliente);
        auditarCliente(actor, "eliminar", guardado.getId(),
                "baja logica del cliente '" + guardado.getNombre() + "' (rfc=" + guardado.getRfc() + ")");
        return ClienteDto.de(guardado);
    }

    /**
     * Consulta puntual de un Cliente activo del tenant (Req 4.3, 23.3). Un
     * Cliente inexistente, inactivo o de otro tenant produce 404 y se audita el
     * intento como acceso cruzado.
     *
     * @param clienteId identificador del Cliente.
     * @return el DTO del Cliente.
     * @throws RecursoNoEncontradoException si el Cliente no es accesible (404).
     */
    @Transactional(readOnly = true)
    public ClienteDto consultarCliente(UUID clienteId) {
        String actor = actorActual();
        Cliente cliente = cargarClienteActivo(clienteId, actor);
        return ClienteDto.de(cliente);
    }

    /**
     * Listado paginado de Clientes activos del tenant, filtrable por nombre o
     * RFC sin distinguir mayusculas (Req 5.7, 5.8). Un filtro nulo o en blanco
     * devuelve todos los Clientes activos.
     *
     * @param filtro   subcadena a buscar en nombre o RFC; {@code null}/blanco
     *                 lista todos.
     * @param pageable parametros de paginacion ya acotados (20/100).
     * @return la pagina de Clientes activos como DTOs.
     */
    @Transactional(readOnly = true)
    public Page<ClienteDto> listarClientes(String filtro, Pageable pageable) {
        String criterio = (filtro == null) ? "" : filtro.strip().toLowerCase(java.util.Locale.ROOT);
        return clienteRepository.buscarActivosPorNombreORfc(criterio, pageable)
                .map(ClienteDto::de);
    }

    /**
     * Asocia un Contacto a un Cliente <strong>activo</strong> del tenant
     * (Req 5.5, 5.6) y audita la operacion.
     *
     * @param comando datos del Contacto y del Cliente propietario.
     * @return el DTO del Contacto creado.
     * @throws RecursoNoEncontradoException si el Cliente no existe o pertenece a
     *         otro tenant (404, Req 5.6, 23.3).
     * @throws com.dessti.crm.platform.error.ReglaNegocioException si el Cliente
     *         esta inactivo o los datos del Contacto son invalidos (422, Req 5.6).
     */
    @Transactional
    public ContactoDto asociarContacto(CrearContactoCommand comando) {
        String actor = actorActual();
        if (comando == null || comando.clienteId() == null) {
            throw new com.dessti.crm.platform.error.ReglaNegocioException(
                    "El Cliente destino del Contacto es obligatorio.");
        }
        // Se resuelve el Cliente por id acotado al tenant (Capa 1/2). Si no existe
        // (o es de otro tenant) -> 404 auditado. Si existe pero esta inactivo, la
        // fabrica del Contacto lo rechaza con 422 (Req 5.6).
        Cliente cliente = clienteRepository.findById(comando.clienteId())
                .orElseGet(() -> {
                    auditarAccesoCruzado(actor, RECURSO_CONTACTO, comando.clienteId());
                    throw new RecursoNoEncontradoException("El Cliente indicado no esta disponible.");
                });

        Contacto contacto = Contacto.paraCliente(
                cliente, comando.nombre(), comando.email(), comando.telefono(), actor);
        Contacto guardado = contactoRepository.save(contacto);
        auditarContacto(actor, "crear", guardado.getId(),
                "asociado contacto '" + guardado.getNombre() + "' al cliente " + cliente.getId());
        return ContactoDto.de(guardado);
    }

    // ------------------------------------------------------------------
    // Reglas internas
    // ------------------------------------------------------------------

    /**
     * Proyecta los datos basicos opcionales del comando de alta al value object
     * de dominio {@link DatosBasicosCliente} (Req 5, V59); la normalizacion y la
     * validacion de cada campo las aplica la entidad.
     */
    private static DatosBasicosCliente datosBasicosDeCreacion(CrearClienteCommand c) {
        return new DatosBasicosCliente(
                c.nombreComercial(), c.tipoPersona(), c.telefonoAdicional(),
                c.direccionCalle(), c.direccionCiudad(), c.direccionEstado(),
                c.direccionCp(), c.direccionPais(), c.notas());
    }

    /**
     * Proyecta los datos basicos opcionales del comando de actualizacion al value
     * object de dominio {@link DatosBasicosCliente} (Req 5, V59).
     */
    private static DatosBasicosCliente datosBasicosDeActualizacion(ActualizarClienteCommand c) {
        return new DatosBasicosCliente(
                c.nombreComercial(), c.tipoPersona(), c.telefonoAdicional(),
                c.direccionCalle(), c.direccionCiudad(), c.direccionEstado(),
                c.direccionCp(), c.direccionPais(), c.notas());
    }

    /**
     * Carga un Cliente activo por id dentro del tenant vigente; si no es
     * accesible (inexistente, inactivo o de otro tenant) audita el intento y
     * lanza 404 (Req 4.3, 23.3).
     */
    private Cliente cargarClienteActivo(UUID clienteId, String actor) {
        if (clienteId == null) {
            throw new RecursoNoEncontradoException("No se encontro el Cliente solicitado.");
        }
        return clienteRepository.findByIdAndActivoTrue(clienteId)
                .orElseGet(() -> {
                    auditarAccesoCruzado(actor, RECURSO_CLIENTE, clienteId);
                    throw new RecursoNoEncontradoException("No se encontro el Cliente solicitado.");
                });
    }

    /**
     * Comprueba si el unico Cliente activo con ese RFC en el tenant es el propio
     * Cliente que se esta actualizando (para no confundir "sin cambio de RFC" con
     * un duplicado real).
     */
    private boolean esMismoRfcDelCliente(UUID clienteId, String rfc) {
        return clienteRepository.findByIdAndActivoTrue(clienteId)
                .map(existente -> rfc.equals(existente.getRfc()))
                .orElse(false);
    }

    private Cliente guardarClienteTraduciendoUnicidad(Cliente cliente) {
        try {
            return clienteRepository.saveAndFlush(cliente);
        } catch (DataIntegrityViolationException ex) {
            // Carrera concurrente contra el indice parcial uq_cliente_rfc_activo_por_tenant (V11).
            throw new ConflictoUnicidadException(
                    "Ya existe un Cliente activo con el identificador fiscal '"
                            + cliente.getRfc() + "'.");
        }
    }

    private void auditarCliente(String actor, String accion, UUID clienteId, String detalle) {
        auditoria.registrar(EventoAuditoria.deTenant(
                TenantContext.require(), actor, accion, RECURSO_CLIENTE,
                detalle + " [id=" + clienteId + "]", null, null));
    }

    private void auditarContacto(String actor, String accion, UUID contactoId, String detalle) {
        auditoria.registrar(EventoAuditoria.deTenant(
                TenantContext.require(), actor, accion, RECURSO_CONTACTO,
                detalle + " [id=" + contactoId + "]", null, null));
    }

    /**
     * Audita un intento de acceso a un recurso no accesible (inexistente o de
     * otro tenant), antes de responder 404 (Req 4.3, 23.3).
     */
    private void auditarAccesoCruzado(String actor, String recurso, UUID recursoId) {
        auditoria.registrar(EventoAuditoria.deTenant(
                TenantContext.require(), actor, "acceso_denegado", recurso,
                "intento de acceso a " + recurso + " no disponible en el tenant [id=" + recursoId + "]",
                null, null));
    }

    /**
     * Resuelve el identificador del actor autenticado para la auditoria; si no
     * hay contexto de seguridad, usa "sistema".
     */
    private String actorActual() {
        return AutenticacionActual.obtener()
                .map(Authentication::getName)
                .filter(nombre -> nombre != null && !nombre.isBlank())
                .orElse("sistema");
    }
}
