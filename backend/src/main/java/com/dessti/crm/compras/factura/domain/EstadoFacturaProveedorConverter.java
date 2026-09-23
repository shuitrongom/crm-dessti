package com.dessti.crm.compras.factura.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Convertidor JPA entre {@link EstadoFacturaProveedor} y su etiqueta persistida en
 * la columna {@code factura_proveedor.estado} (VARCHAR con CHECK {@code IN
 * ('registrada','conciliada','discrepancia','pagada')} de la migracion V29).
 *
 * <p>Persiste siempre {@link EstadoFacturaProveedor#valorBd()} (minusculas ASCII),
 * nunca el nombre de la constante Java, de modo que el valor almacenado respete el
 * CHECK de V29 y un arranque con {@code ddl-auto=validate} valide sin conflictos.
 * Sigue el mismo patron que {@code EstadoOrdenCompraConverter}.</p>
 */
@Converter(autoApply = false)
public class EstadoFacturaProveedorConverter
        implements AttributeConverter<EstadoFacturaProveedor, String> {

    @Override
    public String convertToDatabaseColumn(EstadoFacturaProveedor atributo) {
        return (atributo == null) ? null : atributo.valorBd();
    }

    @Override
    public EstadoFacturaProveedor convertToEntityAttribute(String columna) {
        return (columna == null) ? null : EstadoFacturaProveedor.desdeValorBd(columna);
    }
}
