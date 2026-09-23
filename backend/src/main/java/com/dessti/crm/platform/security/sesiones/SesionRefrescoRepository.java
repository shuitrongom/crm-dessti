package com.dessti.crm.platform.security.sesiones;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Repositorio Spring Data JPA de {@link SesionRefresco} (Req 68). Provee las
 * consultas y actualizaciones que necesita el adaptador del puerto de sesiones:
 * localizar por {@code jti}, revocar en bloque por usuario y listar las
 * sesiones activas de forma paginada.
 */
public interface SesionRefrescoRepository extends JpaRepository<SesionRefresco, UUID> {

    /** Localiza una sesion por su {@code jti} (unico). */
    Optional<SesionRefresco> findByJti(String jti);

    /** @return {@code true} si existe una sesion registrada con ese {@code jti}. */
    boolean existsByJti(String jti);

    /**
     * Revoca en bloque todas las sesiones <b>no revocadas</b> de una cuenta
     * (Req 68.2, 68.4). Se ejecuta como una unica sentencia {@code UPDATE} para
     * eficiencia; no incrementa {@code @Version} de filas ya revocadas al
     * filtrarlas en el {@code WHERE}.
     *
     * @param usuarioId  cuenta cuyas sesiones se revocan.
     * @param motivo     motivo de la revocacion (nombre del enum).
     * @param revocadoEn instante de revocacion (UTC).
     * @return el numero de filas revocadas.
     */
    @Modifying
    @Query("""
            UPDATE SesionRefresco s
               SET s.revocado = true,
                   s.motivoRevocacion = :motivo,
                   s.revocadoEn = :revocadoEn,
                   s.updatedAt = :revocadoEn
             WHERE s.usuarioId = :usuarioId
               AND s.revocado = false
            """)
    int revocarActivasDeUsuario(@Param("usuarioId") UUID usuarioId,
                                @Param("motivo") MotivoRevocacion motivo,
                                @Param("revocadoEn") Instant revocadoEn);

    /**
     * Lista de forma paginada las sesiones <b>activas</b> (no revocadas y con
     * expiracion posterior a {@code ahora}) de una cuenta (Req 68.5), ordenadas
     * de forma estable por instante de emision descendente.
     *
     * @param usuarioId cuenta cuyas sesiones se consultan.
     * @param ahora     instante de referencia (UTC) para descartar expiradas.
     * @param pageable  parametros de paginacion.
     * @return la pagina de sesiones activas.
     */
    @Query("""
            SELECT s
              FROM SesionRefresco s
             WHERE s.usuarioId = :usuarioId
               AND s.revocado = false
               AND s.expiraEn > :ahora
             ORDER BY s.emitidoEn DESC
            """)
    Page<SesionRefresco> listarActivas(@Param("usuarioId") UUID usuarioId,
                                       @Param("ahora") Instant ahora,
                                       Pageable pageable);
}
