package com.dessti.crm.compras.requisicion.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Convertidor JPA entre {@link EstadoRequisicionCompra} y su etiqueta persistida en
 * la columna {@code requisicion_compra.estado} (VARCHAR con CHECK {@code IN
 * ('borrador','enviada','aprobada','rechazada','cancelada')} de la migracion V28).
 *
 * <p>Persiste siempre {@link EstadoRequisicionCompra#valorBd()} (minusculas ASCII),
 * nunca el nombre de la constante Java, de modo que el valor almacenado respete el
 * CHECK de V28 y un arranque con {@code ddl-auto=validate} valide sin conflictos.
 * Sigue el mismo patron que {@code EstadoCotizacionConverter}.</p>
 */
@Converter(autoApply = false)
public class EstadoRequisicionCompraConverter
        implements AttributeConverter<EstadoRequisicionCompra, String> {

    @Override
    public String convertToDatabaseColumn(EstadoRequisicionCompra atributo) {
        return (atributo == null) ? null : atributo.valorBd();
    }

    @Override
    public EstadoRequisicionCompra convertToEntityAttribute(String columna) {
        return (columna == null) ? null : EstadoRequisicionCompra.desdeValorBd(columna);
    }
}
