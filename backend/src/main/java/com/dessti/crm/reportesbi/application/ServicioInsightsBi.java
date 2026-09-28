package com.dessti.crm.reportesbi.application;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dessti.crm.platform.audit.AuditoriaPort;
import com.dessti.crm.platform.audit.EventoAuditoria;
import com.dessti.crm.platform.security.rbac.AutenticacionActual;
import com.dessti.crm.platform.tenant.TenantContext;
import com.dessti.crm.reportesbi.application.ia.GeneradorInsightsPort;
import com.dessti.crm.reportesbi.application.ia.IndicadorInsight;
import com.dessti.crm.reportesbi.application.ia.ResultadoInsights;
import com.dessti.crm.reportesbi.application.ia.SolicitudInsights;

/**
 * Servicio de aplicacion de los <strong>insights ejecutivos</strong> de la Inteligencia
 * de Negocio (Req 48, suite BI+IA). Compone el consolidado de indicadores del periodo,
 * lo entrega al {@link GeneradorInsightsPort} para obtener un narrativo en lenguaje
 * natural (es-MX) y devuelve el {@link InsightsDto} con su procedencia.
 *
 * <h2>Reutilizacion y solo lectura</h2>
 * <p>Reutiliza {@link ServicioInteligenciaNegocio#consolidado} para agregar los
 * indicadores (solo lectura, Req 48.2), evitando duplicar la logica de comparativos.
 * Sobre ese consolidado aplana los indicadores de todas las areas a
 * {@link IndicadorInsight} y delega la redaccion en el puerto de IA.</p>
 *
 * <h2>Degradacion gracil</h2>
 * <p>El puerto de IA nunca rompe el flujo: si el proveedor no esta configurado o falla,
 * el adaptador degrada a un narrativo heuristico determinista. El resultado indica su
 * procedencia via {@link InsightsDto#generadoPorIa()}.</p>
 *
 * <h2>Autorizacion y auditoria</h2>
 * <p>El controlador exige el permiso {@code inteligencia_negocio:leer} (Req 48.6). Esta
 * consulta se audita con la accion {@code insights} (Req 48.7), de forma independiente a
 * la auditoria del consolidado subyacente. El {@link Clock} hace determinista la marca
 * {@code generadoEn}.</p>
 */
@Service
public class ServicioInsightsBi {

    /** Tipo de recurso de auditoria/RBAC (comparte el de la Inteligencia de Negocio). */
    static final String RECURSO = ServicioInteligenciaNegocio.RECURSO;

    private final ServicioInteligenciaNegocio servicioInteligenciaNegocio;
    private final GeneradorInsightsPort generadorInsights;
    private final AuditoriaPort auditoria;
    private final Clock clock;

    /**
     * @param servicioInteligenciaNegocio servicio que compone el consolidado reutilizado.
     * @param generadorInsights           puerto de generacion de insights (IA/heuristico).
     * @param auditoria                   puerto de auditoria (Req 48.7).
     * @param clock                       reloj para la marca temporal determinista.
     */
    public ServicioInsightsBi(ServicioInteligenciaNegocio servicioInteligenciaNegocio,
                              GeneradorInsightsPort generadorInsights,
                              AuditoriaPort auditoria,
                              Clock clock) {
        this.servicioInteligenciaNegocio = servicioInteligenciaNegocio;
        this.generadorInsights = generadorInsights;
        this.auditoria = auditoria;
        this.clock = clock;
    }

    /**
     * Genera los insights ejecutivos del periodo (Req 48.1). Compone el consolidado,
     * lo entrega al generador y audita la consulta. Solo lectura (Req 48.2).
     *
     * @param desde     inicio del periodo (inclusivo); {@code null} no filtra por fecha.
     * @param hasta     fin del periodo (inclusivo); {@code null} no filtra por fecha.
     * @param area      area a filtrar; {@code null} incluye a todas.
     * @param dimension dimension de analisis; {@code null} no segmenta.
     * @return el {@link InsightsDto} con el narrativo y su procedencia.
     */
    @Transactional(readOnly = true)
    public InsightsDto generar(LocalDate desde, LocalDate hasta, String area, String dimension) {
        InteligenciaNegocioDto consolidado =
                servicioInteligenciaNegocio.consolidado(desde, hasta, area, dimension, false);

        SolicitudInsights solicitud = new SolicitudInsights(
                consolidado.desde(), consolidado.hasta(), consolidado.area(),
                aplanar(consolidado));
        ResultadoInsights resultado = generadorInsights.generar(solicitud);

        auditar(desde, hasta, area, dimension, resultado.generadoPorIa());
        return new InsightsDto(
                clock.instant(), consolidado.desde(), consolidado.hasta(), consolidado.area(),
                resultado.resumenEjecutivo(), resultado.hallazgos(), resultado.generadoPorIa());
    }

    /**
     * Aplana los indicadores de todas las areas del consolidado a la proyeccion minima
     * que consume el generador de insights, preservando el area de cada indicador.
     */
    private List<IndicadorInsight> aplanar(InteligenciaNegocioDto consolidado) {
        List<IndicadorInsight> indicadores = new ArrayList<>();
        for (IndicadoresAreaDto areaDto : consolidado.areas()) {
            for (IndicadorDto indicador : areaDto.indicadores()) {
                indicadores.add(new IndicadorInsight(
                        areaDto.area(), indicador.clave(), indicador.etiqueta(),
                        indicador.valor(), indicador.unidad(),
                        indicador.comparativo(), indicador.variacion()));
            }
        }
        return indicadores;
    }

    private void auditar(LocalDate desde, LocalDate hasta, String area, String dimension,
                         boolean generadoPorIa) {
        String detalle = "insights inteligencia de negocio [desde=" + desde + ", hasta=" + hasta
                + ", area=" + area + ", dimension=" + dimension
                + ", generadoPorIa=" + generadoPorIa + "]";
        auditoria.registrar(EventoAuditoria.deTenant(
                TenantContext.require(), actorActual(), "insights", RECURSO, detalle, null, null));
    }

    private String actorActual() {
        return AutenticacionActual.obtener()
                .map(Authentication::getName)
                .filter(nombre -> nombre != null && !nombre.isBlank())
                .orElse("sistema");
    }
}
