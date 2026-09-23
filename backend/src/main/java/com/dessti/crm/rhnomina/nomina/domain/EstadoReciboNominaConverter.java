package com.dessti.crm.rhnomina.nomina.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Convertidor JPA entre {@link EstadoReciboNomina} y su etiqueta persistida en la
 * columna {@code recibo_nomina.estado} (VARCHAR con CHECK {@code IN ('calculado',
 * 'timbrado','cancelado')} de la migracion V34).
 *
 * <p>Persiste siempre {@link EstadoReciboNomina#valorBd()} (minusculas ASCII), nunca
 * el nombre de la constante Java, de modo que el valor almacenado respete el CHECK de
 * V34 y un arranque con {@code ddl-auto=validate} valide sin conflictos. Sigue el
 * mismo patron que {@code EstadoFacturaConverter}.</p>
 */
@Converter(autoApply = false)
public class EstadoReciboNominaConverter implements AttributeConverter<EstadoReciboNomina, String> {

    @Override
    public String convertToDatabaseColumn(EstadoReciboNomina atributo) {
        return (atributo == null) ? null : atributo.valorBd();
    }

    @Override
    public EstadoReciboNomina convertToEntityAttribute(String columna) {
        return (columna == null) ? null : EstadoReciboNomina.desdeValorBd(columna);
    }
}
