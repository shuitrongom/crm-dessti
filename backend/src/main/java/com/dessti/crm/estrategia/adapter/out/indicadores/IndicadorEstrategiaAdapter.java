package com.dessti.crm.estrategia.adapter.out.indicadores;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.dessti.crm.estrategia.adapter.out.persistence.ObjetivoEstrategicoRepository;
import com.dessti.crm.reportesbi.application.indicadores.AreaIndicador;
import com.dessti.crm.reportesbi.application.indicadores.FiltroIndicadores;
import com.dessti.crm.reportesbi.application.indicadores.IndicadorEstrategiaPort;
import com.dessti.crm.reportesbi.application.indicadores.IndicadoresArea;
import com.dessti.crm.reportesbi.application.indicadores.RangoPeriodo;
import com.dessti.crm.reportesbi.application.indicadores.ValorIndicador;

/**
 * Adaptador concreto de <strong>solo lectura</strong> del {@link IndicadorEstrategiaPort}
 * (Req 22.1, 48.1). Deriva del propio modulo de estrategia el avance promedio de los
 * Objetivos_Estrategicos, su numero total y los cumplidos, agregando unicamente con
 * consultas {@code AVG}/{@code COUNT} que no modifican dato alguno (Req 22.2, 48.2).
 *
 * <p>Al registrarse como {@link Component} desplaza automaticamente al adaptador por
 * defecto {@code IndicadorEstrategiaVacio}. El aislamiento por tenant (Req 23) lo garantizan
 * el filtro global de Hibernate y la RLS de PostgreSQL activos sobre el repositorio del
 * modulo; el {@code tenant_id} nunca viaja en el filtro.</p>
 */
@Component
public class IndicadorEstrategiaAdapter implements IndicadorEstrategiaPort {

    private static final BigDecimal UMBRAL_CUMPLIDO = BigDecimal.valueOf(100);
    private static final int ESCALA_AVANCE = 2;

    private final ObjetivoEstrategicoRepository objetivoEstrategicoRepository;

    /**
     * Crea el adaptador con el repositorio de solo lectura de Objetivos_Estrategicos.
     *
     * @param objetivoEstrategicoRepository repositorio de Objetivos_Estrategicos.
     */
    public IndicadorEstrategiaAdapter(
            ObjetivoEstrategicoRepository objetivoEstrategicoRepository) {
        this.objetivoEstrategicoRepository = objetivoEstrategicoRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public IndicadoresArea agregar(FiltroIndicadores filtro) {
        // Cotas de fecha SIEMPRE no nulas (columnas date): las consultas de indicadores
        // comparan directamente (sin ":param IS NULL OR ...") y PostgreSQL infiere el tipo.
        LocalDate desde = RangoPeriodo.fechaDesdeOMinima(filtro.desde());
        LocalDate hasta = RangoPeriodo.fechaHastaOMaxima(filtro.hasta());

        BigDecimal promedio = objetivoEstrategicoRepository.promediarAvance(desde, hasta);
        long total = objetivoEstrategicoRepository.contarObjetivos(desde, hasta);
        long cumplidos = objetivoEstrategicoRepository.contarConAvanceMinimo(UMBRAL_CUMPLIDO, desde, hasta);

        BigDecimal avancePromedio = promedio != null
                ? promedio.setScale(ESCALA_AVANCE, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        return new IndicadoresArea(AreaIndicador.ESTRATEGIA, List.of(
                new ValorIndicador(
                        "avance_promedio_objetivos",
                        "Avance promedio de objetivos",
                        avancePromedio,
                        "porcentaje",
                        null),
                ValorIndicador.conteo(
                        "objetivos_estrategicos_total",
                        "Objetivos estrategicos",
                        BigDecimal.valueOf(total)),
                ValorIndicador.conteo(
                        "objetivos_estrategicos_cumplidos",
                        "Objetivos estrategicos cumplidos",
                        BigDecimal.valueOf(cumplidos))));
    }
}
