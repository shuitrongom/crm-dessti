package com.dessti.crm.rhnomina.nomina.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Convertidor JPA entre {@link EstadoNomina} y su etiqueta persistida en la columna
 * {@code nomina.estado} (VARCHAR con CHECK {@code IN ('borrador','calculada',
 * 'autorizada','timbrada','pagada')} de la migracion V34).
 *
 * <p>Persiste siempre {@link EstadoNomina#valorBd()} (minusculas ASCII), nunca el
 * nombre de la constante Java, de modo que el valor almacenado respete el CHECK de
 * V34 y un arranque con {@code ddl-auto=validate} valide sin conflictos. Sigue el
 * mismo patron que {@code EstadoFacturaConverter}.</p>
 */
@Converter(autoApply = false)
public class EstadoNominaConverter implements AttributeConverter<EstadoNomina, String> {

    @Override
    public String convertToDatabaseColumn(EstadoNomina atributo) {
        return (atributo == null) ? null : atributo.valorBd();
    }

    @Override
    public EstadoNomina convertToEntityAttribute(String columna) {
        return (columna == null) ? null : EstadoNomina.desdeValorBd(columna);
    }
}
