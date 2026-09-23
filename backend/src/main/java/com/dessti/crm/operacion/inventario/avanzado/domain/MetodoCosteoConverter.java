package com.dessti.crm.operacion.inventario.avanzado.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Convertidor JPA entre {@link MetodoCosteo} y su etiqueta persistida en la columna
 * {@code config_inventario_material.metodo_costeo} (VARCHAR con CHECK {@code IN
 * ('promedio','peps')} de la migracion V26).
 *
 * <p>Persiste siempre {@link MetodoCosteo#valorBd()} (minusculas ASCII), nunca el
 * nombre de la constante Java, de modo que el valor almacenado respete el CHECK de
 * V26 y un arranque con {@code ddl-auto=validate} valide sin conflictos. Sigue el
 * mismo patron que {@code TipoMovimientoInventarioConverter}.</p>
 */
@Converter(autoApply = false)
public class MetodoCosteoConverter implements AttributeConverter<MetodoCosteo, String> {

    @Override
    public String convertToDatabaseColumn(MetodoCosteo atributo) {
        return (atributo == null) ? null : atributo.valorBd();
    }

    @Override
    public MetodoCosteo convertToEntityAttribute(String columna) {
        return (columna == null) ? null : MetodoCosteo.desdeValorBd(columna);
    }
}
