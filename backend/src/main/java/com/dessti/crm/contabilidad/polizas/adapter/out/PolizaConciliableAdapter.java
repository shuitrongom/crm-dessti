package com.dessti.crm.contabilidad.polizas.adapter.out;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.dessti.crm.contabilidad.polizas.adapter.out.persistence.PolizaContableRepository;
import com.dessti.crm.contabilidad.polizas.domain.PolizaContable;
import com.dessti.crm.tesoreria.application.PolizaConciliablePort;
import com.dessti.crm.tesoreria.domain.CandidatoConciliacion;

/**
 * Adaptador de solo lectura que implementa el {@link PolizaConciliablePort} del
 * modulo de tesoreria consultando el {@link PolizaContableRepository} del modulo de
 * contabilidad (Req 43.3).
 *
 * <h2>Direccion de la dependencia (acoplamiento aciclico)</h2>
 * <p>El puerto vive en tesoreria; este adaptador vive en contabilidad y lo
 * implementa. Asi la dependencia es unidireccional (contabilidad -> tesoreria), sin
 * ciclos: tesoreria nunca conoce las entidades de contabilidad, solo su propia
 * abstraccion {@link CandidatoConciliacion}.</p>
 *
 * <h2>Mapeo a candidato (Req 43.3)</h2>
 * <p>Cada Poliza_Contable de la ventana {@code [desde, hasta]} se proyecta a un
 * {@link CandidatoConciliacion} con:</p>
 * <ul>
 *   <li>{@code monto} = total de cargos de la poliza (magnitud; el movimiento
 *       bancario compara por valor absoluto),</li>
 *   <li>{@code fecha} = fecha contable de la poliza,</li>
 *   <li>{@code referencia} = identificador del recurso de origen
 *       ({@code origenId}) cuando existe; el emparejamiento fino (monto exacto +
 *       fecha dentro de tolerancia + referencia) lo decide la regla PURA
 *       {@code ReglasConciliacion}.</li>
 * </ul>
 *
 * <p><strong>Fuentes de emparejamiento:</strong> esta implementacion aporta como
 * candidatos las Polizas_Contables, suficientes para la conciliacion y la
 * Property 18. El emparejamiento adicional contra Pago_Cliente es una ampliacion
 * futura que anadiria candidatos {@link CandidatoConciliacion#dePago} sin cambiar el
 * contrato del puerto.</p>
 *
 * <h2>Aislamiento multi-tenant (Req 23)</h2>
 * <p>La consulta se ejecuta sobre {@code PolizaContableRepository}, cuyo agregado es
 * tenant-scoped: el filtro de Hibernate (Capa 1) y la RLS de V33 (Capa 2) acotan los
 * resultados al tenant vigente.</p>
 */
@Component
public class PolizaConciliableAdapter implements PolizaConciliablePort {

    /**
     * Tope de candidatos por ventana para acotar la consulta. Una conciliacion de un
     * periodo mensual rara vez supera este numero de polizas; el emparejamiento fino
     * es en memoria via {@code ReglasConciliacion}.
     */
    private static final int MAX_CANDIDATOS = 1000;

    private final PolizaContableRepository polizaContableRepository;

    public PolizaConciliableAdapter(PolizaContableRepository polizaContableRepository) {
        this.polizaContableRepository = polizaContableRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<CandidatoConciliacion> buscarCandidatos(LocalDate desde, LocalDate hasta) {
        return polizaContableRepository
                .buscarConFiltros(desde, hasta, null, PageRequest.of(0, MAX_CANDIDATOS))
                .stream()
                .map(PolizaConciliableAdapter::aCandidato)
                .toList();
    }

    private static CandidatoConciliacion aCandidato(PolizaContable poliza) {
        String referencia = (poliza.getOrigenId() != null)
                ? poliza.getOrigenId().toString()
                : null;
        return CandidatoConciliacion.dePoliza(
                poliza.getId(), poliza.getTotalCargos(), poliza.getFecha(), referencia);
    }
}
