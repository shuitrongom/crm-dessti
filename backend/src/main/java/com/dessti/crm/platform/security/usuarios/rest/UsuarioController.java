package com.dessti.crm.platform.security.usuarios.rest;

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

import com.dessti.crm.platform.security.usuarios.CrearUsuarioCommand;
import com.dessti.crm.platform.security.usuarios.ServicioUsuarios;
import com.dessti.crm.platform.security.usuarios.UsuarioDto;
import com.dessti.crm.platform.web.pagination.PageRequestFactory;
import com.dessti.crm.platform.web.pagination.PaginaResponse;

import jakarta.validation.Valid;

/**
 * Adaptador de entrada REST para la gestion de Usuarios y sus Roles (Req 4,
 * tarea 11.1).
 *
 * <p>Rutas (relativas al context-path {@code /api/v1}):</p>
 * <ul>
 *   <li>{@code POST /usuarios} — crea una cuenta de Usuario (Req 4.1).</li>
 *   <li>{@code GET /usuarios} — lista paginada de las cuentas de la Empresa,
 *       con busqueda opcional {@code q} (Req 4).</li>
 *   <li>{@code GET /usuarios/{id}} — consulta una cuenta de la Empresa (Req 4).</li>
 *   <li>{@code PUT /usuarios/{id}} — actualiza datos descriptivos de una cuenta
 *       (nombre para mostrar) (Req 4).</li>
 *   <li>{@code POST /usuarios/{id}/desactivar} — desactiva una cuenta (Req 4.2).</li>
 *   <li>{@code PUT /usuarios/{id}/roles} — reemplaza los Roles de una cuenta
 *       (Req 4.3).</li>
 * </ul>
 *
 * <p><strong>Autorizacion (Req 3, 4):</strong> cada operacion exige el permiso
 * atomico correspondiente sobre el recurso {@code usuario}, evaluado dentro de
 * la Empresa del Usuario ({@code @autorizador.tiene(...)}). Es la capacidad que
 * el rol {@code admin_empresa} posee dentro de su Empresa (Req 3.4, 27.10). Sin
 * el permiso, Spring Security responde 403 (denegacion por defecto, Req 3.2).</p>
 */
@RestController
@RequestMapping("/usuarios")
public class UsuarioController {

    private final ServicioUsuarios servicioUsuarios;

    public UsuarioController(ServicioUsuarios servicioUsuarios) {
        this.servicioUsuarios = servicioUsuarios;
    }

    /**
     * Crea una cuenta de Usuario (Req 4.1). Un identificador de acceso duplicado
     * produce 409 (Req 4.4); un Rol de nivel plataforma, 422 (Req 27.7).
     */
    @PostMapping
    @PreAuthorize("@autorizador.tiene('usuario','crear')")
    public ResponseEntity<UsuarioDto> crear(@Valid @RequestBody CrearUsuarioRequest request) {
        UsuarioDto dto = servicioUsuarios.crearUsuario(
                new CrearUsuarioCommand(request.identificadorAcceso(), request.password(),
                        request.nombreVisible(), request.rolIds()));
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    /**
     * Lista de forma paginada (20 por defecto, 100 maximo) las cuentas de la
     * Empresa del Usuario autenticado (Req 4). Admite una busqueda textual
     * opcional {@code q} que filtra por coincidencia (contiene, sin distinguir
     * mayusculas/minusculas) en el identificador de acceso o el nombre visible;
     * un {@code q} vacio/en blanco lista todas las cuentas del tenant. El tenant
     * se deriva del contexto autenticado (Req 23.4), nunca de la peticion.
     *
     * @param q    texto de busqueda opcional (identificador o nombre visible).
     * @param page numero de pagina 0-index; opcional.
     * @param size tamano de pagina; opcional (por defecto 20, maximo 100).
     * @return la pagina de cuentas de la Empresa proyectada a {@link UsuarioDto}.
     */
    @GetMapping
    @PreAuthorize("@autorizador.tiene('usuario','listar')")
    public PaginaResponse<UsuarioDto> listar(
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size) {
        Pageable pageable = PageRequestFactory.acotando(page, size);
        return PaginaResponse.de(servicioUsuarios.listarUsuarios(q, pageable));
    }

    /**
     * Consulta una cuenta de la Empresa por su identificador (Req 4), util para
     * precargar la edicion. Una cuenta de otra Empresa produce 404 (Req 23.3).
     */
    @GetMapping("/{id}")
    @PreAuthorize("@autorizador.tiene('usuario','leer')")
    public ResponseEntity<UsuarioDto> consultar(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(servicioUsuarios.consultarUsuario(id));
    }

    /**
     * Actualiza los datos descriptivos de una cuenta de la Empresa (Req 4): por
     * ahora, unicamente el nombre PARA MOSTRAR. Un {@code null}/blanco lo limpia;
     * un nombre demasiado largo produce 422. Una cuenta de otra Empresa produce
     * 404 (Req 23.3).
     */
    @PutMapping("/{id}")
    @PreAuthorize("@autorizador.tiene('usuario','actualizar')")
    public ResponseEntity<UsuarioDto> actualizar(@PathVariable("id") UUID id,
                                                 @Valid @RequestBody ActualizarUsuarioRequest request) {
        return ResponseEntity.ok(
                servicioUsuarios.actualizarNombreVisible(id, request.nombreVisible()));
    }

    /**
     * Desactiva una cuenta de Usuario para impedir su inicio de sesion (Req 4.2).
     * Una cuenta de otra Empresa produce 404 (Req 23.3).
     */
    @PostMapping("/{id}/desactivar")
    @PreAuthorize("@autorizador.tiene('usuario','actualizar')")
    public ResponseEntity<UsuarioDto> desactivar(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(servicioUsuarios.desactivarUsuario(id));
    }

    /**
     * Reemplaza los Roles de una cuenta (Req 4.3). Los Permisos del nuevo
     * conjunto aplican en la siguiente evaluacion de autorizacion.
     */
    @PutMapping("/{id}/roles")
    @PreAuthorize("@autorizador.tiene('usuario','actualizar')")
    public ResponseEntity<UsuarioDto> asignarRoles(@PathVariable("id") UUID id,
                                                   @Valid @RequestBody AsignarRolesRequest request) {
        return ResponseEntity.ok(servicioUsuarios.asignarRoles(id, request.rolIds()));
    }
}
