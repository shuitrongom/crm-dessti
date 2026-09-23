package com.dessti.crm.calidad.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Convertidor JPA entre {@link EstadoQuejaCliente} y su etiqueta persistida en la
 * columna {@code queja_cliente.estado} (VARCHAR con CHECK
 * {@code IN ('registrada','vinculada','atendida')} de la migracion V47). Persiste
 * siempre {@link EstadoQuejaCliente#valorBd()} (minusculas ASCII).
 */
@Converter(autoApply = false)
public class EstadoQuejaClienteConverter
        implements AttributeConverter<EstadoQuejaCliente, String> {

    @Override
    public String convertToDatabaseColumn(EstadoQuejaCliente atributo) {
        return (atributo == null) ? null : atributo.valorBd();
    }

    @Override
    public EstadoQuejaCliente convertToEntityAttribute(String columna) {
        return (columna == null) ? null : EstadoQuejaCliente.desdeValorBd(columna);
    }
}
