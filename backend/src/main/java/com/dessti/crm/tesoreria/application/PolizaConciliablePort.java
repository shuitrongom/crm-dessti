package com.dessti.crm.tesoreria.application;

import java.time.LocalDate;
import java.util.List;

import com.dessti.crm.tesoreria.domain.CandidatoConciliacion;

/**
 * Puerto de <strong>solo lectura</strong> que el modulo de tesoreria consume para
 * obtener las partidas contables candidatas (Poliza_Contable y, opcionalmente, Pago)
 * con las que emparejar los Movimiento_Bancario durante la conciliacion (Req 43.3).
 *
 * <h2>Acoplamiento aciclico</h2>
 * <p>El puerto se declara en tesoreria y lo implementa un adaptador
 * ({@code PolizaConciliableAdapter}) en el modulo de contabilidad, que consulta el
 * {@code PolizaContableRepository} (y, si aplica, los pagos) acotado al tenant
 * vigente (filtro de Hibernate + RLS de V33). Asi tesoreria depende de una
 * abstraccion propia y no de las entidades de contabilidad, manteniendo la
 * dependencia en un solo sentido (contabilidad -> tesoreria) y sin ciclos.</p>
 *
 * <h2>Fuentes de emparejamiento</h2>
 * <p>La implementacion devuelve como candidatos las Polizas_Contables cuya magnitud
 * (total de cargos) y fecha caen dentro de la ventana consultada; opcionalmente
 * puede incluir Pagos. El emparejamiento fino (monto exacto + fecha dentro de
 * tolerancia + referencia) lo decide la regla PURA
 * {@link com.dessti.crm.tesoreria.domain.ReglasConciliacion} sobre estos
 * candidatos.</p>
 */
public interface PolizaConciliablePort {

    /**
     * Busca las partidas contables candidatas del tenant vigente cuya fecha cae en la
     * ventana {@code [desde, hasta]} (ya ampliada por la tolerancia de dias por la
     * aplicacion) para emparejarlas con los movimientos bancarios (Req 43.3).
     *
     * @param desde fecha minima (inclusiva) de la ventana de busqueda.
     * @param hasta fecha maxima (inclusiva) de la ventana de busqueda.
     * @return la lista de candidatos (Poliza_Contable/Pago); nunca {@code null}.
     */
    List<CandidatoConciliacion> buscarCandidatos(LocalDate desde, LocalDate hasta);
}
