package com.dessti.crm.operacion.inventario.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Convertidor JPA entre {@link TipoMovimientoInventario} y su etiqueta persistida
 * en la columna {@code movimiento_inventario.tipo} (VARCHAR con CHECK {@code IN
 * ('entrada','salida','ajuste')} de la migracion V18).
 *
 * <p>Persiste siempre {@link TipoMovimientoInventario#valorBd()} (minusculas ASCII),
 * nunca el nombre de la constante Java, de modo que el valor almacenado respete el
 * CHECK de V18 y un arranque con {@code ddl-auto=validate} valide sin conflictos.
 * Sigue el mismo patron que {@code EstadoOrdenFabricacionConverter}.</p>
 */
@Converter(autoApply = false)
public class TipoMovimientoInventarioConverter
        implements AttributeConverter<TipoMovimientoInventario, String> {

    @Override
    public String convertToDatabaseColumn(TipoMovimientoInventario atributo) {
        return (atributo == null) ? null : atributo.valorBd();
    }

    @Override
    public TipoMovimientoInventario convertToEntityAttribute(String columna) {
        return (columna == null) ? null : TipoMovimientoInventario.desdeValorBd(columna);
    }
}
