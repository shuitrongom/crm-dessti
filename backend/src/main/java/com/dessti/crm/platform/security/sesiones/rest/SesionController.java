package com.dessti.crm.platform.security.sesiones.rest;

import java.time.Clock;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.dessti.crm.platform.security.sesiones.RegistroSesionesPort;
import com.dessti.crm.platform.security.usuarios.ServicioUsuarios;
import com.dessti.crm.platform.web.pagination.PageRequestFactory;
import com.dessti.crm.platform.web.pagination.PaginaResponse;

/**
 * Adaptador de entrada REST para la gestion administrativa de Sesiones
 * (Token_Refresco) de una cuenta (Req 68.2, 68.5, tarea 11.2).
 *
 * <p>Rutas (relativas al context-path {@code /api/v1}):</p>
 * <ul>
 *   <li>{@code GET  /usuarios/{id}/sesiones} — lista paginada de sesiones
 *       activas de la cuenta (Req 68.5).</li>
 *   <li>{@code POST /usuarios/{id}/sesiones/revocar} — revoca todas las
 *       sesiones vigentes de la cuenta a peticion del Administrador (Req 68.2).</li>
 * </ul>
 *
 * <p><strong>Autorizacion (Req 3, 68):</strong> la consulta exige el permiso
 * {@code ('sesion','listar')} y la revocacion {@code ('sesion','cambiar_estado')},
 * evaluados dentro de la Empresa del Usuario. Estos permisos ya estan sembrados
 * (migracion V5) y asignados al rol {@code admin_empresa}. Sin el permiso, Spring
 * Security responde 403 (denegacion por defecto, Req 3.2).</p>
 */
@RestController
@RequestMapping("/usuarios/{id}/sesiones")
public class SesionController {

    private final RegistroSesionesPort registroSesiones;
    private final ServicioUsuarios servicioUsuarios;
    private final Clock clock;

    public SesionController(RegistroSesionesPort registroSesiones,
                            ServicioUsuarios servicioUsuarios,
                            Clock clock) {
        this.registroSesiones = registroSesiones;
        this.servicioUsuarios = servicioUsuarios;
        this.clock = clock;
    }

    /**
     * Lista de forma paginada las Sesiones activas de una cuenta (Req 68.5), con
     * tamano de pagina por defecto 20 y maximo 100.
     */
    @GetMapping
    @PreAuthorize("@autorizador.tiene('sesion','listar')")
    public ResponseEntity<PaginaResponse<SesionActivaResponse>> listar(
            @PathVariable("id") UUID usuarioId,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size) {
        Pageable pageable = PageRequestFactory.of(page, size);
        PaginaResponse<SesionActivaResponse> respuesta = PaginaResponse.de(
                registroSesiones.listarActivasDeUsuario(usuarioId, clock, pageable),
                SesionActivaResponse::de);
        return ResponseEntity.ok(respuesta);
    }

    /**
     * Revoca todas las Sesiones vigentes de una cuenta a peticion del
     * Administrador (Req 68.2). Una cuenta de otra Empresa produce 404 (Req 23.3).
     */
    @PostMapping("/revocar")
    @PreAuthorize("@autorizador.tiene('sesion','cambiar_estado')")
    public ResponseEntity<Void> revocar(@PathVariable("id") UUID usuarioId) {
        servicioUsuarios.revocarSesiones(usuarioId);
        return ResponseEntity.noContent().build();
    }
}
