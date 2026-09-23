package com.dessti.crm.platform.tenant;

/**
 * Excepcion de dominio que indica que se requirio el {@code tenant_id} del
 * contexto de ejecucion pero no habia ninguno establecido.
 *
 * <p>Se lanza tipicamente cuando un caso de uso o un adaptador de persistencia
 * intenta operar sobre datos multi-tenant fuera del alcance de una peticion
 * autenticada (por ejemplo, tareas de plataforma sin contexto de negocio o un
 * hilo que no heredo el contexto).</p>
 */
public class TenantContextException extends RuntimeException {

    public TenantContextException(String message) {
        super(message);
    }
}
