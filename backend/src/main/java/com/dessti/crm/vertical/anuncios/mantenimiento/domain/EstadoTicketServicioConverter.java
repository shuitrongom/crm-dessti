package com.dessti.crm.vertical.anuncios.mantenimiento.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Convertidor JPA entre {@link EstadoTicketServicio} y su etiqueta persistida en
 * la columna {@code ticket_servicio.estado} (VARCHAR con CHECK {@code IN
 * ('abierto','asignado','en_proceso','resuelto','cerrado')} de la migracion V27).
 *
 * <p>Persiste siempre {@link EstadoTicketServicio#valorBd()} (minusculas ASCII;
 * {@code en_proceso} sin acento), nunca el nombre de la constante Java, de modo
 * que el valor almacenado respete el CHECK de V27 y un arranque con
 * {@code ddl-auto=validate} valide sin conflictos. Sigue el mismo patron que
 * {@code EstadoOrdenFabricacionConverter}.</p>
 */
@Converter(autoApply = false)
public class EstadoTicketServicioConverter
        implements AttributeConverter<EstadoTicketServicio, String> {

    @Override
    public String convertToDatabaseColumn(EstadoTicketServicio atributo) {
        return (atributo == null) ? null : atributo.valorBd();
    }

    @Override
    public EstadoTicketServicio convertToEntityAttribute(String columna) {
        return (columna == null) ? null : EstadoTicketServicio.desdeValorBd(columna);
    }
}
