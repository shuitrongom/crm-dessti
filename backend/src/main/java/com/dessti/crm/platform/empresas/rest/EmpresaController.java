package com.dessti.crm.platform.empresas.rest;

import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.dessti.crm.platform.empresas.ActualizarEmpresaCommand;
import com.dessti.crm.platform.empresas.ActualizarMiEmpresaCommand;
import com.dessti.crm.platform.empresas.CrearEmpresaCommand;
import com.dessti.crm.platform.empresas.DatosDescriptivosEmpresa;
import com.dessti.crm.platform.empresas.EmpresaCreadaDto;
import com.dessti.crm.platform.empresas.EmpresaDto;
import com.dessti.crm.platform.empresas.EstadoEmpresa;
import com.dessti.crm.platform.empresas.ResetPasswordAdminDto;
import com.dessti.crm.platform.empresas.ServicioEmpresas;
import com.dessti.crm.platform.empresas.ServicioSuscripciones;
import com.dessti.crm.platform.empresas.SuscripcionDto;
import com.dessti.crm.platform.tenant.TenantContext;
import com.dessti.crm.platform.web.pagination.PageRequestFactory;
import com.dessti.crm.platform.web.pagination.PaginaResponse;

import jakarta.validation.Valid;

/**
 * Adaptador de entrada REST para la administracion de plataforma de Empresas
 * (tenants) por el {@code super_admin} (Req 24, tarea 14.1).
 *
 * <p>Rutas (relativas al context-path {@code /api/v1}):</p>
 * <ul>
 *   <li>{@code POST /empresas} — da de alta una Empresa con su primer
 *       {@code admin_empresa} y su Suscripcion inicial (Req 24.2).</li>
 *   <li>{@code POST /empresas/{id}/activar} — activa una Empresa (Req 24.1).</li>
 *   <li>{@code POST /empresas/{id}/suspender} — suspende una Empresa; impide el
 *       login de sus Usuarios mientras dure (Req 24.4).</li>
 *   <li>{@code POST /empresas/{id}/admin/reset-password} — restablece la
 *       contrasena del {@code admin_empresa} de la Empresa; devuelve una
 *       contrasena temporal cuando no se proporciona una explicita (CHANGE 3).</li>
 *   <li>{@code PUT /empresas/{id}/modulos} — edita el subconjunto de modulos
 *       habilitados de la Empresa sobre su Suscripcion activa (Req 25.4).</li>
 *   <li>{@code GET /empresas/{id}} — consulta una Empresa (Req 24.1).</li>
 *   <li>{@code GET /empresas} — lista paginada (20/100) filtrable por estado
 *       (Req 24.5).</li>
 * </ul>
 *
 * <h2>Autorizacion de plataforma (Req 24.1, 24.3)</h2>
 * <p>Cada operacion exige el permiso atomico de plataforma correspondiente sobre
 * el recurso {@code empresa} ({@code @autorizador.tiene('empresa', ...)}). Estos
 * permisos ({@code empresa:crear}, {@code empresa:leer}, {@code empresa:listar},
 * {@code empresa:cambiar_estado}) se sembraron en V5 y se asignaron
 * <strong>unicamente</strong> al rol {@code super_admin}. Ningun rol de empresa
 * los posee, por lo que Spring Security responde 403 (denegacion por defecto,
 * Req 3.2) a cualquier otro usuario.</p>
 *
 * <h2>Aislamiento del super_admin (Req 24.3)</h2>
 * <p>Este controlador expone exclusivamente datos de <em>plataforma</em> de la
 * Empresa (identidad, estado, auditoria) a traves de {@link EmpresaDto}. No
 * expone ningun endpoint ni campo con datos de negocio de las Empresas
 * (Clientes, Cotizaciones, etc.), coherente con que el {@code super_admin} no
 * accede a los datos de negocio.</p>
 */
@RestController
@RequestMapping("/empresas")
public class EmpresaController {

    private final ServicioEmpresas servicioEmpresas;
    private final ServicioSuscripciones servicioSuscripciones;

    public EmpresaController(ServicioEmpresas servicioEmpresas,
                             ServicioSuscripciones servicioSuscripciones) {
        this.servicioEmpresas = servicioEmpresas;
        this.servicioSuscripciones = servicioSuscripciones;
    }

    /**
     * Da de alta una Empresa (Req 24.2). Un RFC o un identificador de admin
     * duplicado produce 409; un Plan o Suscripcion inicial inexistente, 404.
     *
     * <p>El alta asocia el Contrato vigente a un Plan o Suscripcion inicial
     * <strong>excluyente</strong>: se indica exactamente uno de {@code planId} o
     * {@code paqueteSuscripcionId} (Req 4.1/4.2). El servicio responde 422 si se
     * indican ambos (Req 4.4) o ninguno (Req 4.3). Cuando el instrumento es un
     * Paquete de Suscripcion que admite prueba y {@code otorgarPrueba} es
     * verdadero, el Contrato nace en estado {@code EN_PRUEBA} (Req 4.5).</p>
     *
     * <p>La respuesta incluye, una unica vez, la contrasena temporal del primer
     * {@code admin_empresa} cuando el Sistema la genero (Req 11.3).</p>
     */
    @PostMapping
    @PreAuthorize("@autorizador.tiene('empresa','crear')")
    public ResponseEntity<EmpresaCreadaDto> crear(@Valid @RequestBody CrearEmpresaRequest request) {
        // El Giro obligatorio de la Empresa (Req 2.1) viaja en el cuerpo REST;
        // el servicio valida que exista y este activo (422 si no, Req 2.2) y lo
        // registra en la auditoria del alta (Req 2.5).
        EmpresaCreadaDto dto = servicioEmpresas.crearEmpresa(new CrearEmpresaCommand(
                request.nombre(),
                request.rfc(),
                request.giroId(),
                request.planId(),
                request.paqueteSuscripcionId(),
                request.otorgarPrueba(),
                request.adminIdentificador(),
                request.adminPassword(),
                request.modulosHabilitados(),
                // Datos descriptivos/de contacto opcionales (Req 24); el dominio
                // los normaliza/valida al asignarlos antes de persistir la Empresa.
                new DatosDescriptivosEmpresa(
                        request.nombreComercial(),
                        request.emailContacto(),
                        request.telefono(),
                        request.sitioWeb(),
                        request.direccionCalle(),
                        request.direccionCiudad(),
                        request.direccionEstado(),
                        request.direccionCp(),
                        request.direccionPais(),
                        request.notas(),
                        request.logo())));
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    /**
     * Edita los datos de plataforma de una Empresa existente por el
     * {@code super_admin} (CHANGE 1): nombre, identificador fiscal (RFC) y ficha
     * descriptiva/de contacto (nombre comercial, correo, telefono, sitio web,
     * direccion, notas y logo). El correo de contacto es obligatorio.
     *
     * <p>Se protege con el permiso de plataforma {@code empresa:actualizar}
     * (sembrado a {@code super_admin} en V5); ningun rol de empresa lo posee, por
     * lo que Spring Security responde 403 a cualquier otro usuario. Una Empresa
     * inexistente produce 404; un RFC de OTRA Empresa, 409; un correo o RFC
     * invalidos, 400/422 (la sintaxis la valida Bean Validation, la estructura del
     * RFC el servicio).</p>
     *
     * <p><strong>Fuera de alcance (por diseno):</strong> este endpoint NO reasigna
     * el {@code giro} (flujo dedicado de cambio de Giro, Req 3), ni el
     * Plan/Suscripcion ({@code PUT /empresas/{id}/modulos} y moneda de
     * facturacion), ni el {@code estado} ({@code POST /empresas/{id}/activar|
     * suspender}). Edita unicamente los datos descriptivos/fiscales.</p>
     *
     * @param id      identificador de la Empresa a editar.
     * @param request nuevos datos de la Empresa.
     * @return el DTO actualizado (mismo detalle que {@code GET /empresas/{id}}).
     */
    @PutMapping("/{id}")
    @PreAuthorize("@autorizador.tiene('empresa','actualizar')")
    public ResponseEntity<EmpresaDto> actualizar(
            @PathVariable("id") UUID id,
            @Valid @RequestBody ActualizarEmpresaRequest request) {
        EmpresaDto dto = servicioEmpresas.actualizarEmpresa(id, new ActualizarEmpresaCommand(
                request.nombre(),
                request.rfc(),
                new DatosDescriptivosEmpresa(
                        request.nombreComercial(),
                        request.emailContacto(),
                        request.telefono(),
                        request.sitioWeb(),
                        request.direccionCalle(),
                        request.direccionCiudad(),
                        request.direccionEstado(),
                        request.direccionCp(),
                        request.direccionPais(),
                        request.notas(),
                        request.logo())));
        return ResponseEntity.ok(dto);
    }

    /**
     * Consulta el detalle de la PROPIA Empresa del {@code admin_empresa}
     * autenticado (CHANGE 2), para precargar el formulario "Datos de mi empresa".
     * El tenant se deriva del contexto autenticado ({@link TenantContext}), nunca
     * de la ruta ni del cuerpo.
     *
     * <p>Se restringe al rol {@code admin_empresa} ({@code hasRole}); un
     * {@code super_admin} (sin tenant, sin ese rol) recibe 403. Es una operacion
     * de nivel empresa, por lo que NO usa los permisos de plataforma
     * {@code empresa:*}.</p>
     *
     * @return el DTO de la propia Empresa del Usuario.
     */
    @GetMapping("/mi-empresa")
    @PreAuthorize("hasRole('admin_empresa')")
    public ResponseEntity<EmpresaDto> consultarMiEmpresa() {
        return ResponseEntity.ok(servicioEmpresas.consultarMiEmpresa(TenantContext.require()));
    }

    /**
     * Edita el perfil de CONTACTO de la PROPIA Empresa del {@code admin_empresa}
     * autenticado (CHANGE 2): nombre, correo de contacto (obligatorio), telefono,
     * direccion y logo. El tenant se deriva del contexto autenticado
     * ({@link TenantContext}), nunca de la ruta ni del cuerpo, por lo que no puede
     * editar la Empresa de otro tenant.
     *
     * <p>Se restringe al rol {@code admin_empresa} ({@code hasRole}); un
     * {@code super_admin} recibe 403 (no tiene ese rol). <strong>El {@code rfc},
     * el {@code giro}, el Plan y el {@code estado} NO son editables por esta
     * via</strong> (no forman parte del contrato {@link ActualizarMiEmpresaRequest}).
     * Un correo invalido produce 400; si el tenant no tiene Empresa, 404.</p>
     *
     * @param request campos de contacto a aplicar (nombre y correo obligatorios).
     * @return el DTO actualizado de la propia Empresa.
     */
    @PutMapping("/mi-empresa")
    @PreAuthorize("hasRole('admin_empresa')")
    public ResponseEntity<EmpresaDto> actualizarMiEmpresa(
            @Valid @RequestBody ActualizarMiEmpresaRequest request) {
        EmpresaDto dto = servicioEmpresas.actualizarMiEmpresa(
                TenantContext.require(),
                new ActualizarMiEmpresaCommand(
                        request.nombre(),
                        request.emailContacto(),
                        request.telefono(),
                        request.direccionCalle(),
                        request.direccionCiudad(),
                        request.direccionEstado(),
                        request.direccionCp(),
                        request.direccionPais(),
                        request.logo()));
        return ResponseEntity.ok(dto);
    }

    /**
     * Edita el subconjunto de modulos habilitados de una Empresa sobre su
     * Suscripcion activa (Req 25.4). El cuerpo {@code { "modulos": ["a","b"] }}
     * fija ese subconjunto EXACTO (el arreglo vacio = cero modulos); un cuerpo con
     * {@code modulos} nulo/omitido revierte a heredar todos los modulos del Plan.
     *
     * <p>Se protege con el permiso de plataforma {@code empresa:cambiar_estado},
     * sembrado a {@code super_admin} en V5 (mismo permiso que gobierna los cambios
     * de estado de la Empresa); no se introduce ningun permiso nuevo sin sembrar.
     * Un subconjunto que no pertenece al Plan produce 422; una Empresa sin
     * Suscripcion activa, 404.</p>
     */
    @PutMapping("/{id}/modulos")
    @PreAuthorize("@autorizador.tiene('empresa','cambiar_estado')")
    public ResponseEntity<SuscripcionDto> actualizarModulos(
            @PathVariable("id") UUID id,
            @RequestBody ActualizarModulosEmpresaRequest request) {
        return ResponseEntity.ok(
                servicioSuscripciones.actualizarModulosEmpresa(id, request.modulos()));
    }

    /**
     * Fija la moneda de facturacion (ISO 4217) de la renta de modulos de una
     * Empresa sobre su Suscripcion activa (monetizacion, V23). Reutiliza el
     * permiso de plataforma {@code empresa:cambiar_estado} del {@code super_admin}.
     * Una Empresa sin Suscripcion activa produce 404; un codigo invalido, 422.
     */
    @PutMapping("/{id}/moneda-facturacion")
    @PreAuthorize("@autorizador.tiene('empresa','cambiar_estado')")
    public ResponseEntity<SuscripcionDto> fijarMonedaFacturacion(
            @PathVariable("id") UUID id,
            @jakarta.validation.Valid @RequestBody FijarMonedaFacturacionRequest request) {
        return ResponseEntity.ok(
                servicioSuscripciones.fijarMonedaFacturacion(id, request.monedaCodigo()));
    }

    /**
     * Reasigna el Giro de una Empresa (Req 3). El cuerpo {@code { "giroId": ... }}
     * indica el nuevo Giro a asignar.
     *
     * <p>Reutiliza el permiso de plataforma {@code empresa:cambiar_estado}
     * (sembrado a {@code super_admin} en V5, el mismo que gobierna activar/
     * suspender/modulos); <strong>no introduce ningun permiso nuevo ni migracion</strong>.
     * El endpoint se limita a delegar en {@code ServicioEmpresas.cambiarGiro}, que
     * impone TODAS las reglas: Giro activo (422 si es invalido/inactivo/inexistente),
     * rechazo si la Empresa tiene datos del vertical de su Giro actual (422), y
     * auditoria del giro anterior/nuevo. Una Empresa inexistente produce 404. El
     * controlador no reimplementa ninguna de estas reglas.</p>
     *
     * @param id      identificador de la Empresa cuyo Giro se reasigna.
     * @param request cuerpo con el identificador del nuevo Giro.
     * @return el DTO actualizado de la Empresa (ya incluye {@code giroId}).
     */
    @PutMapping("/{id}/giro")
    @PreAuthorize("@autorizador.tiene('empresa','cambiar_estado')")
    public ResponseEntity<EmpresaDto> cambiarGiro(
            @PathVariable("id") UUID id,
            @jakarta.validation.Valid @RequestBody CambiarGiroRequest request) {
        return ResponseEntity.ok(servicioEmpresas.cambiarGiro(id, request.giroId()));
    }

    /**
     * Activa una Empresa (Req 24.1). Una Empresa inexistente produce 404; una
     * Empresa cancelada, 422.
     */
    @PostMapping("/{id}/activar")
    @PreAuthorize("@autorizador.tiene('empresa','cambiar_estado')")
    public ResponseEntity<EmpresaDto> activar(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(servicioEmpresas.activarEmpresa(id));
    }

    /**
     * Suspende una Empresa (Req 24.4): impide el inicio de sesion de sus
     * Usuarios mientras dure. Una Empresa inexistente produce 404; una Empresa
     * cancelada, 422.
     */
    @PostMapping("/{id}/suspender")
    @PreAuthorize("@autorizador.tiene('empresa','cambiar_estado')")
    public ResponseEntity<EmpresaDto> suspender(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(servicioEmpresas.suspenderEmpresa(id));
    }

    /**
     * Restablece la contrasena del {@code admin_empresa} de una Empresa
     * (CHANGE 3). El cuerpo es <strong>opcional</strong>: si trae
     * {@code password} se fija esa contrasena (8..255; 422 si esta fuera de
     * rango); si se omite, el Sistema genera una temporal y la devuelve una unica
     * vez en {@code passwordTemporal} (Req 11.3). Un {@code usuarioId} opcional
     * desambigua cuando la Empresa tiene mas de un {@code admin_empresa}.
     *
     * <p>Se protege con el permiso de plataforma {@code empresa:cambiar_estado}
     * (sembrado a {@code super_admin} en V5, el mismo que gobierna el ciclo de
     * vida de la Empresa); no se introduce ningun permiso nuevo sin sembrar. Una
     * Empresa o un administrador inexistentes producen 404; una contrasena
     * explicita invalida, 422.</p>
     */
    @PostMapping("/{id}/admin/reset-password")
    @PreAuthorize("@autorizador.tiene('empresa','cambiar_estado')")
    public ResponseEntity<ResetPasswordAdminDto> resetPasswordAdmin(
            @PathVariable("id") UUID id,
            @jakarta.validation.Valid @RequestBody(required = false) ResetPasswordAdminRequest request) {
        String password = (request == null) ? null : request.password();
        UUID usuarioId = (request == null) ? null : request.usuarioId();
        return ResponseEntity.ok(
                servicioEmpresas.restablecerPasswordAdmin(id, password, usuarioId));
    }

    /**
     * Consulta una Empresa por su identificador (Req 24.1). Una Empresa
     * inexistente produce 404.
     *
     * <p>El {@link EmpresaDto} devuelto viene <strong>enriquecido</strong> con el
     * plan vigente de la Empresa ({@code planVigente}): nombre del plan, estado de
     * la suscripcion vigente y su vigencia. Es {@code null} cuando la Empresa no
     * tiene suscripcion (Req 4.7). El servicio resuelve la suscripcion vigente y el
     * nombre del plan como fuente de verdad.</p>
     */
    @GetMapping("/{id}")
    @PreAuthorize("@autorizador.tiene('empresa','leer')")
    public ResponseEntity<EmpresaDto> consultar(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(servicioEmpresas.consultarEmpresa(id));
    }

    /**
     * Lista Empresas de forma paginada (20 por defecto, 100 maximo) filtrable por
     * estado (Req 24.5). El {@code size} superior al maximo se acota conforme a la
     * politica de paginacion estandar.
     *
     * <p>Ademas admite una busqueda textual opcional {@code q} que filtra por
     * coincidencia (contiene, sin distinguir mayusculas/minusculas) en el nombre,
     * el RFC o el nombre comercial de la Empresa (Req 24). Si {@code q} viene
     * vacio/en blanco se comporta como el listado normal; puede combinarse con
     * {@code estado}.</p>
     *
     * <p>Cada {@link EmpresaDto} de la pagina viene <strong>enriquecido</strong>
     * con el plan vigente de la Empresa ({@code planVigente}): nombre del plan,
     * estado de la suscripcion vigente y su vigencia; es {@code null} cuando la
     * Empresa no tiene suscripcion (Req 4.6). El servicio resuelve la suscripcion
     * vigente y el nombre del plan por lote (fuente de verdad, sin N+1).</p>
     *
     * @param estado filtro opcional por estado (activa/suspendida/cancelada).
     * @param q      texto de busqueda opcional (nombre, RFC o nombre comercial).
     * @param page   numero de pagina 0-index; opcional.
     * @param size   tamano de pagina; opcional (por defecto 20, maximo 100).
     * @return la pagina de Empresas proyectada a {@link EmpresaDto} enriquecido.
     */
    @GetMapping
    @PreAuthorize("@autorizador.tiene('empresa','listar')")
    public PaginaResponse<EmpresaDto> listar(
            @RequestParam(name = "estado", required = false) EstadoEmpresa estado,
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size) {
        Pageable pageable = PageRequestFactory.acotando(page, size);
        return PaginaResponse.de(servicioEmpresas.listarEmpresasDto(estado, q, pageable));
    }
}
