package com.dessti.crm.vertical.anuncios.instalacion.adapter.out.indicadores;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.dessti.crm.vertical.anuncios.instalacion.adapter.out.persistence.OrdenTrabajoInstalacionRepository;
import com.dessti.crm.reportesbi.application.indicadores.AreaIndicador;
import com.dessti.crm.reportesbi.application.indicadores.FiltroIndicadores;
import com.dessti.crm.reportesbi.application.indicadores.IndicadorInstalacionPort;
import com.dessti.crm.reportesbi.application.indicadores.IndicadoresArea;
import com.dessti.crm.reportesbi.application.indicadores.RangoPeriodo;
import com.dessti.crm.reportesbi.application.indicadores.ValorIndicador;

/**
 * Adaptador concreto de <strong>solo lectura</strong> del {@link IndicadorInstalacionPort}
 * (Req 22.1, 48.1). Deriva del propio modulo de instalacion el cumplimiento de las fechas
 * programadas de las Ordenes de Trabajo de Instalacion (OTI completadas en fecha frente a
 * fuera de fecha, mas las vencidas sin completar), agregando unicamente con consultas
 * {@code COUNT} que no modifican dato alguno (Req 22.2, 48.2).
 *
 * <p>Al registrarse como {@link Component} desplaza automaticamente al adaptador por
 * defecto {@code IndicadorInstalacionVacio}. El aislamiento por tenant (Req 23) lo
 * garantizan el filtro global de Hibernate y la RLS de PostgreSQL activos sobre el
 * repositorio del modulo; el {@code tenant_id} nunca viaja en el filtro.</p>
 */
@Component
public class IndicadorInstalacionAdapter implements IndicadorInstalacionPort {

    private static final int ESCALA_PORCENTAJE = 2;

    private final OrdenTrabajoInstalacionRepository ordenTrabajoInstalacionRepository;

    /**
     * Crea el adaptador con el repositorio de solo lectura de Ordenes de Trabajo de
     * Instalacion.
     *
     * @param ordenTrabajoInstalacionRepository repositorio de OTI.
     */
    public IndicadorInstalacionAdapter(
            OrdenTrabajoInstalacionRepository ordenTrabajoInstalacionRepository) {
        this.ordenTrabajoInstalacionRepository = ordenTrabajoInstalacionRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public IndicadoresArea agregar(FiltroIndicadores filtro) {
        // Cotas de fecha SIEMPRE no nulas (columna date fecha_programada): las consultas
        // de indicadores comparan directamente (sin ":param IS NULL OR ...") y PostgreSQL
        // infiere el tipo.
        LocalDate desde = RangoPeriodo.fechaDesdeOMinima(filtro.desde());
        LocalDate hasta = RangoPeriodo.fechaHastaOMaxima(filtro.hasta());

        long completadasEnFecha = ordenTrabajoInstalacionRepository
                .contarCompletadasPorCumplimiento(true, desde, hasta);
        long completadasFueraFecha = ordenTrabajoInstalacionRepository
                .contarCompletadasPorCumplimiento(false, desde, hasta);
        long vencidas = ordenTrabajoInstalacionRepository
                .contarVencidas(LocalDate.now(), desde, hasta);

        long totalCompletadas = completadasEnFecha + completadasFueraFecha;
        BigDecimal cumplimiento = totalCompletadas == 0
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(completadasEnFecha)
                        .multiply(BigDecimal.valueOf(100))
                        .divide(BigDecimal.valueOf(totalCompletadas), ESCALA_PORCENTAJE, RoundingMode.HALF_UP);

        return new IndicadoresArea(AreaIndicador.INSTALACION, List.of(
                ValorIndicador.conteo(
                        "instalaciones_completadas_en_fecha",
                        "Instalaciones completadas en fecha",
                        BigDecimal.valueOf(completadasEnFecha)),
                ValorIndicador.conteo(
                        "instalaciones_completadas_fuera_de_fecha",
                        "Instalaciones completadas fuera de fecha",
                        BigDecimal.valueOf(completadasFueraFecha)),
                ValorIndicador.conteo(
                        "instalaciones_vencidas",
                        "Instalaciones vencidas sin completar",
                        BigDecimal.valueOf(vencidas)),
                new ValorIndicador(
                        "cumplimiento_fechas_instalacion",
                        "Cumplimiento de fechas programadas",
                        cumplimiento,
                        "porcentaje",
                        null)));
    }
}
