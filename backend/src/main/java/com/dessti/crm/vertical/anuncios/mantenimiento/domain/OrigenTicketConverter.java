package com.dessti.crm.vertical.anuncios.mantenimiento.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Convertidor JPA entre {@link OrigenTicket} y su etiqueta persistida en la
 * columna {@code ticket_servicio.origen} (VARCHAR con CHECK {@code IN
 * ('manual','preventivo')} de la migracion V27).
 *
 * <p>Persiste siempre {@link OrigenTicket#valorBd()} (minusculas ASCII), nunca el
 * nombre de la constante Java, de modo que el valor almacenado respete el CHECK de
 * V27. Sigue el mismo patron que {@code EstadoOrdenFabricacionConverter}.</p>
 */
@Converter(autoApply = false)
public class OrigenTicketConverter implements AttributeConverter<OrigenTicket, String> {

    @Override
    public String convertToDatabaseColumn(OrigenTicket atributo) {
        return (atributo == null) ? null : atributo.valorBd();
    }

    @Override
    public OrigenTicket convertToEntityAttribute(String columna) {
        return (columna == null) ? null : OrigenTicket.desdeValorBd(columna);
    }
}
