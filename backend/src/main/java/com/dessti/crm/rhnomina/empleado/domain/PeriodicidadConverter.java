package com.dessti.crm.rhnomina.empleado.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Convertidor JPA entre {@link Periodicidad} y su etiqueta persistida en la
 * columna {@code contrato_laboral.periodicidad} (VARCHAR con CHECK {@code IN
 * ('semanal','quincenal','mensual')} de la migracion V32).
 *
 * <p>Persiste siempre {@link Periodicidad#valorBd()} (minusculas ASCII), nunca el
 * nombre de la constante Java, de modo que el valor almacenado respete el CHECK
 * de V32 y un arranque con {@code ddl-auto=validate} valide sin conflictos.</p>
 */
@Converter(autoApply = false)
public class PeriodicidadConverter implements AttributeConverter<Periodicidad, String> {

    @Override
    public String convertToDatabaseColumn(Periodicidad atributo) {
        return (atributo == null) ? null : atributo.valorBd();
    }

    @Override
    public Periodicidad convertToEntityAttribute(String columna) {
        return (columna == null) ? null : Periodicidad.desdeValorBd(columna);
    }
}
