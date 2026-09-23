package com.dessti.crm.platform.empresas.rest;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.dessti.crm.platform.error.NoAutorizadoException;
import com.dessti.crm.platform.security.rbac.ModulosHabilitadosPort;
import com.dessti.crm.platform.tenant.TenantContext;

/**
 * Adaptador de entrada REST que expone, para el Usuario de empresa autenticado,
 * los modulos ACTUALMENTE contratados por su Empresa (Req 25.4).
 *
 * <p>Ruta (relativa al context-path {@code /api/v1}):</p>
 * <ul>
 *   <li>{@code GET /empresa/modulos} — lista viva de las claves de modulo
 *       efectivamente habilitadas para la Empresa (tenant) del solicitante.</li>
 * </ul>
 *
 * <h2>Motivacion: menu vivo sin re-login</h2>
 * <p>El menu del frontend se pinta con los modulos contratados. En el arranque de
 * sesion esa lista viaja en el claim {@code modulos} del JWT, pero el token es
 * inmutable: si el {@code super_admin} cambia el Plan de la Empresa (p. ej. agrega
 * {@code comercial}), un Usuario ya logueado conserva su claim ANTIGUO hasta que
 * el token expire o vuelva a iniciar sesion, y el bloque nuevo no aparece. Este
 * endpoint devuelve el estado ACTUAL para que el frontend repinte el menu al
 * vuelo, sin exigir re-login.</p>
 *
 * <h2>Fuente unica de verdad (single-sourced)</h2>
 * <p>Reutiliza el MISMO puerto que alimenta el claim del JWT,
 * {@link ModulosHabilitadosPort#modulosHabilitadosDe(UUID)} (implementado por
 * {@code PlanModulosPlanAdapter}: Suscripcion activa &rarr; override de Empresa si
 * existe, en su defecto catalogo del Plan). Asi el endpoint y el claim NUNCA
 * divergen. El metodo del puerto es {@code @Transactional(readOnly)} y fija el
 * tenant destino ({@code SET LOCAL}) dentro de esa transaccion, de modo que la RLS
 * de {@code suscripcion} se aplica correctamente al invocarlo desde aqui.</p>
 *
 * <h2>Autorizacion (decision de diseno)</h2>
 * <p>El menu lo necesita <strong>todo</strong> Usuario de empresa (no solo el
 * {@code admin_empresa}: tambien un usuario de "ventas", etc.), y es meta-informacion
 * de lo contratado, no una funcion de negocio de ningun modulo. Por eso NO se
 * restringe con un permiso de modulo ni con {@code hasRole('admin_empresa')} —que
 * dejaria fuera a los demas roles de empresa—, sino con
 * {@code @PreAuthorize("isAuthenticated()")}. El tenant SIEMPRE se deriva del
 * contexto autenticado ({@link TenantContext}), nunca de la peticion (Req 23.4).</p>
 *
 * <p>Un {@code super_admin} (Usuario de PLATAFORMA, sin tenant) no se rige por el
 * gating de modulos: aunque supera {@code isAuthenticated()}, no hay tenant en
 * contexto y la operacion no le aplica, por lo que se responde 403
 * ({@link NoAutorizadoException}).</p>
 */
@RestController
@RequestMapping("/empresa")
public class ModulosEmpresaController {

    private final ModulosHabilitadosPort modulosHabilitados;

    public ModulosEmpresaController(ModulosHabilitadosPort modulosHabilitados) {
        this.modulosHabilitados = modulosHabilitados;
    }

    /**
     * Devuelve las claves de los modulos ACTUALMENTE contratados por la Empresa
     * del Usuario autenticado, resueltas en el momento de la consulta por la misma
     * fuente que el claim {@code modulos} del JWT.
     *
     * @return {@code { "modulos": ["..."] }}; lista vacia si la Empresa no tiene
     *         modulos habilitados.
     * @throws NoAutorizadoException si el solicitante no tiene una Empresa en
     *         contexto (p. ej. un {@code super_admin} de plataforma) &rarr; 403.
     */
    @GetMapping("/modulos")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ModulosEmpresaDto> consultarModulos() {
        // El tenant SIEMPRE proviene del contexto autenticado (Req 23.4). Un
        // Usuario de plataforma (super_admin) no tiene tenant: el gating de
        // modulos no le aplica, por lo que se le niega el acceso (403).
        UUID tenantId = TenantContext.getCurrent().orElseThrow(() -> new NoAutorizadoException(
                "Esta operacion requiere una Empresa; no aplica a Usuarios de plataforma."));
        return ResponseEntity.ok(new ModulosEmpresaDto(
                modulosHabilitados.modulosHabilitadosDe(tenantId)));
    }
}
