package com.dessti.crm.platform.empresas.offboarding.rest;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.dessti.crm.platform.empresas.EmpresaDto;
import com.dessti.crm.platform.empresas.offboarding.ExportacionTenantDto;
import com.dessti.crm.platform.empresas.offboarding.ResultadoEliminacionTenantDto;
import com.dessti.crm.platform.empresas.offboarding.ServicioOffboarding;

/**
 * Adaptador de entrada REST del offboarding y la portabilidad del tenant
 * (Req 69, tarea 14.4).
 *
 * <p>Rutas (relativas al context-path {@code /api/v1}):</p>
 * <ul>
 *   <li>{@code POST /empresas/{id}/offboarding/exportar} — exporta los datos de
 *       negocio de la Empresa en formato estructurado (JSON), limitados a su
 *       {@code tenant_id} (Req 69.1).</li>
 *   <li>{@code POST /empresas/{id}/offboarding/cancelar} — cancela la Empresa e
 *       inicia su Periodo_Gracia configurable (Req 69.2).</li>
 *   <li>{@code POST /empresas/{id}/offboarding/eliminar} — elimina/anonimiza los
 *       datos de negocio tras el Periodo_Gracia, preservando comprobantes
 *       fiscales (Req 69.3, 69.4).</li>
 * </ul>
 *
 * <h2>Autorizacion (Req 69.1, 69.3)</h2>
 * <ul>
 *   <li><strong>exportar:</strong> {@code super_admin} (permiso de plataforma
 *       {@code offboarding:exportar}, V5) <em>o</em> el {@code admin_empresa} de
 *       la propia Empresa (permiso de nivel empresa
 *       {@code offboarding_propio:exportar}, V10). El servicio exige que el
 *       {@code admin_empresa} solo exporte SU tenant (si no, 404, Req 23.3).</li>
 *   <li><strong>cancelar / eliminar:</strong> exclusivo del {@code super_admin}
 *       (permiso de plataforma {@code offboarding:cambiar_estado}, V5). Ningun
 *       rol de empresa lo posee: 403 por denegacion por defecto (Req 3.2).</li>
 * </ul>
 *
 * <p>Nunca se exponen secretos en la exportacion (Req 10.10, 11.3).</p>
 */
@RestController
@RequestMapping("/empresas/{id}/offboarding")
public class OffboardingController {

    private final ServicioOffboarding servicioOffboarding;

    public OffboardingController(ServicioOffboarding servicioOffboarding) {
        this.servicioOffboarding = servicioOffboarding;
    }

    /**
     * Exporta los datos de negocio de la Empresa, limitados a su {@code tenant_id}
     * (Req 69.1). Devuelve una estructura JSON procesable. El {@code admin_empresa}
     * solo puede exportar su propia Empresa (de lo contrario 404).
     */
    @PostMapping("/exportar")
    @PreAuthorize("@autorizador.tiene('offboarding','exportar') "
            + "or @autorizador.tiene('offboarding_propio','exportar')")
    public ResponseEntity<ExportacionTenantDto> exportar(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(servicioOffboarding.exportar(id));
    }

    /**
     * Cancela la Empresa e inicia su Periodo_Gracia configurable (Req 69.2). Una
     * Empresa inexistente produce 404. Operacion exclusiva del {@code super_admin}.
     */
    @PostMapping("/cancelar")
    @PreAuthorize("@autorizador.tiene('offboarding','cambiar_estado')")
    public ResponseEntity<EmpresaDto> cancelar(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(EmpresaDto.de(servicioOffboarding.cancelarEIniciarGracia(id)));
    }

    /**
     * Ejecuta la eliminacion definitiva de los datos de negocio tras el
     * Periodo_Gracia (Req 69.3), preservando comprobantes fiscales (Req 69.4).
     * Una Empresa inexistente produce 404; una eliminacion antes de tiempo (no
     * cancelada o gracia no expirada) produce 422. Exclusiva del {@code super_admin}.
     */
    @PostMapping("/eliminar")
    @PreAuthorize("@autorizador.tiene('offboarding','cambiar_estado')")
    public ResponseEntity<ResultadoEliminacionTenantDto> eliminar(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(servicioOffboarding.eliminarDefinitivamente(id));
    }
}
