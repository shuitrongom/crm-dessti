package com.dessti.crm.calidad.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Convertidor JPA entre {@link Impacto} y su etiqueta persistida en la columna
 * {@code riesgo.impacto} (VARCHAR con CHECK {@code IN ('bajo','medio','alto')} de la
 * migracion V47). Persiste siempre {@link Impacto#valorBd()}.
 */
@Converter(autoApply = false)
public class ImpactoConverter implements AttributeConverter<Impacto, String> {

    @Override
    public String convertToDatabaseColumn(Impacto atributo) {
        return (atributo == null) ? null : atributo.valorBd();
    }

    @Override
    public Impacto convertToEntityAttribute(String columna) {
        return (columna == null) ? null : Impacto.desdeValorBd(columna);
    }
}
