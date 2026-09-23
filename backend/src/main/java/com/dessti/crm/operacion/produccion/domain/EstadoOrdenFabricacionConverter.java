package com.dessti.crm.operacion.produccion.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Convertidor JPA entre {@link EstadoOrdenFabricacion} y su etiqueta persistida en
 * la columna {@code orden_fabricacion.estado} (VARCHAR con CHECK {@code IN
 * ('pendiente','en_produccion','terminada','cancelada')} de la migracion V17).
 *
 * <p>Persiste siempre {@link EstadoOrdenFabricacion#valorBd()} (minusculas ASCII;
 * {@code en_produccion} sin acento), nunca el nombre de la constante Java, de modo
 * que el valor almacenado respete el CHECK de V17 y un arranque con
 * {@code ddl-auto=validate} valide sin conflictos. Sigue el mismo patron que
 * {@code EstadoCotizacionConverter} y {@code EstadoPruebaDisenoConverter}.</p>
 */
@Converter(autoApply = false)
public class EstadoOrdenFabricacionConverter
        implements AttributeConverter<EstadoOrdenFabricacion, String> {

    @Override
    public String convertToDatabaseColumn(EstadoOrdenFabricacion atributo) {
        return (atributo == null) ? null : atributo.valorBd();
    }

    @Override
    public EstadoOrdenFabricacion convertToEntityAttribute(String columna) {
        return (columna == null) ? null : EstadoOrdenFabricacion.desdeValorBd(columna);
    }
}
