package com.dessti.crm.comercial.cotizacion.application;

/**
 * Puerto de salida que asigna el <strong>consecutivo de folio</strong> de una
 * Cotizacion por (tenant, anio) de forma segura ante concurrencia (V60, Req 6).
 *
 * <p>Se introduce un puerto propio del submodulo de Cotizaciones para
 * <strong>desacoplar</strong> la aplicacion del mecanismo de persistencia del
 * contador: la aplicacion solo necesita el siguiente numero para componer el
 * folio {@code COT-<anio>-<nnnn>}, no conocer la tabla auxiliar
 * {@code cotizacion_folio_seq} ni su UPSERT. El adaptador
 * {@code FolioCotizacionAdapter} implementa este puerto con un UPSERT atomico.</p>
 */
public interface FolioCotizacionPort {

    /**
     * Reserva y devuelve el siguiente consecutivo de folio para el tenant vigente
     * y el anio indicado, incrementando el contador de forma atomica (V60). Cada
     * invocacion devuelve un valor estrictamente creciente y distinto para el
     * mismo (tenant, anio), incluso bajo concurrencia.
     *
     * @param anio anio del folio (p. ej. 2026).
     * @return el siguiente consecutivo (&ge; 1) para ese (tenant, anio).
     */
    int siguienteConsecutivo(int anio);
}
