package com.dessti.crm.platform.tenant;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.mockito.Mockito.mock;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Prueba de slice (sin base de datos ni Testcontainers) que verifica el nucleo
 * del fix de ordenacion del {@link TenantRlsAspect}: que su cuerpo se ejecuta
 * <strong>dentro</strong> de una transaccion activa (Capa 2 RLS, Req 23).
 *
 * <p><strong>Que reproduce el bug:</strong> antes, el aspecto usaba
 * {@code Ordered.LOWEST_PRECEDENCE - 1} y corria por FUERA del interceptor
 * transaccional (que usa {@code LOWEST_PRECEDENCE}), de modo que al fijar
 * {@code app.current_tenant} NO habia transaccion activa. Con
 * {@link TenantTransactionConfig#ORDEN_INTERCEPTOR_TX} (interceptor externo) y
 * {@link TenantRlsAspect#PRECEDENCE} (aspecto interno), el aspecto corre DENTRO
 * de la transaccion. Esta prueba lo comprueba capturando
 * {@link TransactionSynchronizationManager#isActualTransactionActive()} en el
 * momento en que el aspecto invoca al inicializador de sesion.</p>
 *
 * <p><strong>Sin BD:</strong> se usa un {@link PlatformTransactionManager}
 * doble ({@link GestorTransaccionEnMemoria}) que activa el estado de
 * transaccion de {@code TransactionSynchronizationManager} sin abrir ninguna
 * conexion JDBC, y un {@link TenantSessionInitializer} espia que solo registra
 * si habia transaccion activa (no toca la BD).</p>
 *
 * <p>No es {@code *IT}: se ejecuta bajo Surefire ({@code mvn test}) sin Docker.</p>
 */
@DisplayName("TenantRlsAspect corre DENTRO de la transaccion (fix de ordenacion RLS, Req 23)")
class TenantRlsAspectOrdenTransaccionTest {

    private AnnotationConfigApplicationContext contexto;

    @AfterEach
    void cerrar() {
        TenantContext.clear();
        if (contexto != null) {
            contexto.close();
        }
    }

    @Test
    @DisplayName("con tenant en contexto: el aspecto fija el tenant con transaccion activa")
    void aspectoCorreDentroDeTransaccionConTenant() {
        contexto = new AnnotationConfigApplicationContext(ConfiguracionPrueba.class);
        InicializadorEspia espia = contexto.getBean(InicializadorEspia.class);
        ServicioTransaccionalDePrueba servicio = contexto.getBean(ServicioTransaccionalDePrueba.class);

        UUID tenant = UUID.randomUUID();
        TenantContext.set(tenant);

        servicio.operacionTransaccional();

        // El aspecto invoco al inicializador exactamente una vez y, en ese
        // instante, HABIA una transaccion fisica activa (nucleo del fix).
        assertThat(espia.invocaciones()).isEqualTo(1);
        assertThat(espia.huboTransaccionActivaEnLaInvocacion())
                .as("El SET LOCAL del tenant debe correr con una transaccion activa")
                .isTrue();
    }

    @Test
    @DisplayName("sin tenant en contexto (super_admin): no se fija tenant y no hay WARN de 'sin transaccion'")
    void sinTenantNoFijaTenant() {
        contexto = new AnnotationConfigApplicationContext(ConfiguracionPrueba.class);
        InicializadorEspia espia = contexto.getBean(InicializadorEspia.class);
        ServicioTransaccionalDePrueba servicio = contexto.getBean(ServicioTransaccionalDePrueba.class);

        // Sin TenantContext (ambito de plataforma del super_admin).
        servicio.operacionTransaccional();

        // El aspecto SI se ejecuta (hay transaccion activa) y delega en
        // applyCurrentTenant(), que al no haber tenant no aplica nada: el espia
        // registra que hubo transaccion activa pero no se fija ningun tenant.
        assertThat(espia.invocaciones()).isEqualTo(1);
        assertThat(espia.huboTransaccionActivaEnLaInvocacion()).isTrue();
        assertThat(espia.tenantsFijados()).isZero();
    }

    // ------------------------------------------------------------------
    // Configuracion de prueba (mismo orden de interceptor que produccion)
    // ------------------------------------------------------------------

    @Configuration(proxyBeanMethods = false)
    @EnableAspectJAutoProxy
    @EnableTransactionManagement(order = TenantTransactionConfig.ORDEN_INTERCEPTOR_TX)
    static class ConfiguracionPrueba {

        @Bean
        PlatformTransactionManager transactionManager() {
            return new GestorTransaccionEnMemoria();
        }

        /**
         * {@link EntityManagerFactory} doble para satisfacer la inyeccion del
         * {@code @PersistenceContext} heredado por {@link InicializadorEspia}.
         * Nunca se usa: el espia sobreescribe los metodos que tocarian la BD.
         */
        @Bean
        EntityManagerFactory entityManagerFactory() {
            EntityManagerFactory emf = mock(EntityManagerFactory.class);
            org.mockito.Mockito.lenient()
                    .when(emf.createEntityManager()).thenReturn(mock(EntityManager.class));
            return emf;
        }

        @Bean
        InicializadorEspia inicializadorEspia() {
            return new InicializadorEspia();
        }

        @Bean
        TenantRlsAspect tenantRlsAspect(InicializadorEspia inicializador) {
            return new TenantRlsAspect(inicializador);
        }

        @Bean
        ServicioTransaccionalDePrueba servicioTransaccionalDePrueba() {
            return new ServicioTransaccionalDePrueba();
        }
    }

    /** Servicio con un metodo {@code @Transactional} para activar la pila de proxies. */
    static class ServicioTransaccionalDePrueba {
        @Transactional
        public void operacionTransaccional() {
            // Cuerpo vacio: el interes esta en la pila de advices (tx + aspecto RLS).
        }
    }

    /**
     * Espia del {@link TenantSessionInitializer} que no accede a la BD: solo
     * registra si habia transaccion activa cuando el aspecto lo invoco y cuantos
     * tenants se intentaron fijar (para el caso super_admin).
     */
    static class InicializadorEspia extends TenantSessionInitializer {
        private final AtomicInteger invocaciones = new AtomicInteger();
        private final AtomicInteger tenantsFijados = new AtomicInteger();
        private final AtomicBoolean huboTransaccionActiva = new AtomicBoolean(false);

        @Override
        public void applyCurrentTenant() {
            invocaciones.incrementAndGet();
            huboTransaccionActiva.set(TransactionSynchronizationManager.isActualTransactionActive());
            // Replica la logica real: solo hay tenant que fijar si el contexto lo tiene.
            TenantContext.getCurrent().ifPresent(t -> tenantsFijados.incrementAndGet());
        }

        @Override
        public void applyTenant(UUID tenantId) {
            // No se usa en esta prueba; se sobreescribe para no tocar la BD.
            tenantsFijados.incrementAndGet();
        }

        int invocaciones() {
            return invocaciones.get();
        }

        int tenantsFijados() {
            return tenantsFijados.get();
        }

        boolean huboTransaccionActivaEnLaInvocacion() {
            return huboTransaccionActiva.get();
        }
    }

    /**
     * Gestor de transacciones minimo en memoria: participa en
     * {@link TransactionSynchronizationManager} (marca la transaccion como
     * activa) sin abrir ninguna conexion. Suficiente para verificar la
     * ordenacion del aspecto frente al interceptor transaccional.
     */
    static class GestorTransaccionEnMemoria extends AbstractPlatformTransactionManager {

        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
            // No hay recurso real que enlazar; el estado "transaccion activa" lo
            // gestiona AbstractPlatformTransactionManager via newTransaction=true.
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
            // No-op: sin recursos que confirmar.
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
            // No-op: sin recursos que revertir.
        }
    }
}
