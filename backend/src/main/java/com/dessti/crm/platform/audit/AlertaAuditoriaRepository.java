package com.dessti.crm.platform.audit;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Repositorio de las reglas de alerta de auditoria (Alerta_Auditoria, Req 10.11).
 * Adaptador de salida del {@link ServicioAlertasAuditoria}.
 */
public interface AlertaAuditoriaRepository extends JpaRepository<AlertaAuditoria, UUID> {

    /**
     * Devuelve las reglas <strong>activas</strong> aplicables a un patron para
     * el tenant indicado (o para el ambito de plataforma cuando
     * {@code tenantId} es {@code null}), usadas al evaluar un evento recien
     * registrado.
     *
     * @param tenantId empresa del evento; {@code null} para plataforma.
     * @param patron   patron a evaluar.
     * @return reglas activas que aplican al evento.
     */
    @Query("""
            select a from AlertaAuditoria a
            where a.activa = true
              and a.patron = :patron
              and ((:tenantId is null and a.tenantId is null) or a.tenantId = :tenantId)
            """)
    List<AlertaAuditoria> buscarActivasPorPatron(
            @Param("tenantId") UUID tenantId,
            @Param("patron") PatronAlerta patron);
}
