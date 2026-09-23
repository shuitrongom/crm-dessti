package com.dessti.crm.tesoreria.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Convertidor JPA entre {@link EstadoConciliacionBancaria} y su etiqueta persistida
 * en la columna {@code conciliacion_bancaria.estado} (VARCHAR con CHECK {@code IN
 * ('en_proceso', 'completa')} de la migracion V35).
 *
 * <p>Persiste siempre {@link EstadoConciliacionBancaria#valorBd()} (minusculas
 * ASCII), nunca el nombre de la constante Java, de modo que el valor almacenado
 * respete el CHECK de V35. Sigue el mismo patron que
 * {@code EstadoCuentaPorPagarConverter}.</p>
 */
@Converter(autoApply = false)
public class EstadoConciliacionBancariaConverter
        implements AttributeConverter<EstadoConciliacionBancaria, String> {

    @Override
    public String convertToDatabaseColumn(EstadoConciliacionBancaria atributo) {
        return (atributo == null) ? null : atributo.valorBd();
    }

    @Override
    public EstadoConciliacionBancaria convertToEntityAttribute(String columna) {
        return (columna == null) ? null : EstadoConciliacionBancaria.desdeValorBd(columna);
    }
}
