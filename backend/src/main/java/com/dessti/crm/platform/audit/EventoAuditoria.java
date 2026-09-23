package com.dessti.crm.platform.audit;

import java.util.Optional;
import java.util.UUID;

/**
 * Comando de dominio que describe un evento a registrar en la bitacora de
 * auditoria (Req 10). Es un objeto de entrada inmutable, libre de dependencias
 * de framework, que el llamador construye para solicitar el registro de una
 * accion relevante (seguridad o negocio).
 *
 * <p><strong>Sin secretos (Req 10.10):</strong> ni {@link #detalle},
 * {@link #valorAnterior} ni {@link #valorNuevo} deben contener contrasenas,
 * claves, tokens ni credenciales. El filtrado de datos sensibles es
 * responsabilidad del <em>llamador</em> que construye este evento; el servicio
 * de auditoria no inspecciona ni depura el contenido. El {@code valorAnterior}
 * y {@code valorNuevo} se esperan como cadenas JSON (o {@code null}).</p>
 *
 * <p>El {@code tenantId} es opcional: {@link Optional#empty()} representa el
 * ambito de <em>plataforma</em> (super_admin, Req 24.3), que no pertenece a
 * ninguna empresa.</p>
 *
 * @param tenantId       empresa a la que pertenece el evento; vacio para el
 *                       ambito de plataforma.
 * @param actor          identificador de quien ejecuta la accion (usuario o
 *                       proceso del sistema); obligatorio.
 * @param accion         verbo o tipo de accion (por ejemplo {@code login},
 *                       {@code crear}, {@code timbrar}); obligatorio.
 * @param recurso        tipo de recurso afectado (por ejemplo {@code cliente},
 *                       {@code factura}); obligatorio.
 * @param detalle        descripcion adicional legible, sin secretos; opcional.
 * @param valorAnterior  estado previo del recurso como JSON, sin secretos;
 *                       opcional.
 * @param valorNuevo     estado nuevo del recurso como JSON, sin secretos;
 *                       opcional.
 */
public record EventoAuditoria(
        Optional<UUID> tenantId,
        String actor,
        String accion,
        String recurso,
        String detalle,
        String valorAnterior,
        String valorNuevo) {

    /**
     * Valida las invariantes minimas del evento.
     *
     * @throws IllegalArgumentException si {@code actor}, {@code accion} o
     *         {@code recurso} son nulos o vacios.
     */
    public EventoAuditoria {
        tenantId = (tenantId == null) ? Optional.empty() : tenantId;
        requerido(actor, "actor");
        requerido(accion, "accion");
        requerido(recurso, "recurso");
    }

    private static void requerido(String valor, String campo) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException("El campo de auditoria '" + campo + "' es obligatorio");
        }
    }

    /**
     * Crea un evento de ambito de plataforma (sin tenant).
     *
     * @param actor          actor de la accion.
     * @param accion         accion ejecutada.
     * @param recurso        recurso afectado.
     * @param detalle        detalle legible (sin secretos), puede ser {@code null}.
     * @param valorAnterior  JSON del estado previo (sin secretos), puede ser {@code null}.
     * @param valorNuevo     JSON del estado nuevo (sin secretos), puede ser {@code null}.
     * @return el evento de plataforma.
     */
    public static EventoAuditoria dePlataforma(
            String actor, String accion, String recurso,
            String detalle, String valorAnterior, String valorNuevo) {
        return new EventoAuditoria(
                Optional.empty(), actor, accion, recurso, detalle, valorAnterior, valorNuevo);
    }

    /**
     * Crea un evento de ambito de empresa (tenant).
     *
     * @param tenantId       empresa a la que pertenece el evento.
     * @param actor          actor de la accion.
     * @param accion         accion ejecutada.
     * @param recurso        recurso afectado.
     * @param detalle        detalle legible (sin secretos), puede ser {@code null}.
     * @param valorAnterior  JSON del estado previo (sin secretos), puede ser {@code null}.
     * @param valorNuevo     JSON del estado nuevo (sin secretos), puede ser {@code null}.
     * @return el evento de empresa.
     */
    public static EventoAuditoria deTenant(
            UUID tenantId, String actor, String accion, String recurso,
            String detalle, String valorAnterior, String valorNuevo) {
        return new EventoAuditoria(
                Optional.of(tenantId), actor, accion, recurso, detalle, valorAnterior, valorNuevo);
    }
}
