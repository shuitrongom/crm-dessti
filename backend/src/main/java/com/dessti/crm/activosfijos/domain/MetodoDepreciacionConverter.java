package com.dessti.crm.activosfijos.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Convertidor JPA entre {@link MetodoDepreciacion} y su etiqueta persistida en la
 * columna {@code activo_fijo.metodo_depreciacion} (VARCHAR con CHECK {@code IN
 * ('linea_recta','saldos_decrecientes')} de la migracion V37).
 *
 * <p>Persiste siempre {@link MetodoDepreciacion#valorBd()} (minusculas ASCII),
 * nunca el nombre de la constante Java, de modo que el valor almacenado respete el
 * CHECK de V37 y un arranque con {@code ddl-auto=validate} valide sin conflictos.
 * Sigue el mismo patron que {@code EstadoOrdenFabricacionConverter}.</p>
 */
@Converter(autoApply = false)
public class MetodoDepreciacionConverter
        implements AttributeConverter<MetodoDepreciacion, String> {

    @Override
    public String convertToDatabaseColumn(MetodoDepreciacion atributo) {
        return (atributo == null) ? null : atributo.valorBd();
    }

    @Override
    public MetodoDepreciacion convertToEntityAttribute(String columna) {
        return (columna == null) ? null : MetodoDepreciacion.desdeValorBd(columna);
    }
}
