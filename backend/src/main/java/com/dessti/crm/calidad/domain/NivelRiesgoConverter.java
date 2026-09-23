package com.dessti.crm.calidad.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Convertidor JPA entre {@link NivelRiesgo} y su etiqueta persistida en la columna
 * {@code riesgo.nivel_derivado} (VARCHAR con CHECK
 * {@code IN ('bajo','medio','alto','critico')} de la migracion V47). Persiste siempre
 * {@link NivelRiesgo#valorBd()}.
 */
@Converter(autoApply = false)
public class NivelRiesgoConverter implements AttributeConverter<NivelRiesgo, String> {

    @Override
    public String convertToDatabaseColumn(NivelRiesgo atributo) {
        return (atributo == null) ? null : atributo.valorBd();
    }

    @Override
    public NivelRiesgo convertToEntityAttribute(String columna) {
        return (columna == null) ? null : NivelRiesgo.desdeValorBd(columna);
    }
}
