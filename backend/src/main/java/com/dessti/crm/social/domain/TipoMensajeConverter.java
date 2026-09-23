package com.dessti.crm.social.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Convertidor JPA entre {@link TipoMensaje} y su etiqueta persistida en la columna
 * {@code mensaje_social.tipo} (VARCHAR con CHECK
 * {@code IN ('texto','plantilla','interactivo')} de la migracion V41). Persiste
 * siempre {@link TipoMensaje#valorBd()} (minusculas ASCII).
 */
@Converter(autoApply = false)
public class TipoMensajeConverter implements AttributeConverter<TipoMensaje, String> {

    @Override
    public String convertToDatabaseColumn(TipoMensaje atributo) {
        return (atributo == null) ? null : atributo.valorBd();
    }

    @Override
    public TipoMensaje convertToEntityAttribute(String columna) {
        return (columna == null) ? null : TipoMensaje.desdeValorBd(columna);
    }
}
