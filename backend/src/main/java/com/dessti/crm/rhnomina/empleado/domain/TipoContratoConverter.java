package com.dessti.crm.rhnomina.empleado.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Convertidor JPA entre {@link TipoContrato} y su etiqueta persistida en la
 * columna {@code contrato_laboral.tipo} (VARCHAR con CHECK {@code IN
 * ('indeterminado','determinado','obra','capacitacion')} de la migracion V32).
 *
 * <p>Persiste siempre {@link TipoContrato#valorBd()} (minusculas ASCII), nunca el
 * nombre de la constante Java, de modo que el valor almacenado respete el CHECK
 * de V32 y un arranque con {@code ddl-auto=validate} valide sin conflictos.
 * Sigue el mismo patron que {@code TipoMovimientoInventarioConverter}.</p>
 */
@Converter(autoApply = false)
public class TipoContratoConverter implements AttributeConverter<TipoContrato, String> {

    @Override
    public String convertToDatabaseColumn(TipoContrato atributo) {
        return (atributo == null) ? null : atributo.valorBd();
    }

    @Override
    public TipoContrato convertToEntityAttribute(String columna) {
        return (columna == null) ? null : TipoContrato.desdeValorBd(columna);
    }
}
