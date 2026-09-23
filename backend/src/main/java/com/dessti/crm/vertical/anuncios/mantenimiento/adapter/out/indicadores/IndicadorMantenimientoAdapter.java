package com.dessti.crm.vertical.anuncios.mantenimiento.adapter.out.indicadores;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.dessti.crm.vertical.anuncios.mantenimiento.adapter.out.persistence.TicketServicioRepository;
import com.dessti.crm.reportesbi.application.indicadores.AreaIndicador;
import com.dessti.crm.reportesbi.application.indicadores.FiltroIndicadores;
import com.dessti.crm.reportesbi.application.indicadores.IndicadorMantenimientoPort;
import com.dessti.crm.reportesbi.application.indicadores.IndicadoresArea;
import com.dessti.crm.reportesbi.application.indicadores.RangoPeriodo;
import com.dessti.crm.reportesbi.application.indicadores.ValorIndicador;

/**
 * Adaptador concreto de <strong>solo lectura</strong> del {@link IndicadorMantenimientoPort}
 * (Req 22.1, 48.1). Deriva del propio modulo de mantenimiento el cumplimiento del SLA de los
 * Tickets_Servicio resueltos (respuesta y resolucion), agregando unicamente con consultas
 * {@code COUNT} que no modifican dato alguno (Req 22.2, 48.2).
 *
 * <p>Al registrarse como {@link Component} desplaza automaticamente al adaptador por
 * defecto {@code IndicadorMantenimientoVacio}. El aislamiento por tenant (Req 23) lo
 * garantizan el filtro global de Hibernate y la RLS de PostgreSQL activos sobre el
 * repositorio del modulo; el {@code tenant_id} nunca viaja en el filtro.</p>
 */
@Component
public class IndicadorMantenimientoAdapter implements IndicadorMantenimientoPort {

    private static final int ESCALA_PORCENTAJE = 2;

    private final TicketServicioRepository ticketServicioRepository;

    /**
     * Crea el adaptador con el repositorio de solo lectura de Tickets_Servicio.
     *
     * @param ticketServicioRepository repositorio de Tickets_Servicio.
     */
    public IndicadorMantenimientoAdapter(TicketServicioRepository ticketServicioRepository) {
        this.ticketServicioRepository = ticketServicioRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public IndicadoresArea agregar(FiltroIndicadores filtro) {
        // Cotas no nulas [EPOCH, 9999): preservan la semantica "sin limite" y evitan el
        // fallo de inferencia de tipo de PostgreSQL sobre columnas timestamp.
        Instant desde = RangoPeriodo.desdeInclusivoOMinimo(filtro.desde());
        Instant hasta = RangoPeriodo.hastaExclusivoOMaximo(filtro.hasta());

        long resolucionCumplida = ticketServicioRepository
                .contarPorCumplimientoSlaResolucion(true, desde, hasta);
        long resolucionIncumplida = ticketServicioRepository
                .contarPorCumplimientoSlaResolucion(false, desde, hasta);
        long respuestaCumplida = ticketServicioRepository
                .contarPorCumplimientoSlaRespuesta(true, desde, hasta);
        long respuestaIncumplida = ticketServicioRepository
                .contarPorCumplimientoSlaRespuesta(false, desde, hasta);

        return new IndicadoresArea(AreaIndicador.MANTENIMIENTO, List.of(
                ValorIndicador.conteo(
                        "tickets_sla_resolucion_cumplido",
                        "Tickets con SLA de resolucion cumplido",
                        BigDecimal.valueOf(resolucionCumplida)),
                ValorIndicador.conteo(
                        "tickets_sla_resolucion_incumplido",
                        "Tickets con SLA de resolucion incumplido",
                        BigDecimal.valueOf(resolucionIncumplida)),
                new ValorIndicador(
                        "cumplimiento_sla_resolucion",
                        "Cumplimiento del SLA de resolucion",
                        porcentaje(resolucionCumplida, resolucionCumplida + resolucionIncumplida),
                        "porcentaje",
                        null),
                ValorIndicador.conteo(
                        "tickets_sla_respuesta_cumplido",
                        "Tickets con SLA de respuesta cumplido",
                        BigDecimal.valueOf(respuestaCumplida)),
                ValorIndicador.conteo(
                        "tickets_sla_respuesta_incumplido",
                        "Tickets con SLA de respuesta incumplido",
                        BigDecimal.valueOf(respuestaIncumplida)),
                new ValorIndicador(
                        "cumplimiento_sla_respuesta",
                        "Cumplimiento del SLA de respuesta",
                        porcentaje(respuestaCumplida, respuestaCumplida + respuestaIncumplida),
                        "porcentaje",
                        null)));
    }

    private static BigDecimal porcentaje(long parte, long total) {
        if (total == 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(parte)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(total), ESCALA_PORCENTAJE, RoundingMode.HALF_UP);
    }
}
