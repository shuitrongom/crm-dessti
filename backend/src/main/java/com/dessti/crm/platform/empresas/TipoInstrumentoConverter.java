package com.dessti.crm.platform.empresas;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Convertidor JPA entre {@link TipoInstrumento} y su etiqueta persistida en la
 * columna {@code suscripcion.tipo_instrumento} (VARCHAR con CHECK
 * {@code IN ('plan', 'suscripcion')} de la migracion V64).
 *
 * <p>Persiste siempre {@link TipoInstrumento#valorBd()} (minusculas), nunca el
 * nombre de la constante Java, de modo que el valor almacenado respete el CHECK
 * de V64 y un arranque con {@code ddl-auto=validate} valide sin conflictos.
 * Sigue el mismo patron que {@link EstadoSuscripcionConverter}.</p>
 */
@Converter(autoApply = false)
public class TipoInstrumentoConverter implements AttributeConverter<TipoInstrumento, String> {

    @Override
    public String convertToDatabaseColumn(TipoInstrumento atributo) {
        return (atributo == null) ? null : atributo.valorBd();
    }

    @Override
    public TipoInstrumento convertToEntityAttribute(String columna) {
        return (columna == null) ? null : TipoInstrumento.desdeValorBd(columna);
    }
}
