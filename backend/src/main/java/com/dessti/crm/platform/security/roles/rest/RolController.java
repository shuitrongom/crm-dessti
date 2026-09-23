package com.dessti.crm.platform.security.roles.rest;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.dessti.crm.platform.security.roles.RolAsignableDto;
import com.dessti.crm.platform.security.roles.ServicioRoles;

/**
 * Adaptador de entrada REST para la consulta de Roles asignables por el
 * {@code admin_empresa} de una Empresa (plataforma-multigiro).
 *
 * <p>Rutas (relativas al context-path {@code /api/v1}):</p>
 * <ul>
 *   <li>{@code GET /roles/asignables} — lista los roles predefinidos que la
 *       Empresa del Usuario autenticado puede asignar, filtrados por los modulos
 *       que contrato (Req 4, 27).</li>
 * </ul>
 *
 * <h2>Autorizacion</h2>
 * <p>Se protege con el permiso {@code rol:listar}, que el rol
 * {@code admin_empresa} posee (V5, seccion 3.2). Sin ese permiso, Spring
 * Security responde 403 (denegacion por defecto). El tenant se deriva del
 * contexto autenticado, nunca de la peticion (Req 23.4).</p>
 */
@RestController
@RequestMapping("/roles")
public class RolController {

    private final ServicioRoles servicioRoles;

    public RolController(ServicioRoles servicioRoles) {
        this.servicioRoles = servicioRoles;
    }

    /**
     * Lista los roles predefinidos asignables por la Empresa del contexto:
     * los transversales de administracion/direccion (siempre) y los de modulo
     * cuyo modulo requerido esta contratado. El {@code super_admin} nunca se
     * incluye. Orden: transversales primero, luego los de modulo, por nombre.
     *
     * @return los roles asignables ({@code id}, {@code nombre}, {@code modulo}
     *         representativo o {@code null}, y {@code descripcion}).
     */
    @GetMapping("/asignables")
    @PreAuthorize("@autorizador.tiene('rol','listar')")
    public List<RolAsignableDto> asignables() {
        return servicioRoles.listarRolesAsignables();
    }
}
