package com.dessti.crm.operacion.inventario.avanzado.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Convertidor JPA entre {@link TipoMovimientoAlmacen} y su etiqueta persistida en la
 * columna {@code movimiento_almacen.tipo} (VARCHAR con CHECK de la migracion V26).
 *
 * <p>Persiste siempre {@link TipoMovimientoAlmacen#valorBd()} (minusculas ASCII), nunca
 * el nombre de la constante Java, de modo que el valor almacenado respete el CHECK de
 * V26 y un arranque con {@code ddl-auto=validate} valide sin conflictos. Sigue el mismo
 * patron que {@code TipoMovimientoInventarioConverter}.</p>
 */
@Converter(autoApply = false)
public class TipoMovimientoAlmacenConverter
        implements AttributeConverter<TipoMovimientoAlmacen, String> {

    @Override
    public String convertToDatabaseColumn(TipoMovimientoAlmacen atributo) {
        return (atributo == null) ? null : atributo.valorBd();
    }

    @Override
    public TipoMovimientoAlmacen convertToEntityAttribute(String columna) {
        return (columna == null) ? null : TipoMovimientoAlmacen.desdeValorBd(columna);
    }
}
