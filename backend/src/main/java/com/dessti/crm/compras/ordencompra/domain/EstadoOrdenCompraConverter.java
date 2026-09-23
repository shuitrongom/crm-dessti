package com.dessti.crm.compras.ordencompra.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Convertidor JPA entre {@link EstadoOrdenCompra} y su etiqueta persistida en la
 * columna {@code orden_compra.estado} (VARCHAR con CHECK {@code IN ('abierta',
 * 'recibida_parcial','recibida_total','cerrada','cancelada')} de la migracion V28).
 *
 * <p>Persiste siempre {@link EstadoOrdenCompra#valorBd()} (minusculas ASCII),
 * nunca el nombre de la constante Java, de modo que el valor almacenado respete el
 * CHECK de V28 y un arranque con {@code ddl-auto=validate} valide sin conflictos.
 * Sigue el mismo patron que {@code EstadoCotizacionConverter} y
 * {@code EstadoOrdenFabricacionConverter}.</p>
 */
@Converter(autoApply = false)
public class EstadoOrdenCompraConverter implements AttributeConverter<EstadoOrdenCompra, String> {

    @Override
    public String convertToDatabaseColumn(EstadoOrdenCompra atributo) {
        return (atributo == null) ? null : atributo.valorBd();
    }

    @Override
    public EstadoOrdenCompra convertToEntityAttribute(String columna) {
        return (columna == null) ? null : EstadoOrdenCompra.desdeValorBd(columna);
    }
}
