package com.dessti.crm.platform.tenant;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.UUID;
import org.hibernate.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Fija la variable de sesion de PostgreSQL {@code app.current_tenant} sobre la
 * conexion de la transaccion en curso, habilitando la Row-Level Security como
 * SEGUNDA CAPA del aislamiento multi-tenant (Capa 2, Req 23; ver migracion
 * {@code V2__rls_multi_tenant.sql}).
 *
 * <p><strong>Por que {@code SET LOCAL}:</strong> {@code SET LOCAL} acota el
 * alcance de la variable a la <em>transaccion</em> actual (se revierte al
 * terminar la transaccion), de modo que su valor no se filtra a otras
 * operaciones que reutilicen la misma conexion del pool. La orden se ejecuta a
 * traves de {@link Session#doWork} para garantizar que corre sobre la
 * <em>misma</em> conexion JDBC que respalda la transaccion.</p>
 *
 * <p><strong>Seguridad de la sentencia:</strong> el valor del tenant es un
 * {@link UUID} (no texto arbitrario del usuario) y se envia mediante
 * {@code set_config(..., true)} con parametro vinculado ({@code PreparedStatement}),
 * evitando cualquier interpolacion de cadenas y, por tanto, inyeccion SQL.
 * {@code set_config('app.current_tenant', :valor, true)} es el equivalente
 * funcional de {@code SET LOCAL app.current_tenant = :valor} (el tercer
 * argumento {@code true} lo hace local a la transaccion).</p>
 *
 * <p><strong>Coherencia con la Capa 1:</strong> este inicializador es
 * complementario al {@link TenantFilterActivator} (filtro de Hibernate). Ambos
 * usan el mismo {@code tenant_id} del {@link TenantContext} derivado del JWT
 * (Req 23.4). Si no hay tenant en el contexto (ambito de plataforma del
 * super_admin), NO se fija la variable: por el diseno fail-safe de las politicas
 * ({@code current_setting('app.current_tenant', true)} devuelve NULL), no se ven
 * filas tenant-scoped, que es el comportamiento seguro deseado.</p>
 */
@Component
public class TenantSessionInitializer {

    private static final Logger log = LoggerFactory.getLogger(TenantSessionInitializer.class);

    /** Nombre de la variable de sesion de PostgreSQL usada por las politicas RLS. */
    public static final String TENANT_SETTING = "app.current_tenant";

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * Fija {@code app.current_tenant} en la transaccion actual con el
     * {@code tenant_id} del {@link TenantContext}, si existe.
     *
     * <p>Debe invocarse dentro de una transaccion activa. Si no hay tenant en el
     * contexto, no realiza ninguna accion (ambito de plataforma).</p>
     */
    public void applyCurrentTenant() {
        TenantContext.getCurrent().ifPresent(this::applyTenant);
    }

    /**
     * Fija {@code app.current_tenant} en la transaccion actual con un
     * {@code tenant_id} explicito (util para casos de uso de plataforma que
     * operan sobre una empresa concreta y para pruebas).
     *
     * @param tenantId identificador de la empresa; no nulo.
     */
    public void applyTenant(UUID tenantId) {
        if (tenantId == null) {
            throw new TenantContextException("El tenant_id no puede ser nulo al fijar app.current_tenant");
        }
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            // SET LOCAL solo tiene efecto dentro de una transaccion; fuera de
            // ella se revertiria de inmediato. Se advierte para diagnosticar
            // usos incorrectos sin exponer el valor del tenant.
            log.warn("Se intento fijar {} sin una transaccion activa; la RLS podria no aplicarse. "
                    + "Asegure @Transactional en el caso de uso.", TENANT_SETTING);
        }
        Session session = entityManager.unwrap(Session.class);
        session.doWork(connection -> {
            // set_config('app.current_tenant', <uuid>, true) == SET LOCAL ... (transaccion).
            try (var ps = connection.prepareStatement(
                    "SELECT set_config('" + TENANT_SETTING + "', ?, true)")) {
                ps.setString(1, tenantId.toString());
                ps.execute();
            }
        });
    }
}
