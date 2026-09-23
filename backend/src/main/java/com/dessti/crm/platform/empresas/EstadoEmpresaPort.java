package com.dessti.crm.platform.empresas;

import java.util.UUID;

/**
 * Puerto de solo lectura que expone el estado de suspension de una Empresa para
 * la ruta de autenticacion (Req 24.4).
 *
 * <p>Lo consume {@code ServicioAutenticacion} para <strong>impedir el inicio de
 * sesion</strong> de los Usuarios de una Empresa suspendida. Se define como un
 * puerto minimo (una sola consulta booleana) para no acoplar la autenticacion a
 * la entidad {@code Empresa} ni a su repositorio, y para mantener la
 * arquitectura hexagonal: la autenticacion depende de esta abstraccion, no de la
 * implementacion de persistencia.</p>
 *
 * <p><strong>super_admin (plataforma):</strong> los Usuarios de plataforma
 * tienen {@code tenant_id} nulo y no pertenecen a ninguna Empresa; el llamador
 * debe omitir esta comprobacion cuando {@code tenantId} es {@code null}.</p>
 */
public interface EstadoEmpresaPort {

    /**
     * Indica si la Empresa identificada esta suspendida (Req 24.4).
     *
     * @param tenantId identificador de la Empresa (su {@code id} es el
     *                 {@code tenant_id}); no {@code null}.
     * @return {@code true} si la Empresa existe y su estado es
     *         {@link EstadoEmpresa#SUSPENDIDA}; {@code false} si esta activa o no
     *         se encuentra (no bloquear por ausencia; el resto de reglas de login
     *         ya rechazan credenciales invalidas de forma generica).
     */
    boolean estaSuspendida(UUID tenantId);

    /**
     * Indica si el acceso de los Usuarios de la Empresa debe estar
     * <strong>bloqueado</strong> por su estado de ciclo de vida (Req 24.4,
     * 69.2): la Empresa esta {@link EstadoEmpresa#SUSPENDIDA} o
     * {@link EstadoEmpresa#CANCELADA}.
     *
     * <p>Es la comprobacion que {@code ServicioAutenticacion} usa para impedir
     * el inicio de sesion: mientras dure la suspension (Req 24.4) y tambien
     * durante el Periodo_Gracia de una Empresa cancelada (Req 69.2), en el que
     * los datos se conservan pero el acceso queda restringido conforme al estado
     * de la Empresa. Solo una Empresa {@code activa} permite el login.</p>
     *
     * @param tenantId identificador de la Empresa (su {@code id} es el
     *                 {@code tenant_id}); {@code null} para Usuarios de
     *                 plataforma (super_admin), que nunca se bloquean por este
     *                 medio.
     * @return {@code true} si la Empresa existe y su estado bloquea el acceso
     *         (suspendida o cancelada); {@code false} si esta activa, no se
     *         encuentra o {@code tenantId} es {@code null}.
     */
    boolean accesoBloqueado(UUID tenantId);
}
