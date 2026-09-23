package com.dessti.crm.comercial.cotizacion.adapter.out.persistence;

import java.util.UUID;

import org.springframework.stereotype.Component;

import com.dessti.crm.comercial.cotizacion.application.FolioCotizacionPort;
import com.dessti.crm.platform.tenant.TenantContext;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * Adaptador de salida que implementa {@link FolioCotizacionPort} sobre la tabla
 * auxiliar {@code cotizacion_folio_seq} (V60) mediante un <strong>UPSERT
 * atomico</strong>, garantizando folios secuenciales por (tenant, anio) sin
 * condiciones de carrera (Req 6).
 *
 * <h2>Correccion ante concurrencia</h2>
 * <p>El consecutivo NO se calcula contando filas de {@code cotizacion} (patron
 * propenso a duplicados bajo concurrencia), sino con:</p>
 * <pre>
 *   INSERT INTO cotizacion_folio_seq (tenant_id, anio, ultimo) VALUES (?, ?, 1)
 *   ON CONFLICT (tenant_id, anio) DO UPDATE SET ultimo = cotizacion_folio_seq.ultimo + 1
 *   RETURNING ultimo;
 * </pre>
 * <p>que incrementa y devuelve el siguiente numero en una sola sentencia atomica.
 * El indice unico parcial {@code uq_cotizacion_tenant_folio} sobre
 * {@code cotizacion} actua como red de seguridad adicional.</p>
 *
 * <h2>Tenant explicito</h2>
 * <p>{@code cotizacion_folio_seq} es una tabla de plataforma SIN RLS (V60); el
 * {@code tenant_id} se toma del {@link TenantContext} vigente (nunca de la
 * peticion, Req 23.4) y se fija explicitamente en la sentencia, de modo que cada
 * tenant opera sobre su propio contador.</p>
 */
@Component("cotizacionFolioAdapter")
public class FolioCotizacionAdapter implements FolioCotizacionPort {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public int siguienteConsecutivo(int anio) {
        UUID tenantId = TenantContext.require();
        // UPSERT atomico: inserta el contador en 1 la primera vez del anio, o lo
        // incrementa en +1 si ya existe, devolviendo el valor resultante.
        Object resultado = entityManager.createNativeQuery("""
                        INSERT INTO cotizacion_folio_seq (tenant_id, anio, ultimo)
                        VALUES (:tenantId, :anio, 1)
                        ON CONFLICT (tenant_id, anio)
                        DO UPDATE SET ultimo = cotizacion_folio_seq.ultimo + 1
                        RETURNING ultimo
                        """)
                .setParameter("tenantId", tenantId)
                .setParameter("anio", anio)
                .getSingleResult();
        return ((Number) resultado).intValue();
    }
}
