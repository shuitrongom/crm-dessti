package com.dessti.crm.vertical.anuncios.permiso.application;

import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.dessti.crm.platform.tenant.TenantContext;
import com.dessti.crm.platform.tenant.TenantSessionInitializer;

/**
 * Planificador diario <strong>multi-tenant</strong> (Decisión D8) que dispara la
 * notificación de vencimientos próximos de permisos (Req 13.1, 13.5, 13.6),
 * siguiendo el patrón de {@code ProgramadorRespaldos}.
 *
 * <p><strong>Seguridad en pruebas:</strong> el bean solo existe cuando
 * {@code crm.permisos.vencimientos.habilitado=true}
 * ({@link ConditionalOnProperty}). Por defecto la capacidad está desactivada, de
 * modo que en pruebas (incluidos los tests de contexto) y en un arranque sin
 * configurar el planificador NO se registra ni se dispara.</p>
 *
 * <p><strong>Iteración multi-tenant (Req 13.6):</strong> enumera las Empresas
 * activas vía {@link EmpresasActivasPort} y, por cada {@code tenantId}, dentro de
 * una <em>transacción propia</em> ({@link TransactionTemplate}), fija el contexto
 * de tenant ({@link TenantContext#set(UUID)}) y la variable de sesión de RLS
 * ({@link TenantSessionInitializer#applyTenant(UUID)}) antes de invocar
 * {@link ServicioPermisos#notificarVencimientosProximos()}. El
 * {@link TenantContext} se limpia en un bloque {@code finally} para no filtrar el
 * tenant a la siguiente iteración del hilo del planificador.</p>
 *
 * <p><strong>Aislamiento de fallos:</strong> un fallo al procesar un tenant se
 * captura y se registra (log) sin propagarse, de modo que el barrido continúa con
 * el resto de tenants y las futuras ejecuciones se siguen reprogramando.</p>
 *
 * <p>{@code @EnableScheduling} ya está habilitado por {@code ConfiguracionRespaldo}
 * (para {@code ProgramadorRespaldos}); no se duplica aquí.</p>
 */
@Component
@ConditionalOnProperty(name = "crm.permisos.vencimientos.habilitado", havingValue = "true")
public class ProgramadorVencimientosPermisos {

    private static final Logger log = LoggerFactory.getLogger(ProgramadorVencimientosPermisos.class);

    private final EmpresasActivasPort empresasActivas;
    private final ServicioPermisos servicioPermisos;
    private final TenantSessionInitializer tenantSession;
    private final TransactionTemplate transactionTemplate;

    public ProgramadorVencimientosPermisos(EmpresasActivasPort empresasActivas,
                                           ServicioPermisos servicioPermisos,
                                           TenantSessionInitializer tenantSession,
                                           PlatformTransactionManager transactionManager) {
        this.empresasActivas = empresasActivas;
        this.servicioPermisos = servicioPermisos;
        this.tenantSession = tenantSession;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /**
     * Barrido periódico programado (Req 13.1). La expresión cron se resuelve desde
     * {@code crm.permisos.vencimientos.cron} (por defecto diario a las 06:00).
     * Enumera las Empresas activas y procesa cada tenant de forma aislada.
     */
    @Scheduled(cron = "${crm.permisos.vencimientos.cron:0 0 6 * * *}")
    public void notificarVencimientosProgramado() {
        List<UUID> tenants = empresasActivas.tenantsActivos();
        log.info("Iniciando barrido de vencimientos de permisos para {} empresa(s) activa(s) (Req 13.6).",
                tenants.size());
        for (UUID tenantId : tenants) {
            procesarTenant(tenantId);
        }
    }

    /**
     * Procesa un tenant dentro de su propia transacción: fija el contexto de
     * tenant y la variable de sesión de RLS y ejecuta la notificación de
     * vencimientos. Un fallo por tenant se registra y NO detiene el barrido del
     * resto (Req 13.6). El {@link TenantContext} se limpia siempre en
     * {@code finally}.
     *
     * @param tenantId identificador del tenant (Empresa activa) a procesar.
     */
    private void procesarTenant(UUID tenantId) {
        try {
            transactionTemplate.executeWithoutResult(status -> {
                TenantContext.set(tenantId);
                tenantSession.applyTenant(tenantId);
                int notificados = servicioPermisos.notificarVencimientosProximos();
                log.debug("Tenant {}: {} permiso(s) por vencer notificado(s).", tenantId, notificados);
            });
        } catch (RuntimeException e) {
            // Aislamiento de fallos: se registra sin propagar para no detener el
            // barrido del resto de tenants ni la reprogramación futura.
            log.error("Fallo al notificar vencimientos de permisos del tenant {} (Req 13.6): {}",
                    tenantId, e.getMessage());
        } finally {
            TenantContext.clear();
        }
    }
}
