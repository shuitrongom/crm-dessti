package com.dessti.crm.calidad.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Convertidor JPA entre {@link EstadoRiesgo} y su etiqueta persistida en la columna
 * {@code riesgo.estado} (VARCHAR con CHECK
 * {@code IN ('identificado','en_tratamiento','mitigado','aceptado')} de la migracion
 * V47). Persiste siempre {@link EstadoRiesgo#valorBd()}.
 */
@Converter(autoApply = false)
public class EstadoRiesgoConverter implements AttributeConverter<EstadoRiesgo, String> {

    @Override
    public String convertToDatabaseColumn(EstadoRiesgo atributo) {
        return (atributo == null) ? null : atributo.valorBd();
    }

    @Override
    public EstadoRiesgo convertToEntityAttribute(String columna) {
        return (columna == null) ? null : EstadoRiesgo.desdeValorBd(columna);
    }
}
