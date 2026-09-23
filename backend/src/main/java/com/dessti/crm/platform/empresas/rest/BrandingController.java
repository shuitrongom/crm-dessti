package com.dessti.crm.platform.empresas.rest;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.dessti.crm.platform.empresas.ActualizarBrandingCommand;
import com.dessti.crm.platform.empresas.BrandingDto;
import com.dessti.crm.platform.empresas.ServicioBranding;

import jakarta.validation.Valid;

/**
 * Adaptador de entrada REST para la personalizacion de marca (branding) de la
 * Empresa del Usuario autenticado (Req 26, tarea 14.3).
 *
 * <p>Rutas (relativas al context-path {@code /api/v1}):</p>
 * <ul>
 *   <li>{@code GET /empresa/branding} — consulta el nombre visible y el
 *       logotipo de la Empresa del Usuario, para que la interfaz aplique la
 *       marca (Req 26.2).</li>
 *   <li>{@code PUT /empresa/branding} — actualiza el nombre visible y el
 *       logotipo de la Empresa del Usuario (Req 26.1).</li>
 * </ul>
 *
 * <h2>Alcance de empresa (Req 26.1, 26.2, 23.4)</h2>
 * <p>Ambas operaciones actuan sobre la <strong>propia Empresa</strong> del
 * Usuario: el {@code tenant_id} lo deriva {@link ServicioBranding} del contexto
 * autenticado, nunca de la ruta ni del cuerpo. No existe forma de referenciar
 * ni exponer el branding de otra Empresa.</p>
 *
 * <h2>Autorizacion (Req 3, 27.10)</h2>
 * <p>Es una operacion de nivel <em>empresa</em> del rol {@code admin_empresa}
 * (gestion de la configuracion de su Empresa), no del {@code super_admin}. Por
 * ello se exigen permisos del recurso {@code branding} (no los permisos de
 * plataforma {@code empresa:*}): {@code branding:actualizar} para el PUT y
 * {@code branding:leer} para el GET, sembrados y asignados al rol predefinido
 * {@code admin_empresa} (V5 y V9). Sin el permiso, Spring Security responde 403
 * (denegacion por defecto, Req 3.2).</p>
 */
@RestController
@RequestMapping("/empresa/branding")
public class BrandingController {

    private final ServicioBranding servicioBranding;

    public BrandingController(ServicioBranding servicioBranding) {
        this.servicioBranding = servicioBranding;
    }

    /**
     * Consulta la personalizacion de marca de la Empresa del Usuario (Req 26.2).
     *
     * @return el branding vigente (nombre visible + logotipo); los campos pueden
     *         ser {@code null} si no se ha personalizado.
     */
    @GetMapping
    @PreAuthorize("@autorizador.tiene('branding','leer')")
    public ResponseEntity<BrandingDto> consultar() {
        return ResponseEntity.ok(servicioBranding.consultarBranding());
    }

    /**
     * Actualiza la personalizacion de marca de la Empresa del Usuario (Req 26.1).
     * Un nombre visible o logotipo demasiado largo produce 422.
     *
     * @param request nombre visible y logotipo a aplicar (ambos opcionales).
     * @return el branding resultante.
     */
    @PutMapping
    @PreAuthorize("@autorizador.tiene('branding','actualizar')")
    public ResponseEntity<BrandingDto> actualizar(@Valid @RequestBody ActualizarBrandingRequest request) {
        BrandingDto dto = servicioBranding.actualizarBranding(
                new ActualizarBrandingCommand(request.nombreVisible(), request.logo(), request.colorPrimario()));
        return ResponseEntity.ok(dto);
    }
}
