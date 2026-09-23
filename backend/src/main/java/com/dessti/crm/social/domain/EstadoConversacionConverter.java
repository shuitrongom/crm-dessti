package com.dessti.crm.social.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Convertidor JPA entre {@link EstadoConversacion} y su etiqueta persistida en la
 * columna {@code conversacion.estado} (VARCHAR con CHECK
 * {@code IN ('abierta','asignada','cerrada')} de la migracion V41). Persiste
 * siempre {@link EstadoConversacion#valorBd()} (minusculas ASCII).
 */
@Converter(autoApply = false)
public class EstadoConversacionConverter implements AttributeConverter<EstadoConversacion, String> {

    @Override
    public String convertToDatabaseColumn(EstadoConversacion atributo) {
        return (atributo == null) ? null : atributo.valorBd();
    }

    @Override
    public EstadoConversacion convertToEntityAttribute(String columna) {
        return (columna == null) ? null : EstadoConversacion.desdeValorBd(columna);
    }
}
