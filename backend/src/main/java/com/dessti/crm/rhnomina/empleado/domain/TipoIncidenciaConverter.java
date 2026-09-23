package com.dessti.crm.rhnomina.empleado.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Convertidor JPA entre {@link TipoIncidencia} y su etiqueta persistida en la
 * columna {@code incidencia.tipo} (VARCHAR con CHECK {@code IN ('asistencia',
 * 'falta','permiso','incapacidad','tiempo_extra')} de la migracion V32).
 *
 * <p>Persiste siempre {@link TipoIncidencia#valorBd()} (minusculas ASCII), nunca
 * el nombre de la constante Java, de modo que el valor almacenado respete el
 * CHECK de V32 y un arranque con {@code ddl-auto=validate} valide sin conflictos.</p>
 */
@Converter(autoApply = false)
public class TipoIncidenciaConverter implements AttributeConverter<TipoIncidencia, String> {

    @Override
    public String convertToDatabaseColumn(TipoIncidencia atributo) {
        return (atributo == null) ? null : atributo.valorBd();
    }

    @Override
    public TipoIncidencia convertToEntityAttribute(String columna) {
        return (columna == null) ? null : TipoIncidencia.desdeValorBd(columna);
    }
}
