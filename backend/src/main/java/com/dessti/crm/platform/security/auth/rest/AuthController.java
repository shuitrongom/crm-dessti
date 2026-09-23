package com.dessti.crm.platform.security.auth.rest;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.dessti.crm.platform.security.auth.ServicioAutenticacion;
import com.dessti.crm.platform.security.ratelimit.ResolvedorIpCliente;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

/**
 * Adaptador de entrada REST para la autenticacion (Req 1, tarea 9.1).
 *
 * <p>Rutas (relativas al context-path {@code /api/v1}):</p>
 * <ul>
 *   <li>{@code POST /auth/login} — verifica credenciales y emite tokens.</li>
 *   <li>{@code POST /auth/refresh} — reemite el Token_Acceso desde un
 *       Token_Refresco vigente.</li>
 *   <li>{@code POST /auth/logout} — cierre de sesion: revoca el Token_Refresco
 *       presentado para que no emita nuevos Token_Acceso (Req 68.1).</li>
 * </ul>
 *
 * <p>Estas rutas son publicas (ver {@code SecurityConfig}); el resto de la API
 * requiere un Token_Acceso valido.</p>
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final ServicioAutenticacion servicioAutenticacion;

    public AuthController(ServicioAutenticacion servicioAutenticacion) {
        this.servicioAutenticacion = servicioAutenticacion;
    }

    /**
     * Inicia sesion con identificador y contrasena (Req 1.1). Credenciales
     * invalidas o cuenta bloqueada producen 401 con mensaje generico (Req 1.3,
     * 2.2). La direccion de origen se resuelve considerando el Proxy_Inverso
     * ({@code X-Forwarded-For}) y se registra en auditoria (Req 2.3, 2.5).
     */
    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request,
                                               HttpServletRequest httpRequest) {
        String ipOrigen = ResolvedorIpCliente.resolver(httpRequest);
        TokenResponse respuesta = servicioAutenticacion.login(
                request.identificador(), request.password(), ipOrigen);
        return ResponseEntity.ok(respuesta);
    }

    /**
     * Renueva el Token_Acceso a partir de un Token_Refresco vigente (Req 1.5).
     * Un refresco invalido/expirado produce 401 (Req 1.9).
     */
    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        TokenResponse respuesta = servicioAutenticacion.refresh(request.refreshToken());
        return ResponseEntity.ok(respuesta);
    }

    /**
     * Cierra la sesion (Req 68.1): revoca el Token_Refresco presentado en el
     * cuerpo, de modo que no pueda emitir nuevos Token_Acceso. El
     * Token_Acceso de vida corta (&le; 15 min) acota la ventana residual. Es
     * idempotente: un refresco invalido/expirado tambien responde 204. Se
     * limpia el contexto de seguridad del hilo por prolijidad.
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshRequest request) {
        servicioAutenticacion.logout(request.refreshToken());
        SecurityContextHolder.clearContext();
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}
