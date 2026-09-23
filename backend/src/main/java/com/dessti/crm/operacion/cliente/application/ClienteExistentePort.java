package com.dessti.crm.operacion.cliente.application;

import java.util.UUID;

/**
 * Puerto de solo lectura del Núcleo que verifica la existencia de un Cliente
 * accesible dentro del tenant vigente (Req 4.1, 1.5).
 *
 * <p><strong>Ubicación (decisión de la tarea 1.6):</strong> este puerto se define
 * en un paquete <em>neutral</em> del Núcleo —{@code com.dessti.crm.operacion.cliente.application}—
 * en lugar de residir en {@code operacion.produccion} u {@code operacion.proyecto}.
 * Ambos submódulos del Núcleo lo consumen ({@code ServicioOrdenesFabricacion.crearDirecta}
 * y {@code ServicioProyectos.crear}); colocarlo dentro de uno de ellos obligaría al
 * otro a depender de su paquete {@code application}, creando un acoplamiento
 * artificial entre dos consumidores del Núcleo. Un paquete neutral lo hace
 * importable por ambos sin ciclos. El adaptador que lo implementa vive en el
 * módulo <strong>comercial</strong> (donde reside la entidad Cliente), de modo que
 * la dependencia resultante es {@code comercial → operacion.cliente} (adaptador →
 * puerto), sin ciclo con el Núcleo (Req 16.4).</p>
 *
 * <p>Sigue el mismo patrón que los puertos homónimos ya presentes en el módulo
 * comercial ({@code comercial.cotizacion.application.ClienteExistentePort} y
 * {@code comercial.oportunidad.application.ClienteExistentePort}): el consumidor
 * solo necesita saber si el Cliente existe, sin conocer su modelo de persistencia,
 * y sin que la entidad Cliente se exponga fuera de comercial.</p>
 */
public interface ClienteExistentePort {

    /**
     * Indica si existe un Cliente accesible con el identificador dado en el tenant
     * vigente (Req 4.1). El aislamiento por tenant lo garantizan el filtro global
     * de Hibernate y la Row-Level Security de PostgreSQL (RLS + {@code TenantContext}),
     * de modo que un Cliente de otro tenant no se considera existente.
     *
     * @param clienteId identificador del Cliente a verificar.
     * @return {@code true} si el Cliente existe y es accesible en el tenant vigente;
     *         {@code false} en otro caso (incluye {@code clienteId} nulo).
     */
    boolean existeEnTenant(UUID clienteId);
}
