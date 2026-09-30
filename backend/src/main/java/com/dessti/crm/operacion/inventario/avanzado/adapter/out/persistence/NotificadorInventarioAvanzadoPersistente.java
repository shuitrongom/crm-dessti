package com.dessti.crm.operacion.inventario.avanzado.adapter.out.persistence;

import java.time.Clock;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.dessti.crm.operacion.inventario.avanzado.application.NotificacionReabastecimiento;
import com.dessti.crm.operacion.inventario.avanzado.application.NotificacionStockMaximo;
import com.dessti.crm.operacion.inventario.avanzado.application.NotificacionStockMinimo;
import com.dessti.crm.operacion.inventario.avanzado.application.NotificadorInventarioAvanzadoPort;
import com.dessti.crm.operacion.inventario.avanzado.domain.AlertaInventario;
import com.dessti.crm.operacion.inventario.avanzado.domain.TipoAlertaInventario;

/**
 * Adaptador de salida que PERSISTE las alertas de stock del inventario avanzado (Req 60) en
 * la tabla {@code alerta_inventario} (V88), ademas de registrarlas en el log. Sustituye al
 * placeholder {@code NotificadorInventarioAvanzadoRegistroLog}: al ser un bean concreto del
 * puerto {@link NotificadorInventarioAvanzadoPort}, desactiva automaticamente el bean por
 * defecto de {@code InventarioAvanzadoConfig} ({@code @ConditionalOnMissingBean}).
 *
 * <p>Con esto, las condiciones de stock minimo/maximo/reabastecimiento detectadas por el
 * servicio quedan disponibles para consultarse desde la UI (bitacora de alertas), sin dejar
 * de escribir la traza en el log. La persistencia ocurre dentro de la transaccion del
 * movimiento que dispara la deteccion (el metodo de servicio es transaccional), de modo que
 * una alerta solo se guarda si el movimiento se confirma.</p>
 *
 * <p>Es de infraestructura (adapter/out): traduce los DTOs de notificacion del puerto a la
 * entidad de dominio {@link AlertaInventario} y delega en el repositorio JPA.</p>
 */
@Component
public class NotificadorInventarioAvanzadoPersistente implements NotificadorInventarioAvanzadoPort {

    private static final Logger log =
            LoggerFactory.getLogger(NotificadorInventarioAvanzadoPersistente.class);

    private final AlertaInventarioRepository alertaRepository;
    private final Clock clock;

    public NotificadorInventarioAvanzadoPersistente(AlertaInventarioRepository alertaRepository,
                                                    Clock clock) {
        this.alertaRepository = alertaRepository;
        this.clock = clock;
    }

    @Override
    public void notificarStockMinimo(NotificacionStockMinimo n) {
        persistir(TipoAlertaInventario.MINIMO, n.almacenId(), n.materialId(), n.nombre(),
                n.cantidad(), n.umbral());
        log.warn("[STOCK_MINIMO] tenant={} almacen={} material={} nombre='{}' cantidad={} umbral={}",
                n.tenantId(), n.almacenId(), n.materialId(), n.nombre(), n.cantidad(), n.umbral());
    }

    @Override
    public void notificarStockMaximo(NotificacionStockMaximo n) {
        persistir(TipoAlertaInventario.MAXIMO, n.almacenId(), n.materialId(), n.nombre(),
                n.cantidad(), n.umbral());
        log.warn("[STOCK_MAXIMO] tenant={} almacen={} material={} nombre='{}' cantidad={} umbral={}",
                n.tenantId(), n.almacenId(), n.materialId(), n.nombre(), n.cantidad(), n.umbral());
    }

    @Override
    public void notificarReabastecimiento(NotificacionReabastecimiento n) {
        persistir(TipoAlertaInventario.REABASTECIMIENTO, n.almacenId(), n.materialId(), n.nombre(),
                n.cantidad(), n.umbral());
        log.warn("[REABASTECIMIENTO] tenant={} almacen={} material={} nombre='{}' cantidad={} umbral={}",
                n.tenantId(), n.almacenId(), n.materialId(), n.nombre(), n.cantidad(), n.umbral());
    }

    private void persistir(TipoAlertaInventario tipo, java.util.UUID almacenId,
                           java.util.UUID materialId, String nombre,
                           java.math.BigDecimal cantidad, java.math.BigDecimal umbral) {
        Instant ahora = Instant.now(clock);
        AlertaInventario alerta = AlertaInventario.registrar(
                tipo, almacenId, materialId, nombre, cantidad, umbral, ahora, "sistema");
        alertaRepository.save(alerta);
    }
}
