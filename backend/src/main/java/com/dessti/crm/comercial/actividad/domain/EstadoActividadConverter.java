package com.dessti.crm.comercial.actividad.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Convertidor JPA entre {@link EstadoActividad} y su etiqueta persistida en la
 * columna {@code actividad_comercial.estado} (VARCHAR con CHECK
 * {@code IN ('pendiente','completada','cancelada')} de V79).
 *
 * <p>Persiste siempre {@link EstadoActividad#valorBd()} (minusculas ASCII),
 * nunca el nombre de la constante Java, de modo que el valor almacenado respete
 * el CHECK de V79 y un arranque con {@code ddl-auto=validate} valide sin
 * conflictos. Sigue el mismo patron que {@code EtapaOportunidadConverter}.</p>
 */
@Converter(autoApply = false)
public class EstadoActividadConverter implements AttributeConverter<EstadoActividad, String> {

    @Override
    public String convertToDatabaseColumn(EstadoActividad atributo) {
        return (atributo == null) ? null : atributo.valorBd();
    }

    @Override
    public EstadoActividad convertToEntityAttribute(String columna) {
        return (columna == null) ? null : EstadoActividad.desdeValorBd(columna);
    }
}
