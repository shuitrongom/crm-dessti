package com.dessti.crm.platform.tenant;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Aspecto que fija la variable de sesion de PostgreSQL {@code app.current_tenant}
 * al inicio de cada metodo transaccional, activando la Row-Level Security como
 * SEGUNDA CAPA del aislamiento multi-tenant (Capa 2, Req 23; ver la migracion
 * {@code V2__rls_multi_tenant.sql}).
 *
 * <p><strong>Como funciona:</strong> el aspecto envuelve la ejecucion de todo
 * metodo anotado con {@link org.springframework.transaction.annotation.Transactional}
 * (los casos de uso de aplicacion, tareas 15+). Para que el {@code SET LOCAL}
 * corra sobre la MISMA conexion de la transaccion, el aspecto debe ejecutarse
 * <em>dentro</em> (mas al interior) del interceptor transaccional de Spring.</p>
 *
 * <p><strong>Ordenacion (fix del bug de la RLS que no aplicaba):</strong> en
 * Spring, un valor de {@code @Order} MENOR = MAYOR prioridad = advice mas
 * EXTERNO. El interceptor transaccional se registra, por medio de
 * {@link TenantTransactionConfig}, con alta prioridad
 * ({@link TenantTransactionConfig#ORDEN_INTERCEPTOR_TX}), quedando EXTERNO. Este
 * aspecto usa una precedencia FIJA MAYOR (menor prioridad,
 * {@link #PRECEDENCE}), de modo que queda INTERNO al interceptor transaccional:
 * cuando ejecuta el {@code SET LOCAL} la transaccion ya esta abierta y la orden
 * corre sobre la conexion transaccional (via
 * {@link TenantSessionInitializer#applyCurrentTenant()}).</p>
 *
 * <p>Antes, el aspecto estaba en {@code Ordered.LOWEST_PRECEDENCE - 1} mientras
 * el interceptor transaccional usaba el valor por defecto
 * {@code Ordered.LOWEST_PRECEDENCE}. Al ser {@code LOWEST_PRECEDENCE - 1 <
 * LOWEST_PRECEDENCE}, el aspecto tenia MAYOR prioridad y corria por FUERA de la
 * transaccion, ejecutando el {@code SET LOCAL} sin transaccion activa (se
 * descartaba, la RLS nunca lo veia). Este cambio lo corrige.</p>
 *
 * <p><strong>Coherencia con la Capa 1:</strong> usa el mismo {@code tenant_id}
 * del {@link TenantContext} que el {@link TenantFilterActivator} (filtro de
 * Hibernate). Si no hay tenant (ambito de plataforma del super_admin), no fija
 * la variable; por el diseno fail-safe de las politicas, no se ven filas
 * tenant-scoped, que es el comportamiento seguro deseado.</p>
 *
 * <p><strong>Alcance:</strong> se limita a los paquetes de la aplicacion
 * ({@code com.dessti.crm..*}) para no interceptar transacciones de
 * infraestructura (por ejemplo, las de Flyway o Actuator).</p>
 */
@Aspect
@Component
@Order(TenantRlsAspect.PRECEDENCE)
public class TenantRlsAspect {

    private static final Logger log = LoggerFactory.getLogger(TenantRlsAspect.class);

    /**
     * Precedencia del aspecto: un nivel MAS INTERNO (valor MAYOR = menor
     * prioridad) que el interceptor transaccional de Spring, que
     * {@link TenantTransactionConfig} coloca en
     * {@link TenantTransactionConfig#ORDEN_INTERCEPTOR_TX}. Al ser un valor
     * mayor, este aspecto se ejecuta dentro de la transaccion ya abierta,
     * garantizando que el {@code SET LOCAL} corra sobre su conexion.
     */
    public static final int PRECEDENCE = TenantTransactionConfig.ORDEN_INTERCEPTOR_TX + 100;

    private final TenantSessionInitializer tenantSessionInitializer;

    public TenantRlsAspect(TenantSessionInitializer tenantSessionInitializer) {
        this.tenantSessionInitializer = tenantSessionInitializer;
    }

    /**
     * Metodos anotados con {@code @Transactional} dentro de los paquetes de la
     * aplicacion (a nivel de metodo o de clase).
     */
    @Pointcut("execution(@org.springframework.transaction.annotation.Transactional * com.dessti.crm..*(..)) "
            + "|| execution(* (@org.springframework.transaction.annotation.Transactional com.dessti.crm..*).*(..))")
    public void transactionalMethod() {
        // Pointcut sin cuerpo.
    }

    @Around("transactionalMethod()")
    public Object applyTenantWithinTransaction(ProceedingJoinPoint joinPoint) throws Throwable {
        // En este punto el interceptor transaccional externo ya abrio la
        // transaccion (ver TenantTransactionConfig). Solo se fija el tenant si
        // efectivamente hay una transaccion activa: si no la hubiera (por
        // ejemplo, un @Transactional(propagation = NOT_SUPPORTED) o un metodo
        // sin transaccion real), el SET LOCAL no tendria efecto y no debe
        // intentarse (evita el WARN de "sin transaccion activa").
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            tenantSessionInitializer.applyCurrentTenant();
        } else if (log.isTraceEnabled()) {
            log.trace("Metodo transaccional sin transaccion fisica activa; "
                    + "no se fija app.current_tenant (Capa 2 RLS omitida en este punto).");
        }
        return joinPoint.proceed();
    }
}
