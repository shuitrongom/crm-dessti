package com.dessti.crm.calidad.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Convertidor JPA entre {@link OrigenQueja} y su etiqueta persistida en la columna
 * {@code queja_cliente.origen} (VARCHAR con CHECK
 * {@code IN ('portal','social','correo','telefono','otro')} de la migracion V47).
 * Persiste siempre {@link OrigenQueja#valorBd()} (minusculas ASCII).
 */
@Converter(autoApply = false)
public class OrigenQuejaConverter implements AttributeConverter<OrigenQueja, String> {

    @Override
    public String convertToDatabaseColumn(OrigenQueja atributo) {
        return (atributo == null) ? null : atributo.valorBd();
    }

    @Override
    public OrigenQueja convertToEntityAttribute(String columna) {
        return (columna == null) ? null : OrigenQueja.desdeValorBd(columna);
    }
}
