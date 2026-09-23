package com.dessti.crm.notificaciones.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Convertidor JPA entre {@link TipoEventoNotificacion} y su etiqueta persistida en
 * la columna {@code notificacion.evento_origen} (VARCHAR(40) de la migracion V42).
 *
 * <p>Persiste siempre {@link TipoEventoNotificacion#valorBd()} (minusculas ASCII,
 * sin acentos), nunca el nombre de la constante Java, de modo que el valor
 * almacenado sea estable y un arranque con {@code ddl-auto=validate} valide sin
 * conflictos. Sigue el mismo patron que {@code EstadoOrdenFabricacionConverter}.</p>
 */
@Converter(autoApply = false)
public class TipoEventoNotificacionConverter
        implements AttributeConverter<TipoEventoNotificacion, String> {

    @Override
    public String convertToDatabaseColumn(TipoEventoNotificacion atributo) {
        return (atributo == null) ? null : atributo.valorBd();
    }

    @Override
    public TipoEventoNotificacion convertToEntityAttribute(String columna) {
        return (columna == null) ? null : TipoEventoNotificacion.desdeValorBd(columna);
    }
}
