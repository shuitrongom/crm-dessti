package com.dessti.crm.calidad.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Convertidor JPA entre {@link EstadoCambioSgc} y su etiqueta persistida en la columna
 * {@code cambio_sgc.estado} (VARCHAR con CHECK
 * {@code IN ('propuesto','aprobado','implementado','rechazado')} de la migracion V47).
 * Persiste siempre {@link EstadoCambioSgc#valorBd()}.
 */
@Converter(autoApply = false)
public class EstadoCambioSgcConverter implements AttributeConverter<EstadoCambioSgc, String> {

    @Override
    public String convertToDatabaseColumn(EstadoCambioSgc atributo) {
        return (atributo == null) ? null : atributo.valorBd();
    }

    @Override
    public EstadoCambioSgc convertToEntityAttribute(String columna) {
        return (columna == null) ? null : EstadoCambioSgc.desdeValorBd(columna);
    }
}
