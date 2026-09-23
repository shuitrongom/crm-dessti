package com.dessti.crm.platform.tenant;

import java.util.Optional;
import java.util.UUID;

/**
 * Almacen del identificador de empresa ({@code tenant_id}) asociado al hilo que
 * atiende la peticion en curso (Capa 1 de la estrategia multi-tenant, Req 23).
 *
 * <p><strong>Regla de seguridad (Req 23.4):</strong> el {@code tenant_id}
 * SIEMPRE se deriva del contexto autenticado (el JWT del Usuario) y se coloca
 * aqui mediante el {@link TenantResolutionFilter}. <em>Nunca</em> debe tomarse
 * de parametros de la peticion (query params, path variables, cabeceras
 * arbitrarias o cuerpo), pues ello permitiria acceso cruzado entre empresas.</p>
 *
 * <p>La implementacion se apoya en un {@link ThreadLocal}, por lo que el valor
 * es visible unicamente dentro del hilo que lo establece. El filtro de
 * resolucion garantiza limpiar el contexto al terminar la peticion (bloque
 * {@code finally}) para evitar fugas entre peticiones reutilizadas del pool de
 * hilos del contenedor.</p>
 *
 * <p>Clase de utilidad, no instanciable y thread-safe por diseno.</p>
 */
public final class TenantContext {

    private static final ThreadLocal<UUID> CURRENT_TENANT = new ThreadLocal<>();

    private TenantContext() {
        // Utilidad estatica: no instanciable.
    }

    /**
     * Establece el {@code tenant_id} del hilo actual.
     *
     * @param tenantId identificador de la empresa derivado del contexto
     *                 autenticado; no debe ser {@code null}.
     * @throws TenantContextException si {@code tenantId} es {@code null}.
     */
    public static void set(UUID tenantId) {
        if (tenantId == null) {
            throw new TenantContextException("El tenant_id no puede ser nulo al establecer el contexto");
        }
        CURRENT_TENANT.set(tenantId);
    }

    /**
     * Devuelve el {@code tenant_id} del hilo actual, exigiendo su presencia.
     *
     * @return el {@code tenant_id} vigente.
     * @throws TenantContextException si no hay ningun tenant establecido.
     */
    public static UUID require() {
        UUID tenantId = CURRENT_TENANT.get();
        if (tenantId == null) {
            throw new TenantContextException(
                    "No hay un tenant establecido en el contexto de la peticion actual");
        }
        return tenantId;
    }

    /**
     * Devuelve el {@code tenant_id} del hilo actual si existe.
     *
     * @return un {@link Optional} con el {@code tenant_id} o vacio si no hay
     *         contexto (por ejemplo, en operaciones de plataforma sin empresa).
     */
    public static Optional<UUID> getCurrent() {
        return Optional.ofNullable(CURRENT_TENANT.get());
    }

    /**
     * Indica si hay un {@code tenant_id} establecido en el hilo actual.
     *
     * @return {@code true} si existe contexto de tenant; {@code false} en otro caso.
     */
    public static boolean isPresent() {
        return CURRENT_TENANT.get() != null;
    }

    /**
     * Limpia el contexto de tenant del hilo actual.
     *
     * <p>Debe invocarse al finalizar cada peticion (en un bloque
     * {@code finally}) para no filtrar el {@code tenant_id} a peticiones
     * posteriores que reutilicen el mismo hilo del contenedor.</p>
     */
    public static void clear() {
        CURRENT_TENANT.remove();
    }
}
