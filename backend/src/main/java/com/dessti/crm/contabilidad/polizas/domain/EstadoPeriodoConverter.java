package com.dessti.crm.contabilidad.polizas.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Convertidor JPA entre {@link EstadoPeriodo} y su etiqueta persistida en la columna
 * {@code periodo_contable.estado} (VARCHAR con CHECK {@code IN ('abierto',
 * 'cerrado')} de la migracion V72).
 *
 * <p>Persiste siempre {@link EstadoPeriodo#valorBd()} (minusculas ASCII), nunca el
 * nombre de la constante Java, de modo que el valor almacenado respete el CHECK de
 * V72 y un arranque con {@code ddl-auto=validate} valide sin conflictos. Sigue el
 * mismo patron que {@code EstadoCuentaPorPagarConverter}.</p>
 */
@Converter(autoApply = false)
public class EstadoPeriodoConverter implements AttributeConverter<EstadoPeriodo, String> {

    @Override
    public String convertToDatabaseColumn(EstadoPeriodo atributo) {
        return (atributo == null) ? null : atributo.valorBd();
    }

    @Override
    public EstadoPeriodo convertToEntityAttribute(String columna) {
        return (columna == null) ? null : EstadoPeriodo.desdeValorBd(columna);
    }
}
