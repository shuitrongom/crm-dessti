package com.dessti.crm.facturacion.notacredito.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Convertidor JPA entre {@link EstadoNotaCredito} y su etiqueta persistida en la
 * columna {@code nota_credito.estado} (VARCHAR con CHECK {@code IN ('borrador',
 * 'timbrada', 'cancelada')} de la migracion V30). Persiste siempre
 * {@link EstadoNotaCredito#valorBd()} (minusculas ASCII). Sigue el patron de
 * {@code EstadoFacturaConverter}.
 */
@Converter(autoApply = false)
public class EstadoNotaCreditoConverter implements AttributeConverter<EstadoNotaCredito, String> {

    @Override
    public String convertToDatabaseColumn(EstadoNotaCredito atributo) {
        return (atributo == null) ? null : atributo.valorBd();
    }

    @Override
    public EstadoNotaCredito convertToEntityAttribute(String columna) {
        return (columna == null) ? null : EstadoNotaCredito.desdeValorBd(columna);
    }
}
