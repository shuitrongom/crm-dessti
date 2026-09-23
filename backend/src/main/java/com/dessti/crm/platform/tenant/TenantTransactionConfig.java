package com.dessti.crm.platform.tenant;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * Configura la precedencia del interceptor transaccional de Spring para que el
 * {@link TenantRlsAspect} pueda ejecutarse de forma FIABLE <em>dentro</em> de la
 * transaccion (Capa 2 del aislamiento multi-tenant, Req 23; ver
 * {@code V2__rls_multi_tenant.sql}).
 *
 * <h2>Por que esta configuracion</h2>
 * <p>Por defecto, el asesor (advisor) transaccional de Spring se registra con
 * orden {@link Ordered#LOWEST_PRECEDENCE} (= {@code Integer.MAX_VALUE}), es
 * decir, la MENOR prioridad posible, por lo que queda como el advice MAS
 * INTERNO de la pila de proxies. Con ese valor por defecto es IMPOSIBLE colocar
 * otro {@code @Around} (como el aspecto de RLS) todavia mas al interior mediante
 * {@code @Order} con un valor mayor, porque {@code Integer.MAX_VALUE} ya es el
 * maximo (cualquier suma desborda). Ese era precisamente el bug: el aspecto
 * estaba en {@code LOWEST_PRECEDENCE - 1} (un valor MENOR = MAYOR prioridad),
 * de modo que corria por FUERA del interceptor transaccional y ejecutaba el
 * {@code SET LOCAL app.current_tenant} SIN una transaccion activa (la variable
 * se descartaba y la RLS nunca la veia).</p>
 *
 * <p><strong>Solucion:</strong> se baja la precedencia del interceptor
 * transaccional a un valor FIJO de alta prioridad ({@link #ORDEN_INTERCEPTOR_TX}),
 * dejandolo como advice EXTERNO. Asi el {@link TenantRlsAspect}, con una
 * precedencia FIJA mas interna ({@link TenantRlsAspect#PRECEDENCE}), corre
 * DENTRO de la transaccion ya abierta y el {@code SET LOCAL} se aplica sobre la
 * MISMA conexion transaccional. Es el mecanismo soportado y documentado por
 * Spring ({@code @EnableTransactionManagement(order = ...)}) para controlar la
 * posicion del interceptor transaccional frente a otros aspectos.</p>
 *
 * <h2>Impacto acotado</h2>
 * <p>El unico {@code @Aspect} de la aplicacion es {@link TenantRlsAspect}; los
 * demas usos de {@code Ordered} son filtros de servlet (otro dominio de
 * ordenacion, no advisors de AOP). Por tanto, cambiar el orden del interceptor
 * transaccional solo reordena la relacion tx-interceptor vs. aspecto-RLS, que es
 * justo lo que se busca, sin afectar otros advices.</p>
 */
@Configuration(proxyBeanMethods = false)
@EnableTransactionManagement(order = TenantTransactionConfig.ORDEN_INTERCEPTOR_TX)
public class TenantTransactionConfig {

    /**
     * Precedencia del interceptor transaccional de Spring: alta prioridad
     * (valor bajo) para que quede como advice EXTERNO y el {@link TenantRlsAspect}
     * (interno) corra dentro de la transaccion.
     *
     * <p>Se usa {@code HIGHEST_PRECEDENCE + 100} en lugar del maximo absoluto
     * para dejar margen a eventuales aspectos que deban envolver a la
     * transaccion (por ejemplo, futuros aspectos de auditoria/observabilidad),
     * sin colisionar con este.</p>
     */
    public static final int ORDEN_INTERCEPTOR_TX = Ordered.HIGHEST_PRECEDENCE + 100;
}
