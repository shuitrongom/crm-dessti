package com.dessti.crm.platform.security.perfil.rest;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.dessti.crm.platform.security.perfil.PerfilDto;
import com.dessti.crm.platform.security.perfil.ServicioPerfil;

import jakarta.validation.Valid;

/**
 * Adaptador de entrada REST para el <strong>perfil propio</strong> del Usuario
 * autenticado (CHANGE 2).
 *
 * <p>Rutas (relativas al context-path {@code /api/v1}):</p>
 * <ul>
 *   <li>{@code GET /auth/perfil} — devuelve los datos de cuenta del propio
 *       Usuario (id, identificador legible, roles y tenant).</li>
 *   <li>{@code PUT /auth/perfil/password} — cambia la contrasena propia previa
 *       verificacion de la contrasena actual (204 al exito).</li>
 * </ul>
 *
 * <h2>Autorizacion</h2>
 * <p>Ambas operaciones exigen unicamente estar <em>autenticado</em>
 * ({@code @PreAuthorize("isAuthenticated()")}); no requieren ningun permiso
 * atomico especial, porque cada Usuario opera sobre su <strong>propia</strong>
 * cuenta. Funciona igual para el {@code super_admin} (tenant NULL) que para un
 * {@code admin_empresa}. La cuenta se resuelve del principal del contexto de
 * seguridad, nunca de la peticion.</p>
 */
@RestController
@RequestMapping("/auth/perfil")
public class PerfilController {

    private final ServicioPerfil servicioPerfil;

    public PerfilController(ServicioPerfil servicioPerfil) {
        this.servicioPerfil = servicioPerfil;
    }

    /**
     * Devuelve el perfil del Usuario autenticado (CHANGE 2). Solo lectura.
     */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<PerfilDto> consultar() {
        return ResponseEntity.ok(servicioPerfil.consultarPerfil());
    }

    /**
     * Cambia la contrasena propia (CHANGE 2). Verifica la contrasena actual; si
     * es incorrecta responde 422; si la nueva no cumple la longitud, 400. Al
     * exito responde 204 sin cuerpo y NO cierra la sesion en curso.
     */
    @PutMapping("/password")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> cambiarPassword(@Valid @RequestBody CambiarPasswordRequest request) {
        servicioPerfil.cambiarPasswordPropia(request.passwordActual(), request.passwordNueva());
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}
