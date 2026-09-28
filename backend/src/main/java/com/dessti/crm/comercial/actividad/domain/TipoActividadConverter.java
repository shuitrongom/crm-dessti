package com.dessti.crm.comercial.actividad.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Convertidor JPA entre {@link TipoActividad} y su etiqueta persistida en la
 * columna {@code actividad_comercial.tipo} (VARCHAR con CHECK
 * {@code IN ('llamada','correo','reunion','tarea','nota')} de V79).
 *
 * <p>Persiste siempre {@link TipoActividad#valorBd()} (minusculas ASCII), nunca
 * el nombre de la constante Java, de modo que el valor almacenado respete el
 * CHECK de V79 y un arranque con {@code ddl-auto=validate} valide sin conflictos.
 * Sigue el mismo patron que {@code EtapaOportunidadConverter}.</p>
 */
@Converter(autoApply = false)
public class TipoActividadConverter implements AttributeConverter<TipoActividad, String> {

    @Override
    public String convertToDatabaseColumn(TipoActividad atributo) {
        return (atributo == null) ? null : atributo.valorBd();
    }

    @Override
    public TipoActividad convertToEntityAttribute(String columna) {
        return (columna == null) ? null : TipoActividad.desdeValorBd(columna);
    }
}
