package com.dessti.crm.activosfijos.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Convertidor JPA entre {@link EstadoActivoFijo} y su etiqueta persistida en la
 * columna {@code activo_fijo.estado} (VARCHAR con CHECK {@code IN ('activo','baja')}
 * de la migracion V37).
 *
 * <p>Persiste siempre {@link EstadoActivoFijo#valorBd()} (minusculas ASCII), nunca
 * el nombre de la constante Java, de modo que el valor almacenado respete el CHECK
 * de V37 y un arranque con {@code ddl-auto=validate} valide sin conflictos. Sigue
 * el mismo patron que {@code EstadoOrdenFabricacionConverter}.</p>
 */
@Converter(autoApply = false)
public class EstadoActivoFijoConverter
        implements AttributeConverter<EstadoActivoFijo, String> {

    @Override
    public String convertToDatabaseColumn(EstadoActivoFijo atributo) {
        return (atributo == null) ? null : atributo.valorBd();
    }

    @Override
    public EstadoActivoFijo convertToEntityAttribute(String columna) {
        return (columna == null) ? null : EstadoActivoFijo.desdeValorBd(columna);
    }
}
