package com.dessti.crm.platform.empresas;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.dessti.crm.platform.security.usuarios.LimiteUsuariosPort;
import com.dessti.crm.platform.security.usuarios.UsuarioRepository;

/**
 * Adaptador REAL de {@link LimiteUsuariosPort} respaldado por la Suscripcion
 * vigente de la Empresa y el {@code max_usuarios} de su Plan (Req 25.3).
 *
 * <p>Vive en el modulo de plataforma (empresas) porque conoce Suscripciones y
 * Planes; {@code ServicioUsuarios} (seguridad) solo depende del puerto, no de
 * esta implementacion, preservando la frontera hexagonal.</p>
 *
 * <h2>Regla del limite (Req 25.3)</h2>
 * <p>Una Empresa puede crear una cuenta adicional solo si el numero de cuentas
 * activas actuales es <strong>estrictamente menor</strong> que el
 * {@code max_usuarios} del Plan de su Suscripcion activa. Es decir, tras crear la
 * cuenta el conteo no debe superar el limite. Un {@code max_usuarios = 0}
 * bloquea toda creacion. Solo cuentan las cuentas {@code activo = true}: una
 * cuenta desactivada libera cupo.</p>
 *
 * <h2>Denegacion por defecto</h2>
 * <p>Si la Empresa no tiene una Suscripcion activa o su Plan no existe, no hay
 * limite conocido bajo el cual autorizar la creacion, por lo que se deniega
 * (devuelve {@code false}). El servicio traduce la denegacion a un rechazo con
 * mensaje de limite alcanzado (Req 25.3).</p>
 */
@Component
public class LimiteUsuariosPlanAdapter implements LimiteUsuariosPort {

    private final SuscripcionRepository suscripcionRepository;
    private final PlanRepository planRepository;
    private final UsuarioRepository usuarioRepository;

    public LimiteUsuariosPlanAdapter(SuscripcionRepository suscripcionRepository,
                                     PlanRepository planRepository,
                                     UsuarioRepository usuarioRepository) {
        this.suscripcionRepository = suscripcionRepository;
        this.planRepository = planRepository;
        this.usuarioRepository = usuarioRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean puedeCrearUsuario(UUID tenantId) {
        if (tenantId == null) {
            return false;
        }
        Optional<Suscripcion> activa = suscripcionRepository
                .findFirstByTenantIdAndEstadoOrderByIdAsc(tenantId, EstadoSuscripcion.ACTIVA);
        if (activa.isEmpty()) {
            return false;
        }
        Optional<Plan> plan = planRepository.findById(activa.get().getPlanId());
        if (plan.isEmpty()) {
            return false;
        }
        long activos = usuarioRepository.countByTenantIdAndActivoTrue(tenantId);
        return activos < plan.get().getMaxUsuarios();
    }
}
