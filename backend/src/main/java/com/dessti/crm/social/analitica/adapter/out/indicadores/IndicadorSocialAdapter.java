package com.dessti.crm.social.analitica.adapter.out.indicadores;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.dessti.crm.reportesbi.application.indicadores.AreaIndicador;
import com.dessti.crm.reportesbi.application.indicadores.FiltroIndicadores;
import com.dessti.crm.reportesbi.application.indicadores.IndicadorSocialPort;
import com.dessti.crm.reportesbi.application.indicadores.IndicadoresArea;
import com.dessti.crm.reportesbi.application.indicadores.ValorIndicador;
import com.dessti.crm.social.analitica.application.ServicioAnaliticaSocial;
import com.dessti.crm.social.analitica.domain.MetricasSociales;

/**
 * Adaptador concreto de <strong>solo lectura</strong> del {@link IndicadorSocialPort}
 * (Req 22.1, 48.1, 66.3). Reutiliza el asiento (seam) del propio modulo social
 * {@link ServicioAnaliticaSocial#metricasDominio} para derivar los mensajes por canal, el
 * tiempo de respuesta promedio y los leads/conversiones (Req 66.1), sin modificar dato
 * alguno (Req 22.2, 48.2, 66.1).
 *
 * <p>Al registrarse como {@link Component} desplaza automaticamente al adaptador por
 * defecto {@code IndicadorSocialVacio}. El aislamiento por tenant (Req 23, 66.6) lo
 * garantizan el filtro global de Hibernate y la RLS de PostgreSQL activos sobre los
 * repositorios del modulo social que consume el servicio de analitica; el {@code tenant_id}
 * nunca viaja en el filtro. El servicio de analitica es de solo lectura y no audita cuando
 * se le invoca por este camino (la auditoria de indicadores la realiza el consumidor).</p>
 */
@Component
public class IndicadorSocialAdapter implements IndicadorSocialPort {

    private static final int ESCALA_TIEMPO = 0;

    private final ServicioAnaliticaSocial servicioAnaliticaSocial;

    /**
     * Crea el adaptador con el servicio de analitica social del propio modulo.
     *
     * @param servicioAnaliticaSocial servicio de solo lectura de metricas sociales.
     */
    public IndicadorSocialAdapter(ServicioAnaliticaSocial servicioAnaliticaSocial) {
        this.servicioAnaliticaSocial = servicioAnaliticaSocial;
    }

    @Override
    @Transactional(readOnly = true)
    public IndicadoresArea agregar(FiltroIndicadores filtro) {
        // Todos los canales (canal = null); el rango de fechas lo interpreta el servicio.
        List<MetricasSociales> metricas =
                servicioAnaliticaSocial.metricasDominio(filtro.desde(), filtro.hasta(), null);

        long recibidosTotal = 0;
        long enviadosTotal = 0;
        long conversionesTotal = 0;
        long alcanceTotal = 0;
        // Promedio ponderado del tiempo de respuesta por conversaciones con respuesta
        // (aproximado por el alcance de cada canal), para consolidar canales heterogeneos.
        long tiempoPonderadoAcumulado = 0;
        long pesoAcumulado = 0;

        List<ValorIndicador> indicadores = new ArrayList<>();
        for (MetricasSociales m : metricas) {
            recibidosTotal += m.mensajesRecibidos();
            enviadosTotal += m.mensajesEnviados();
            conversionesTotal += m.conversiones();
            alcanceTotal += m.alcance();
            if (m.tiempoRespuestaPromedioSegundos() > 0 && m.alcance() > 0) {
                tiempoPonderadoAcumulado += m.tiempoRespuestaPromedioSegundos() * m.alcance();
                pesoAcumulado += m.alcance();
            }
            indicadores.add(ValorIndicador.conteo(
                    "mensajes_" + m.canal().valorBd(),
                    "Mensajes en " + m.canal().valorBd(),
                    BigDecimal.valueOf(m.interacciones())));
        }

        BigDecimal tiempoRespuestaPromedio = pesoAcumulado == 0
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(tiempoPonderadoAcumulado)
                        .divide(BigDecimal.valueOf(pesoAcumulado), ESCALA_TIEMPO, RoundingMode.HALF_UP);

        indicadores.add(ValorIndicador.conteo(
                "mensajes_recibidos",
                "Mensajes recibidos",
                BigDecimal.valueOf(recibidosTotal)));
        indicadores.add(ValorIndicador.conteo(
                "mensajes_enviados",
                "Mensajes enviados",
                BigDecimal.valueOf(enviadosTotal)));
        indicadores.add(ValorIndicador.conteo(
                "conversaciones_alcance",
                "Conversaciones alcanzadas",
                BigDecimal.valueOf(alcanceTotal)));
        indicadores.add(new ValorIndicador(
                "tiempo_respuesta_promedio",
                "Tiempo de respuesta promedio",
                tiempoRespuestaPromedio,
                "segundos",
                null));
        indicadores.add(ValorIndicador.conteo(
                "leads_captados",
                "Leads captados",
                BigDecimal.valueOf(conversionesTotal)));

        return new IndicadoresArea(AreaIndicador.REDES_SOCIALES, indicadores);
    }
}
