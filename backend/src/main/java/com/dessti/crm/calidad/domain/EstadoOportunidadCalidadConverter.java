package com.dessti.crm.calidad.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Convertidor JPA entre {@link EstadoOportunidadCalidad} y su etiqueta persistida en la
 * columna {@code oportunidad_calidad.estado} (VARCHAR con CHECK
 * {@code IN ('identificada','en_evaluacion','en_ejecucion','realizada','descartada')}
 * de la migracion V47). Persiste siempre {@link EstadoOportunidadCalidad#valorBd()}.
 */
@Converter(autoApply = false)
public class EstadoOportunidadCalidadConverter
        implements AttributeConverter<EstadoOportunidadCalidad, String> {

    @Override
    public String convertToDatabaseColumn(EstadoOportunidadCalidad atributo) {
        return (atributo == null) ? null : atributo.valorBd();
    }

    @Override
    public EstadoOportunidadCalidad convertToEntityAttribute(String columna) {
        return (columna == null) ? null : EstadoOportunidadCalidad.desdeValorBd(columna);
    }
}
