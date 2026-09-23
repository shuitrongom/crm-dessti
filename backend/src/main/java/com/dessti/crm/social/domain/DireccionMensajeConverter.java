package com.dessti.crm.social.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Convertidor JPA entre {@link DireccionMensaje} y su etiqueta persistida en la
 * columna {@code mensaje_social.direccion} (VARCHAR con CHECK
 * {@code IN ('entrante','saliente')} de la migracion V41). Persiste siempre
 * {@link DireccionMensaje#valorBd()} (minusculas ASCII).
 */
@Converter(autoApply = false)
public class DireccionMensajeConverter implements AttributeConverter<DireccionMensaje, String> {

    @Override
    public String convertToDatabaseColumn(DireccionMensaje atributo) {
        return (atributo == null) ? null : atributo.valorBd();
    }

    @Override
    public DireccionMensaje convertToEntityAttribute(String columna) {
        return (columna == null) ? null : DireccionMensaje.desdeValorBd(columna);
    }
}
