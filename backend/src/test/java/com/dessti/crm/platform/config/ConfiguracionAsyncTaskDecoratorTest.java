package com.dessti.crm.platform.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskDecorator;

import com.dessti.crm.platform.tenant.TenantContext;

/**
 * Pruebas unitarias del {@link TaskDecorator} de propagacion de contexto del
 * ejecutor intensivo (Tarea 49.1, Req 51.3, 23). Verifican que el
 * {@code tenant_id} vigente al capturar la tarea se propaga al hilo trabajador y
 * que el contexto del hilo se limpia/restaura al terminar, preservando el
 * aislamiento multi-tenant en hilos reutilizados del pool.
 *
 * <p>Se ejercita directamente la logica del decorador ejecutando el
 * {@link Runnable} decorado en el mismo hilo, tras haber limpiado el contexto
 * (simulando un hilo del pool sin tenant heredado).</p>
 */
@DisplayName("Tarea 49.1 - TaskDecorator propaga TenantContext al hilo asincrono (Req 23, 51.3)")
class ConfiguracionAsyncTaskDecoratorTest {

    private final TaskDecorator decorador =
            new ConfiguracionAsync.ContextoTenantSeguridadTaskDecorator();

    @AfterEach
    void limpiar() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Propaga el tenant_id vigente al hilo que ejecuta la tarea")
    void propagaTenantAlHiloTrabajador() {
        UUID tenant = UUID.randomUUID();
        TenantContext.set(tenant);

        // Se decora capturando el contexto actual (con tenant establecido).
        AtomicReference<UUID> tenantVistoEnTarea = new AtomicReference<>();
        Runnable decorada = decorador.decorate(
                () -> tenantVistoEnTarea.set(TenantContext.getCurrent().orElse(null)));

        // Se simula un hilo trabajador sin tenant heredado antes de ejecutar.
        TenantContext.clear();
        decorada.run();

        assertThat(tenantVistoEnTarea.get())
                .as("La tarea debe ver el tenant capturado al decorar (Req 23)")
                .isEqualTo(tenant);
    }

    @Test
    @DisplayName("Restaura/limpia el contexto del hilo trabajador tras ejecutar (sin fuga)")
    void limpiaContextoTrasEjecutar() {
        UUID tenant = UUID.randomUUID();
        TenantContext.set(tenant);
        Runnable decorada = decorador.decorate(() -> {
            // La tarea usa el tenant propagado.
            assertThat(TenantContext.getCurrent()).contains(tenant);
        });

        // Hilo trabajador sin contexto previo.
        TenantContext.clear();
        decorada.run();

        // Tras ejecutar, el hilo trabajador no debe conservar el tenant (Req 23).
        assertThat(TenantContext.isPresent())
                .as("El hilo del pool no debe arrastrar el tenant tras la tarea")
                .isFalse();
    }

    @Test
    @DisplayName("Restaura el contexto previo del hilo trabajador si ya tenia uno")
    void restauraContextoPrevioDelHilo() {
        UUID tenantPrevioDelHilo = UUID.randomUUID();
        UUID tenantDeLaTarea = UUID.randomUUID();

        // Se captura el contexto de la tarea (tenant de la tarea).
        TenantContext.set(tenantDeLaTarea);
        Runnable decorada = decorador.decorate(
                () -> assertThat(TenantContext.getCurrent()).contains(tenantDeLaTarea));

        // El hilo trabajador ya tenia un tenant previo distinto.
        TenantContext.set(tenantPrevioDelHilo);
        decorada.run();

        // Tras la tarea, el hilo recupera SU tenant previo, no el de la tarea.
        assertThat(TenantContext.getCurrent())
                .as("Debe restaurarse el tenant previo del hilo del pool")
                .contains(tenantPrevioDelHilo);
    }
}
