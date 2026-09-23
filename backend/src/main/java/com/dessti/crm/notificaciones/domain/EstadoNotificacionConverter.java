package com.dessti.crm.notificaciones.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Convertidor JPA entre {@link EstadoNotificacion} y su etiqueta persistida en la
 * columna {@code notificacion.estado} (VARCHAR con CHECK {@code IN ('pendiente',
 * 'enviada','fallida','omitida')} de la migracion V42).
 *
 * <p>Persiste siempre {@link EstadoNotificacion#valorBd()} (minusculas ASCII), nunca
 * el nombre de la constante Java, de modo que el valor almacenado respete el CHECK
 * de V42 y un arranque con {@code ddl-auto=validate} valide sin conflictos. Sigue el
 * mismo patron que {@code EstadoOrdenFabricacionConverter}.</p>
 */
@Converter(autoApply = false)
public class EstadoNotificacionConverter implements AttributeConverter<EstadoNotificacion, String> {

    @Override
    public String convertToDatabaseColumn(EstadoNotificacion atributo) {
        return (atributo == null) ? null : atributo.valorBd();
    }

    @Override
    public EstadoNotificacion convertToEntityAttribute(String columna) {
        return (columna == null) ? null : EstadoNotificacion.desdeValorBd(columna);
    }
}
