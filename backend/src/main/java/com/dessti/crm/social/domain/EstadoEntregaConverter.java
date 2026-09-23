package com.dessti.crm.social.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Convertidor JPA entre {@link EstadoEntrega} y su etiqueta persistida en la
 * columna {@code mensaje_social.estado_entrega} (VARCHAR NULL con CHECK
 * {@code IN ('enviado','entregado','leido','fallido')} de la migracion V41).
 * Persiste siempre {@link EstadoEntrega#valorBd()} (minusculas ASCII); admite
 * {@code null} para los mensajes entrantes.
 */
@Converter(autoApply = false)
public class EstadoEntregaConverter implements AttributeConverter<EstadoEntrega, String> {

    @Override
    public String convertToDatabaseColumn(EstadoEntrega atributo) {
        return (atributo == null) ? null : atributo.valorBd();
    }

    @Override
    public EstadoEntrega convertToEntityAttribute(String columna) {
        return (columna == null) ? null : EstadoEntrega.desdeValorBd(columna);
    }
}
