package com.dessti.crm.platform.respaldo.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Convertidor JPA entre {@link TipoRespaldo} y su etiqueta persistida en la
 * columna {@code respaldo.tipo} (VARCHAR con CHECK {@code IN ('respaldo','restauracion')}
 * de la migracion V46).
 *
 * <p>Persiste siempre {@link TipoRespaldo#valor()} (minusculas ASCII), nunca el
 * nombre de la constante Java, para respetar el CHECK de V46 y validar con
 * {@code ddl-auto=validate}. Mismo patron que {@code EstadoActivoFijoConverter}.</p>
 */
@Converter(autoApply = false)
public class TipoRespaldoConverter implements AttributeConverter<TipoRespaldo, String> {

    @Override
    public String convertToDatabaseColumn(TipoRespaldo atributo) {
        return (atributo == null) ? null : atributo.valor();
    }

    @Override
    public TipoRespaldo convertToEntityAttribute(String columna) {
        return (columna == null) ? null : TipoRespaldo.desde(columna);
    }
}
