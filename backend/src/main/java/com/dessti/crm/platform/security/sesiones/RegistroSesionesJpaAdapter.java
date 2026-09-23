package com.dessti.crm.platform.security.sesiones;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Adaptador de salida (persistencia) que implementa {@link RegistroSesionesPort}
 * sobre PostgreSQL mediante {@link SesionRefrescoRepository} (Req 68).
 *
 * <p>Es la unica implementacion del puerto en el arranque de la aplicacion, por
 * lo que Spring la inyecta sin ambiguedad en los casos de uso de autenticacion
 * y de gestion de usuarios. El reloj se inyecta para que la evaluacion de
 * "sesion activa" y el sellado del instante de revocacion sean deterministas y
 * consistentes con el resto de la plataforma (UTC).</p>
 */
@Repository
public class RegistroSesionesJpaAdapter implements RegistroSesionesPort {

    private final SesionRefrescoRepository repositorio;
    private final Clock clock;

    public RegistroSesionesJpaAdapter(SesionRefrescoRepository repositorio, Clock clock) {
        this.repositorio = repositorio;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void registrar(RegistroSesion sesion) {
        // Idempotencia por jti: no duplicar si ya existe (por ejemplo, reintentos).
        if (repositorio.existsByJti(sesion.jti())) {
            return;
        }
        repositorio.save(new SesionRefresco(sesion));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean estaRevocado(String jti) {
        if (jti == null || jti.isBlank()) {
            // Un refresco sin jti no puede validarse contra el registro: se
            // rechaza de forma conservadora (Req 68).
            return true;
        }
        // Un jti desconocido se trata como revocado (rechazo conservador): si la
        // sesion no fue registrada, se cierra el acceso ante la duda (Req 68).
        return repositorio.findByJti(jti)
                .map(SesionRefresco::isRevocado)
                .orElse(true);
    }

    @Override
    @Transactional
    public int revocar(String jti, MotivoRevocacion motivo) {
        if (jti == null || jti.isBlank()) {
            return 0;
        }
        return repositorio.findByJti(jti)
                .map(sesion -> {
                    boolean cambio = sesion.revocar(motivo, clock);
                    if (cambio) {
                        repositorio.save(sesion);
                        return 1;
                    }
                    return 0;
                })
                .orElse(0);
    }

    @Override
    @Transactional
    public int revocarTodasDeUsuario(UUID usuarioId, MotivoRevocacion motivo) {
        if (usuarioId == null) {
            return 0;
        }
        return repositorio.revocarActivasDeUsuario(usuarioId, motivo, clock.instant());
    }

    @Override
    @Transactional(readOnly = true)
    public Page<SesionActivaView> listarActivasDeUsuario(UUID usuarioId, Clock relojConsulta,
                                                         Pageable pageable) {
        Clock efectivo = (relojConsulta != null) ? relojConsulta : this.clock;
        Instant ahora = efectivo.instant();
        return repositorio.listarActivas(usuarioId, ahora, pageable)
                .map(SesionRefresco::aVista);
    }
}
