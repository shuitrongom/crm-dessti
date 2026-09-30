package com.dessti.crm.operacion.inventario.avanzado.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Convertidor JPA entre {@link TipoAlertaInventario} y su etiqueta persistida en la
 * columna {@code alerta_inventario.tipo} (VARCHAR con CHECK de la migracion V88).
 *
 * <p>Persiste siempre {@link TipoAlertaInventario#valorBd()} (minusculas ASCII), nunca el
 * nombre de la constante Java, de modo que el valor almacenado respete el CHECK de V88 y un
 * arranque con {@code ddl-auto=validate} valide sin conflictos. Sigue el mismo patron que
 * {@code TipoMovimientoAlmacenConverter}.</p>
 */
@Converter(autoApply = false)
public class TipoAlertaInventarioConverter
        implements AttributeConverter<TipoAlertaInventario, String> {

    @Override
    public String convertToDatabaseColumn(TipoAlertaInventario atributo) {
        return (atributo == null) ? null : atributo.valorBd();
    }

    @Override
    public TipoAlertaInventario convertToEntityAttribute(String columna) {
        return (columna == null) ? null : TipoAlertaInventario.desdeValorBd(columna);
    }
}
