package com.dessti.crm.calidad.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Convertidor JPA entre {@link Probabilidad} y su etiqueta persistida en la columna
 * {@code riesgo.probabilidad} (VARCHAR con CHECK {@code IN ('baja','media','alta')} de
 * la migracion V47). Persiste siempre {@link Probabilidad#valorBd()}.
 */
@Converter(autoApply = false)
public class ProbabilidadConverter implements AttributeConverter<Probabilidad, String> {

    @Override
    public String convertToDatabaseColumn(Probabilidad atributo) {
        return (atributo == null) ? null : atributo.valorBd();
    }

    @Override
    public Probabilidad convertToEntityAttribute(String columna) {
        return (columna == null) ? null : Probabilidad.desdeValorBd(columna);
    }
}
