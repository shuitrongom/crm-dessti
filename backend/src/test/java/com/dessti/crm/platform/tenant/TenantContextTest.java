package com.dessti.crm.platform.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Pruebas unitarias puras de {@link TenantContext} (sin contexto de Spring ni
 * base de datos). Verifican set/require/getCurrent/clear y el aislamiento por
 * hilo del ThreadLocal (Req 23).
 */
class TenantContextTest {

    @AfterEach
    void cleanUp() {
        // Garantiza que ninguna prueba filtre estado al hilo de la siguiente.
        TenantContext.clear();
    }

    @Test
    @DisplayName("set() y require() devuelven el mismo tenant en el hilo actual")
    void setAndRequireReturnSameTenant() {
        UUID tenantId = UUID.randomUUID();

        TenantContext.set(tenantId);

        assertThat(TenantContext.require()).isEqualTo(tenantId);
        assertThat(TenantContext.getCurrent()).contains(tenantId);
        assertThat(TenantContext.isPresent()).isTrue();
    }

    @Test
    @DisplayName("require() lanza excepcion de dominio cuando no hay tenant")
    void requireThrowsWhenNoTenant() {
        assertThat(TenantContext.isPresent()).isFalse();

        assertThatThrownBy(TenantContext::require)
                .isInstanceOf(TenantContextException.class)
                .hasMessageContaining("No hay un tenant");
    }

    @Test
    @DisplayName("set(null) es rechazado con excepcion de dominio")
    void setNullIsRejected() {
        assertThatThrownBy(() -> TenantContext.set(null))
                .isInstanceOf(TenantContextException.class)
                .hasMessageContaining("no puede ser nulo");
    }

    @Test
    @DisplayName("clear() elimina el tenant del hilo actual")
    void clearRemovesTenant() {
        TenantContext.set(UUID.randomUUID());
        assertThat(TenantContext.isPresent()).isTrue();

        TenantContext.clear();

        assertThat(TenantContext.isPresent()).isFalse();
        assertThat(TenantContext.getCurrent()).isEmpty();
    }

    @Test
    @DisplayName("el tenant esta aislado por hilo (ThreadLocal)")
    void tenantIsIsolatedPerThread() throws Exception {
        UUID mainTenant = UUID.randomUUID();
        TenantContext.set(mainTenant);

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            // Otro hilo no debe ver el tenant del hilo principal.
            Future<Boolean> otherThreadPresence = executor.submit(TenantContext::isPresent);
            assertThat(otherThreadPresence.get(5, TimeUnit.SECONDS)).isFalse();

            // Y puede fijar el suyo sin afectar al hilo principal.
            UUID otherTenant = UUID.randomUUID();
            Future<UUID> otherThreadTenant = executor.submit(() -> {
                TenantContext.set(otherTenant);
                UUID resolved = TenantContext.require();
                TenantContext.clear();
                return resolved;
            });
            assertThat(otherThreadTenant.get(5, TimeUnit.SECONDS)).isEqualTo(otherTenant);
        } finally {
            executor.shutdownNow();
        }

        // El hilo principal conserva su propio tenant intacto.
        assertThat(TenantContext.require()).isEqualTo(mainTenant);
    }
}
