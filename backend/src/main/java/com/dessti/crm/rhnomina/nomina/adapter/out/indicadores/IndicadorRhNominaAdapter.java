package com.dessti.crm.rhnomina.nomina.adapter.out.indicadores;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.dessti.crm.reportesbi.application.indicadores.AreaIndicador;
import com.dessti.crm.reportesbi.application.indicadores.FiltroIndicadores;
import com.dessti.crm.reportesbi.application.indicadores.IndicadorRhNominaPort;
import com.dessti.crm.reportesbi.application.indicadores.IndicadoresArea;
import com.dessti.crm.reportesbi.application.indicadores.RangoPeriodo;
import com.dessti.crm.reportesbi.application.indicadores.ValorIndicador;
import com.dessti.crm.rhnomina.nomina.adapter.out.persistence.ReciboNominaRepository;

/**
 * Adaptador concreto de <strong>solo lectura</strong> del {@link IndicadorRhNominaPort}
 * (Req 22.1, 48.1). Deriva del propio modulo de RH/nomina el costo de nomina del periodo
 * (percepciones) y el neto pagado, agregando unicamente con consultas {@code SUM}/
 * {@code COUNT} que no modifican dato alguno (Req 22.2, 48.2).
 *
 * <p>Al registrarse como {@link Component} desplaza automaticamente al adaptador por
 * defecto {@code IndicadorRhNominaVacio}. El aislamiento por tenant (Req 23) lo garantizan
 * el filtro global de Hibernate y la RLS de PostgreSQL activos sobre el repositorio del
 * modulo; el {@code tenant_id} nunca viaja en el filtro.</p>
 */
@Component
public class IndicadorRhNominaAdapter implements IndicadorRhNominaPort {

    private final ReciboNominaRepository reciboNominaRepository;

    /**
     * Crea el adaptador con el repositorio de solo lectura de Recibo_Nomina.
     *
     * @param reciboNominaRepository repositorio de Recibo_Nomina.
     */
    public IndicadorRhNominaAdapter(ReciboNominaRepository reciboNominaRepository) {
        this.reciboNominaRepository = reciboNominaRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public IndicadoresArea agregar(FiltroIndicadores filtro) {
        // Cotas no nulas [EPOCH, 9999): preservan la semantica "sin limite" y evitan el
        // fallo de inferencia de tipo de PostgreSQL sobre columnas timestamp.
        Instant desde = RangoPeriodo.desdeInclusivoOMinimo(filtro.desde());
        Instant hasta = RangoPeriodo.hastaExclusivoOMaximo(filtro.hasta());

        BigDecimal costo = reciboNominaRepository.sumarCostoNomina(desde, hasta);
        BigDecimal neto = reciboNominaRepository.sumarNetoNomina(desde, hasta);
        long recibos = reciboNominaRepository.contarRecibos(desde, hasta);

        return new IndicadoresArea(AreaIndicador.RH_NOMINA, List.of(
                ValorIndicador.monetario(
                        "costo_nomina_periodo",
                        "Costo de nomina del periodo",
                        costo != null ? costo : BigDecimal.ZERO),
                ValorIndicador.monetario(
                        "neto_nomina_periodo",
                        "Neto pagado de nomina del periodo",
                        neto != null ? neto : BigDecimal.ZERO),
                ValorIndicador.conteo(
                        "recibos_nomina_periodo",
                        "Recibos de nomina del periodo",
                        BigDecimal.valueOf(recibos))));
    }
}
