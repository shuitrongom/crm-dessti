package com.dessti.crm.platform.respaldo.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Convertidor JPA entre {@link EstadoRespaldo} y su etiqueta persistida en la
 * columna {@code respaldo.estado} (VARCHAR con CHECK
 * {@code IN ('en_proceso','completado','fallido')} de la migracion V46).
 *
 * <p>Persiste siempre {@link EstadoRespaldo#valor()} (minusculas ASCII), nunca
 * el nombre de la constante Java, para respetar el CHECK de V46 y validar con
 * {@code ddl-auto=validate}. Mismo patron que {@code EstadoActivoFijoConverter}.</p>
 */
@Converter(autoApply = false)
public class EstadoRespaldoConverter implements AttributeConverter<EstadoRespaldo, String> {

    @Override
    public String convertToDatabaseColumn(EstadoRespaldo atributo) {
        return (atributo == null) ? null : atributo.valor();
    }

    @Override
    public EstadoRespaldo convertToEntityAttribute(String columna) {
        return (columna == null) ? null : EstadoRespaldo.desde(columna);
    }
}
