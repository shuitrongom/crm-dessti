package com.dessti.crm.calidad.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Convertidor JPA entre {@link TipoContexto} y su etiqueta persistida en la columna
 * {@code contexto_organizacion.tipo} (VARCHAR con CHECK {@code IN ('interna','externa')}
 * de la migracion V47). Persiste siempre {@link TipoContexto#valorBd()}.
 */
@Converter(autoApply = false)
public class TipoContextoConverter implements AttributeConverter<TipoContexto, String> {

    @Override
    public String convertToDatabaseColumn(TipoContexto atributo) {
        return (atributo == null) ? null : atributo.valorBd();
    }

    @Override
    public TipoContexto convertToEntityAttribute(String columna) {
        return (columna == null) ? null : TipoContexto.desdeValorBd(columna);
    }
}
