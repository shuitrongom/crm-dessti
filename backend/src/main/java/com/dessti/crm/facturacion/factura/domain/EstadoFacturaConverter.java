package com.dessti.crm.facturacion.factura.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Convertidor JPA entre {@link EstadoFactura} y su etiqueta persistida en la
 * columna {@code factura.estado} (VARCHAR con CHECK {@code IN ('borrador',
 * 'timbrada', 'cancelacion_en_proceso', 'cancelada')} de la migracion V30).
 *
 * <p>Persiste siempre {@link EstadoFactura#valorBd()} (minusculas ASCII), nunca el
 * nombre de la constante Java, de modo que el valor almacenado respete el CHECK de
 * V30 y un arranque con {@code ddl-auto=validate} valide sin conflictos. Sigue el
 * mismo patron que {@code EstadoCotizacionConverter}.</p>
 */
@Converter(autoApply = false)
public class EstadoFacturaConverter implements AttributeConverter<EstadoFactura, String> {

    @Override
    public String convertToDatabaseColumn(EstadoFactura atributo) {
        return (atributo == null) ? null : atributo.valorBd();
    }

    @Override
    public EstadoFactura convertToEntityAttribute(String columna) {
        return (columna == null) ? null : EstadoFactura.desdeValorBd(columna);
    }
}
