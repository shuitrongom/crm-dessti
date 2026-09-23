package com.dessti.crm.platform.config;

import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.aop.interceptor.SimpleAsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import com.dessti.crm.platform.tenant.TenantContext;

/**
 * Configuracion del procesamiento asincrono para operaciones intensivas
 * (Req 51.3): calculo de Nomina, generacion de Estado_Financiero, analisis de
 * Inteligencia_Negocio y timbrado masivo. Estas operaciones se delegan a un
 * <em>executor</em> dedicado y acotado para no bloquear la operacion
 * interactiva del resto de los Usuarios.
 *
 * <p><strong>Alcance (decision conservadora):</strong> esta clase provee
 * unicamente la <em>infraestructura</em> (habilitacion de {@code @Async} y el
 * bean del executor con su decorador de contexto). No se anaden anotaciones
 * {@code @Async} a los servicios sincronos existentes, pues ello alteraria su
 * semantica transaccional y de consistencia. Los casos de uso que quieran
 * ejecutarse fuera del hilo de la peticion deben inyectar el executor por su
 * qualifier {@link #EJECUTOR_INTENSIVO} y encargarse de su propia gestion de
 * transacciones dentro de la tarea.</p>
 *
 * <p><strong>Propagacion de contexto (Req 23):</strong> las tareas asincronas
 * se ejecutan en hilos del pool distintos del hilo de la peticion y, por tanto,
 * <em>no</em> heredan el {@link ThreadLocal} del {@link TenantContext} ni el
 * {@code SecurityContext}. Para preservar el aislamiento multi-tenant y la
 * identidad del actor, el executor se envuelve con un {@link TaskDecorator}
 * ({@link ContextoTenantSeguridadTaskDecorator}) que <b>copia</b> el
 * {@code tenant_id} y el {@code SecurityContext} vigentes al capturar la tarea,
 * los <b>establece</b> en el hilo trabajador antes de ejecutarla y los
 * <b>limpia</b> siempre en un bloque {@code finally}, evitando fugas entre
 * tareas que reutilicen el mismo hilo del pool.</p>
 */
@Configuration
@EnableAsync
public class ConfiguracionAsync implements AsyncConfigurer {

    /**
     * Qualifier del executor dedicado a operaciones intensivas. Los casos de
     * uso lo referencian con {@code @Async("ejecutorIntensivo")} o inyectando
     * el bean por su nombre.
     */
    public static final String EJECUTOR_INTENSIVO = "ejecutorIntensivo";

    private static final Logger log = LoggerFactory.getLogger(ConfiguracionAsync.class);

    /**
     * Executor dedicado a operaciones intensivas (Req 51.3). Pool acotado con
     * cola limitada y politica de rechazo {@code CallerRunsPolicy}: si el pool y
     * la cola estan saturados, la tarea se ejecuta en el hilo invocador,
     * aplicando contrapresion en lugar de descartar trabajo o crecer sin limite.
     *
     * <p>Se registra como el executor por defecto de {@code @Async} (via
     * {@link #getAsyncExecutor()}) y ademas queda disponible por su nombre
     * {@link #EJECUTOR_INTENSIVO} para uso explicito.</p>
     *
     * @return el {@link ThreadPoolTaskExecutor} configurado.
     */
    @Bean(name = EJECUTOR_INTENSIVO)
    public ThreadPoolTaskExecutor ejecutorIntensivo() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // Nucleo/maximo acotados: dimensionados para trabajo intensivo de CPU/IO
        // sin ahogar al contenedor. Ajustables en despliegue si fuese necesario.
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(100);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("crm-intensivo-");
        // Contrapresion: nunca se descarta trabajo intensivo (nomina, timbrado
        // masivo). Si el pool y la cola se saturan, corre en el hilo invocador.
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        // Propaga tenant_id + SecurityContext al hilo trabajador (Req 23).
        executor.setTaskDecorator(new ContextoTenantSeguridadTaskDecorator());
        // Apagado ordenado: espera a que terminen las tareas en curso.
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        log.info("Executor de operaciones intensivas '{}' inicializado (core={}, max={}, cola={}) (Req 51.3).",
                EJECUTOR_INTENSIVO, executor.getCorePoolSize(), executor.getMaxPoolSize(), 100);
        return executor;
    }

    /**
     * Executor por defecto para los metodos {@code @Async} sin qualifier: el
     * mismo executor intensivo, de modo que toda tarea asincrona propague el
     * contexto de tenant y seguridad.
     */
    @Override
    public Executor getAsyncExecutor() {
        return ejecutorIntensivo();
    }

    /**
     * Manejador de excepciones no capturadas en metodos {@code @Async} de tipo
     * {@code void}: registra el fallo (sin exponer datos sensibles) en lugar de
     * perderlo en silencio.
     */
    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return new SimpleAsyncUncaughtExceptionHandler();
    }

    /**
     * {@link TaskDecorator} que propaga el {@code tenant_id} (Capa 1 multi-tenant,
     * Req 23) y el {@code SecurityContext} de Spring Security desde el hilo que
     * <em>captura</em> la tarea hacia el hilo del pool que la <em>ejecuta</em>.
     *
     * <p>El contexto se <b>copia</b> en el momento de decorar (hilo de la
     * peticion), se <b>establece</b> antes de correr la tarea y se
     * <b>restaura/limpia</b> siempre en {@code finally}, garantizando que un
     * hilo reutilizado del pool no arrastre el tenant ni la identidad de una
     * tarea previa.</p>
     */
    static final class ContextoTenantSeguridadTaskDecorator implements TaskDecorator {

        @Override
        public Runnable decorate(Runnable runnable) {
            // Captura en el hilo de origen (puede no haber tenant en tareas de
            // plataforma; se representa como null y no se establece).
            UUID tenantId = TenantContext.getCurrent().orElse(null);
            SecurityContext securityContext = SecurityContextHolder.getContext();

            return () -> {
                // Estado previo del hilo trabajador, para restaurarlo despues.
                UUID tenantPrevio = TenantContext.getCurrent().orElse(null);
                SecurityContext securityPrevio = SecurityContextHolder.getContext();
                try {
                    if (tenantId != null) {
                        TenantContext.set(tenantId);
                    } else {
                        TenantContext.clear();
                    }
                    if (securityContext != null) {
                        SecurityContextHolder.setContext(securityContext);
                    } else {
                        SecurityContextHolder.clearContext();
                    }
                    runnable.run();
                } finally {
                    // Restaura el estado previo del hilo del pool (o lo limpia),
                    // evitando fugas de tenant/identidad entre tareas (Req 23).
                    if (tenantPrevio != null) {
                        TenantContext.set(tenantPrevio);
                    } else {
                        TenantContext.clear();
                    }
                    if (securityPrevio != null) {
                        SecurityContextHolder.setContext(securityPrevio);
                    } else {
                        SecurityContextHolder.clearContext();
                    }
                }
            };
        }
    }
}
